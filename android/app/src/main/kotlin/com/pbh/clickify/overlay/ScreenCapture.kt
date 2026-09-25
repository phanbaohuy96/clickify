package com.pbh.clickify.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.Display
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pbh.clickify.domain.recognition.GrayImage
import com.pbh.clickify.domain.recognition.ScreenSource
import com.pbh.clickify.domain.recognition.TemplateSource
import com.pbh.clickify.domain.repository.TemplateFiles
import com.pbh.clickify.domain.scenario.ScenarioLimits
import com.pbh.clickify.domain.scenario.ScreenProfile
import com.pbh.clickify.domain.scenario.ScreenRegion
import com.pbh.clickify.service.ClickifyAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * One frame of the display, through the accessibility service (`TP-2`, [ADR-0016]).
 *
 * `takeScreenshot` is the whole of capture here. MediaProjection is faster and asks the user for
 * the screen every single session, which would put a system dialogue in front of Start — the one
 * press this app promises is always available.
 *
 * Returns null rather than throwing on every refusal: the platform rate-limits this call, and a
 * secure window blocks it outright. `TP-4` treats a refused frame as a poll that found nothing,
 * which is the `onTimeout` the user chose.
 *
 * [ADR-0016]: ../../../../../../../docs/adr/0016-screenshots-come-from-the-accessibility-service.md
 */
suspend fun AccessibilityService.captureScreen(): Bitmap? =
    suspendCancellableCoroutine { continuation ->
        val executor = Executor { it.run() }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            executor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val copied =
                        result.hardwareBuffer.use { buffer ->
                            Bitmap
                                .wrapHardwareBuffer(buffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        }
                    if (continuation.isActive) continuation.resume(copied)
                }

                override fun onFailure(errorCode: Int) {
                    Log.d(TAG, "the platform refused a screenshot, code $errorCode")
                    if (continuation.isActive) continuation.resume(null)
                }
            },
        )
    }

/**
 * The frames a run looks at (`TP-2`, `TP-5`).
 *
 * The greyscale conversion happens off the main thread because it walks every pixel of the
 * display — four million of them on the test device — and it is the cheap half of what follows.
 */
class AccessibilityScreens(
    private val profile: () -> ScreenProfile,
) : ScreenSource {
    override suspend fun frame(): GrayImage? {
        val service = ClickifyAccessibilityService.instance ?: return null
        val captured = service.captureScreen() ?: return null
        return withContext(Dispatchers.Default) { captured.fittedTo(profile()).toGrayImage() }
    }
}

/**
 * A Scenario's Templates, decoded once and kept (`TP-27`).
 *
 * One instance per run. A **Template** is read from disk and turned into a [GrayImage] the first
 * time a **Step** asks for it and never again, which is what keeps a wait's polls to the cost of
 * the search itself. A **Template** whose file has gone is remembered as missing, so `TP-29`'s
 * timeout does not re-read a directory two and a half times a second.
 */
class StoredTemplates(
    private val files: TemplateFiles,
    private val scenarioId: UUID,
) : TemplateSource {
    private val decoded = mutableMapOf<UUID, GrayImage?>()

    override suspend fun template(id: UUID): GrayImage? {
        if (decoded.containsKey(id)) return decoded[id]
        val image =
            files.read(scenarioId, id)?.let { bytes ->
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.toGrayImage()
                }
            }
        decoded[id] = image
        return image
    }
}

/**
 * TP-3: a frame the size of the **Screen profile**, so a point found in it is a point on the
 * screen.
 *
 * Every device this has run on hands back a bitmap exactly the size of the display and this does
 * nothing. It exists because the alternative to noticing a difference is a match reported at the
 * wrong coordinates with a confident score attached, which is the one failure mode this whole
 * feature is built to avoid.
 */
internal fun Bitmap.fittedTo(profile: ScreenProfile): Bitmap =
    if (width == profile.widthPixels && height == profile.heightPixels) {
        this
    } else {
        Bitmap.createScaledBitmap(this, profile.widthPixels, profile.heightPixels, true)
    }

/**
 * Brightness `0…1`, row by row, which is the only thing [com.pbh.clickify.domain.recognition.TemplateMatcher] knows how to read.
 *
 * The coefficients are the ordinary luminance ones. What matters more than which coefficients is
 * that **one** conversion is used for both halves: a **Template** is cropped out of a frame that
 * came through here, so the pixels being correlated were measured the same way.
 */
internal fun Bitmap.toGrayImage(): GrayImage {
    val argb = IntArray(width * height)
    getPixels(argb, 0, width, 0, 0, width, height)
    val pixels = FloatArray(argb.size)
    for (index in argb.indices) {
        val pixel = argb[index]
        val red = (pixel shr RED_SHIFT) and CHANNEL_MASK
        val green = (pixel shr GREEN_SHIFT) and CHANNEL_MASK
        val blue = pixel and CHANNEL_MASK
        pixels[index] = (RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue) / CHANNEL_MAXIMUM
    }
    return GrayImage(width, height, pixels)
}

/**
 * TP-8: the pixels inside the rectangle the user dragged, or null when there are none left.
 *
 * Clamped to the frame rather than trusted. The rectangle comes from `rawX`/`rawY`, and a finger
 * that left the screen at its very edge can report a coordinate one pixel past it.
 */
internal fun Bitmap.cropped(region: ScreenRegion): Bitmap? {
    val left = region.left.coerceIn(0, width)
    val top = region.top.coerceIn(0, height)
    val cropWidth = (region.right.coerceIn(0, width) - left)
    val cropHeight = (region.bottom.coerceIn(0, height) - top)
    if (cropWidth < ScenarioLimits.MINIMUM_TEMPLATE_SIDE_PIXELS || cropHeight < ScenarioLimits.MINIMUM_TEMPLATE_SIDE_PIXELS) {
        return null
    }
    return Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
}

/** TP-27: a Template is a PNG, because a Template that lost a pixel would match a little worse. */
internal fun Bitmap.toPng(): ByteArray =
    ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, PNG_QUALITY_IGNORED, it) }.toByteArray()

/** TP-29: a Template to show in the panel, or null when its file has gone. */
internal suspend fun TemplateFiles.decodedPreview(
    scenarioId: UUID,
    templateId: UUID,
): ImageBitmap? =
    read(scenarioId, templateId)?.let { bytes ->
        withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

private const val TAG = "ScreenCapture"

/** PNG is lossless, so the quality argument is read by nobody. */
private const val PNG_QUALITY_IGNORED = 100
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
private const val CHANNEL_MAXIMUM = 255f
private const val RED_WEIGHT = 0.299f
private const val GREEN_WEIGHT = 0.587f
private const val BLUE_WEIGHT = 0.114f
