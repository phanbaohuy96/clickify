import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

val kotlinVersion = libs.versions.kotlin.get()

extensions.configure<SpotlessExtension>("spotless") {
    kotlinGradle {
        target("*.gradle.kts", "gradle/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jetbrains.kotlinx.kover")

    extensions.configure<DetektExtension>("detekt") {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("detekt.yml"))
        // The plugin's default source is main and test only, which left the tier 2 harness
        // (`src/androidTest`) and the target app it drives (`src/debug`) outside every gate but
        // Spotless. Code that decides whether `SF-1` passes is not code to lint less.
        source.setFrom(
            files(
                "src/main/kotlin",
                "src/test/kotlin",
                "src/androidTest/kotlin",
                "src/debug/kotlin",
            ),
        )
    }

    configurations.configureEach {
        if (
            name.endsWith("CompileClasspath") ||
            name.endsWith("RuntimeClasspath") ||
            name.contains("UnitTest") ||
            name.startsWith("ksp")
        ) {
            resolutionStrategy.force(
                "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion",
                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion",
            )
        }
    }

    extensions.configure<SpotlessExtension>("spotless") {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**", "**/generated/**")
            ktlint(libs.versions.ktlint.get())
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
        }
    }

    tasks.withType<Detekt>().configureEach {
        exclude("**/build/**", "**/generated/**")
    }
}

/**
 * Coverage is verified **per module**, against that module's own execution data.
 *
 * The root aggregate was tried first and reported a different number on CI than on a developer
 * machine while every per-module number matched exactly, so the aggregation — not the code — was
 * what moved. `:app` is left out: after the filters below it holds nothing but Compose and wiring,
 * and a rule over an empty set measures nothing.
 */
val coveredModules = setOf(":core", ":data", ":domain")

configure(subprojects.filter { it.path in coveredModules }) {
    extensions.configure<KoverProjectExtension>("kover") {
        reports {
            total {
                filters {
                    includes {
                        // Grows with each slice. Only classes that carry logic worth asserting on
                        // belong here; Compose screens, generated code and wiring are excluded.
                        classes(
                            "com.pbh.clickify.core.overlay.*",
                            "com.pbh.clickify.core.ui.DomainErrorText*",
                            "com.pbh.clickify.core.ui.UiText*",
                            "com.pbh.clickify.data.scenario.*",
                            "com.pbh.clickify.domain.model.*",
                            "com.pbh.clickify.domain.run.*",
                            "com.pbh.clickify.domain.scenario.*",
                        )
                    }
                    excludes {
                        // Composables need a Robolectric or instrumented UI test, which
                        // android/docs/testing.md places in tier 2, not in this JVM tier.
                        annotatedBy("androidx.compose.runtime.Composable")
                        classes(
                            "*.BuildConfig",
                            "*.R",
                            "*.R\$*",
                            "*ComposableSingletons*",
                            "*Hilt_*",
                            "*_Factory",
                            "*_HiltModules*",
                            "com.pbh.clickify.core.common.*",
                            "com.pbh.clickify.core.designsystem.AppTheme",
                            "com.pbh.clickify.core.designsystem.AppThemeKt",
                            "com.pbh.clickify.core.designsystem.components.*",
                            "*ScreenKt*",
                        )
                    }
                }
                verify {
                    rule("Line coverage must stay at or above 80% for app logic") {
                        minBound(80, CoverageUnit.LINE, AggregationType.COVERED_PERCENTAGE)
                    }
                }
            }
        }
    }
}
