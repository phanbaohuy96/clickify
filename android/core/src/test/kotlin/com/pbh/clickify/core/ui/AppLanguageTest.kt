package com.pbh.clickify.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * IL-1 to IL-3: the language swap, and the one property of it that is easy to lose.
 *
 * The interesting assertion is the last one. `createConfigurationContext` returns a Context whose
 * base is the application rather than the Activity, so using it directly would quietly break every
 * `findActivity()` walk in the app — including `moveTaskToBack`, which is what `OV-26` needs when
 * a Scenario is opened.
 */
@RunWith(RobolectricTestRunner::class)
class AppLanguageTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `no chosen language leaves the context exactly as it was`() {
        assertSame(context, context.localisedFor(null))
        assertSame(context, context.localisedFor(""))
    }

    @Test
    fun `a chosen language is the language the resources speak`() {
        val localised = context.localisedFor("vi")

        assertEquals(
            "vi",
            localised.resources.configuration.locales[0]
                .language,
        )
    }

    @Test
    fun `every offered language is a tag the platform understands`() {
        InterfaceLanguage.entries.forEach { language ->
            val locales =
                context
                    .localisedFor(language.tag)
                    .resources.configuration.locales

            assertEquals(
                language.tag.substringBefore('-'),
                locales[0].language,
                "${language.nativeName} asked for ${language.tag} and got ${locales[0]}",
            )
        }
    }

    /**
     * The reason this is a [ContextWrapper] rather than what `createConfigurationContext` returns.
     *
     * Anything that walks the base chain looking for an Activity has to keep finding one, or
     * `OV-26`'s `moveTaskToBack` stops working the moment a language is chosen — which is a bug
     * that would look like "opening a Scenario leaves the app in front, but only in Spanish".
     */
    @Test
    fun `the activity is still reachable through a localised context`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val localised = activity.localisedFor("ja")

        assertSame(activity, assertNotNull(localised.findActivity()))
        assertEquals(
            "ja",
            localised.resources.configuration.locales[0]
                .language,
        )
    }

    @Test
    fun `the chosen language is remembered process-wide and can be put back`() {
        AppLanguage.set("es")
        assertEquals("es", AppLanguage.tag.value)

        AppLanguage.set(null)
        assertEquals(null, AppLanguage.tag.value)
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
