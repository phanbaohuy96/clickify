package com.pbh.clickify.brand

import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The product name is a proper noun and is never translated ([ADR-0017]), so it sits as a plain literal in
 * every `strings.xml` rather than being interpolated from `app_name`. Android cannot reference one string
 * resource from another anyway, and the alternative — a `%1$s` in every sentence — hands the translator a
 * clause with no subject. This test is what pays for that choice: a rename that misses a locale, or a new
 * string arriving with the retired spelling, fails here instead of shipping.
 *
 * Deliberately narrow — **string resources only, never prose**. Documentation has to be able to say the old
 * name in order to explain it ([ADR-0017] and `CONTEXT.md` both do), and `android/docs/landscape.md` names
 * *other people's* products, one of which is literally called Smart AutoClicker.
 */
class BrandNameTest {
    @Test
    fun `no string resource carries the retired name`() {
        val files = stringResources()

        // Asserted before a single file is read. The trap this closes: a wrong root finds nothing, every
        // check below passes vacuously, and the test reports success for a scan it never performed.
        assertTrue(files.size >= 25, "expected the string resources of both modules, found ${files.size}")
        assertEquals(
            LOCALES,
            files.map { it.parentFile.name }.toSet().intersect(LOCALES),
            "a locale went missing from the walk",
        )

        files.forEach { file ->
            val text = file.readText()
            RETIRED.forEach { spelling ->
                assertFalse(
                    text.contains(spelling),
                    "${file.parentFile.parentFile.parentFile.name}/${file.parentFile.name}/${file.name} " +
                        "still says \"$spelling\"",
                )
            }
        }
    }

    @Test
    fun `the launcher label and the application id are Clickify`() {
        assertContains(
            androidRoot().resolve("app/src/main/res/values/strings.xml").readText(),
            """<string name="app_name">Clickify</string>""",
        )
        // Frozen from the first release onward; see [ADR-0017].
        assertContains(
            androidRoot().resolve("app/build.gradle.kts").readText(),
            """applicationId = "com.pbh.clickify"""",
        )
    }

    private companion object {
        /** The spellings **Clickify** replaced. The lowercase category, "an auto clicker", stays legal. */
        val RETIRED = listOf("Auto Click", "AutoClick")

        /** LC-1: the five languages that ship. */
        val LOCALES = setOf("values", "values-vi", "values-ja", "values-es", "values-zh-rCN")

        fun stringResources(): List<File> =
            androidRoot().walkTopDown()
                .onEnter { it.name != "build" }
                .filter { it.isFile && it.name.startsWith("strings") && it.extension == "xml" }
                .filter { it.parentFile.name in LOCALES }
                .toList()

        /** Unit tests run from the module directory, so the root is found by walking up to the settings file. */
        fun androidRoot(): File =
            generateSequence(File(".").absoluteFile) { it.parentFile }
                .firstOrNull { it.resolve("settings.gradle.kts").exists() }
                ?: error("no settings.gradle.kts above ${File(".").absolutePath}")
    }
}
