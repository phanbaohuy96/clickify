package com.pbh.clickify.core.designsystem

import android.content.Context
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import com.pbh.clickify.core.R

// DS-1: two families, bundled rather than downloaded.
//
// Bundled because Clickify has no INTERNET permission and no intention of asking for one. A font
// provider would also be one more thing that can be unavailable at the moment the floating control
// has to be legible over somebody else's game.
//
// Space Grotesk carries names and numbers: it has the squared-off, instrument-panel character this
// app is. IBM Plex Sans carries everything meant to be read as a sentence — it was drawn for
// interfaces and stays readable at 12sp over an arbitrary background, which is the hardest thing
// this interface does.

/**
 * The two families, exposed so they can be in memory before anything needs to draw.
 *
 * The first composition that needs a font reads it off disk on the thread it is composing on, and
 * these are two variable files of about 660 KB between them — which the emulator turned into
 * several seconds of nothing before the floating control appeared. The control is the first thing
 * the user sees after leaving the app, and it must not be the slowest.
 */
object AppFonts {
    val Display: FontFamily get() = DisplayFamily

    val Body: FontFamily get() = BodyFamily
}

/**
 * Reads both families into the resolver's cache, off the composing thread.
 *
 * Called once per process. The Overlay and the Activity share a process, so whichever starts first
 * pays for both.
 */
suspend fun preloadFonts(context: Context) {
    val resolver = createFontFamilyResolver(context)
    resolver.preload(AppFonts.Display)
    resolver.preload(AppFonts.Body)
}

internal val DisplayFamily =
    FontFamily(
        variable(R.font.space_grotesk, FontWeight.Medium),
        variable(R.font.space_grotesk, FontWeight.SemiBold),
        variable(R.font.space_grotesk, FontWeight.Bold),
    )

internal val BodyFamily =
    FontFamily(
        variable(R.font.ibm_plex_sans, FontWeight.Normal),
        variable(R.font.ibm_plex_sans, FontWeight.Medium),
        variable(R.font.ibm_plex_sans, FontWeight.SemiBold),
    )

/**
 * One file per family, one axis setting per weight (`DS-1`).
 *
 * A variable font means three weights cost three entries rather than three files, which is most of
 * why bundling two families is affordable at all. `FontVariation` is still marked experimental; it
 * has been in Compose since 1.5 and the alternative is six static files.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(
    resource: Int,
    weight: FontWeight,
) = Font(
    resId = resource,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)
