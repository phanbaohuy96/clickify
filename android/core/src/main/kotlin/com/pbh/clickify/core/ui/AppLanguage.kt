package com.pbh.clickify.core.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The interface language, as one value the whole process can see (`IL-1`).
 *
 * A singleton, and deliberately: macOS solved the same problem the same way
 * ([ADR-0010](../../../../../../../docs/adr/0010-strings-files-and-a-live-bundle-swap.md)) and for
 * the same reason. The language is not a property of a screen or of a window — it belongs to the
 * application — and the alternative here is threading it through **two** window systems, because
 * most of this app's interface is in the Overlay rather than in an Activity.
 *
 * Android's own answer, `LocaleManager.setApplicationLocales`, arrives at API 33 and this app
 * supports 30. It also restarts the process to apply, which would take the Overlay off the screen
 * of whatever the user was in the middle of automating.
 */
object AppLanguage {
    private val _tag = MutableStateFlow<String?>(null)

    /** The chosen language, or null to follow the phone. */
    val tag: StateFlow<String?> = _tag.asStateFlow()

    fun set(tag: String?) {
        _tag.value = tag
    }
}

/**
 * IL-2: the five languages, offered under their own names.
 *
 * Under their own names because somebody who has set their phone to a language they cannot read is
 * exactly the person who needs this list, and "Vietnamese" is no help to them.
 *
 * Only English and Vietnamese have been checked by a person; the other three are machine
 * translations, as on macOS. Anything missing falls back to English, so a partial translation is
 * still worth having.
 */
enum class InterfaceLanguage(
    val tag: String,
    val nativeName: String,
) {
    ENGLISH("en", "English"),
    VIETNAMESE("vi", "Tiếng Việt"),
    CHINESE("zh-CN", "中文（简体）"),
    JAPANESE("ja", "日本語"),
    SPANISH("es", "Español"),
}

/**
 * This Context with its resources speaking [tag], and everything else about it untouched.
 *
 * A [ContextWrapper] rather than the Context `createConfigurationContext` hands back, because that
 * one is **not** a wrapper: its base is the application rather than the Activity, so anything that
 * walks the chain looking for an Activity — `moveTaskToBack`, a dialogue's window — stops finding
 * one. This keeps the chain and swaps only the two things that carry words.
 */
fun Context.localisedFor(tag: String?): Context = if (tag.isNullOrBlank()) this else LocalisedContext(this, tag)

private class LocalisedContext(
    base: Context,
    tag: String,
) : ContextWrapper(base) {
    private val localised: Context =
        base.createConfigurationContext(
            Configuration(base.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(tag)))
            },
        )

    override fun getResources(): Resources = localised.resources

    override fun getAssets(): AssetManager = localised.assets
}

/**
 * IL-3: everything drawn inside this reads its words in the chosen language, at once.
 *
 * No restart, no Activity recreation, and no second code path for the Overlay — which is the whole
 * difficulty this requirement exists for. An Overlay window is attached to the window manager
 * rather than to an Activity, so it is never recreated for a configuration change and would
 * otherwise keep the language it was attached with until the phone was rebooted.
 *
 * Applied inside the one theme both surfaces already go through, so a window cannot forget it.
 */
@Composable
fun Localised(content: @Composable () -> Unit) {
    val tag by AppLanguage.tag.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val localised = remember(context, tag) { context.localisedFor(tag) }

    CompositionLocalProvider(
        LocalContext provides localised,
        LocalResources provides localised.resources,
        LocalConfiguration provides localised.resources.configuration,
        content = content,
    )
}
