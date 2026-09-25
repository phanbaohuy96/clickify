package com.pbh.clickify.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.pbh.clickify.domain.settings.ControlPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test

/**
 * AP-1, OV-14: the one thing Clickify remembers about itself rather than about a Scenario.
 *
 * The case worth a test is the empty one. "No opinion yet" and "the top-left corner" are different
 * answers, and a store that confused them would put a first-run control in a corner the user never
 * chose.
 */
class DataStoreSettingsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun settings(file: File = File(folder.newFolder(), "settings.preferences_pb")) =
        DataStoreSettings(PreferenceDataStoreFactory.create(produceFile = { file }))

    @Test
    fun `a store that has never been written has no opinion about where the control goes`() =
        runTest {
            assertThat(settings().settings.first().controlPosition).isNull()
        }

    @Test
    fun `the control's position survives being written and read back`() =
        runTest {
            val store = settings()

            store.setControlPosition(ControlPosition(x = 1_120, y = 640))

            assertThat(store.settings.first().controlPosition).isEqualTo(ControlPosition(1_120, 640))
        }

    @Test
    fun `the corner is a position like any other, and is not mistaken for no opinion`() =
        runTest {
            val store = settings()

            store.setControlPosition(ControlPosition(x = 0, y = 0))

            assertThat(store.settings.first().controlPosition).isEqualTo(ControlPosition(0, 0))
        }

    @Test
    fun `moving the control twice keeps only the second place`() =
        runTest {
            val store = settings()

            store.setControlPosition(ControlPosition(0, 100))
            store.setControlPosition(ControlPosition(900, 200))

            assertThat(store.settings.first().controlPosition).isEqualTo(ControlPosition(900, 200))
        }

    /** IL-1: a language chosen on purpose survives the process. */
    @Test
    fun `a chosen language is written and read back`() =
        runTest {
            val store = settings()

            store.setLanguageTag("vi")

            assertThat(store.settings.first().languageTag).isEqualTo("vi")
        }

    /**
     * IL-1: null is not "English".
     *
     * A phone set to Japanese shows Japanese without anybody choosing it, so "follow the phone"
     * has to be a different value from any language — it is the absence of a choice, and it is
     * stored as the absence of a key.
     */
    @Test
    fun `putting the language back to the phone forgets the choice entirely`() =
        runTest {
            val store = settings()
            store.setLanguageTag("es")

            store.setLanguageTag(null)

            assertThat(store.settings.first().languageTag).isNull()
        }

    @Test
    fun `a fresh install follows the phone`() =
        runTest {
            assertThat(settings().settings.first().languageTag).isNull()
        }
}
