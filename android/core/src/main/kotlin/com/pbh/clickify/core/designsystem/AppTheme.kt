package com.pbh.clickify.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pbh.clickify.core.ui.Localised

/** Complete theme configuration injected into [ClickifyTheme]. */
@Immutable
data class AppThemeConfig(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val spacing: AppSpacing,
    val decoration: AppDecoration,
)

/** Shared spacing scale for screens and reusable components. */
@Immutable
data class AppSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val screen: Dp = 20.dp,
)

/** Shared non-color decoration tokens used by app components. */
@Immutable
data class AppDecoration(
    val minTouchTarget: Dp = 48.dp,
    val loadingIndicatorSize: Dp = 18.dp,
    val loadingIndicatorStrokeWidth: Dp = 2.dp,
    val cardElevation: Dp = 0.dp,
    val focusedBorderWidth: Dp = 2.dp,
    /** DS-3: the hairline that replaces a card border on the dark scheme. */
    val hairline: Dp = 1.dp,
)

/**
 * DS-3: the radius tightens as things get smaller, rather than one number everywhere.
 *
 * A chip inside a panel inside a rounded window with the same corner at all three depths reads as
 * a mistake. The container is soft; what sits inside it is not.
 */
private fun appShapes(): Shapes =
    Shapes(
        extraSmall =
            androidx.compose.foundation.shape
                .RoundedCornerShape(6.dp),
        small =
            androidx.compose.foundation.shape
                .RoundedCornerShape(10.dp),
        medium =
            androidx.compose.foundation.shape
                .RoundedCornerShape(14.dp),
        large =
            androidx.compose.foundation.shape
                .RoundedCornerShape(20.dp),
        extraLarge =
            androidx.compose.foundation.shape
                .RoundedCornerShape(28.dp),
    )

private val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
private val LocalAppDecoration = staticCompositionLocalOf { AppDecoration() }
private val LocalAppThemeConfig = staticCompositionLocalOf { AppThemeDefaults.light() }

/** Composition-local accessors for the active app theme tokens. */
object AppTheme {
    /** Current Material color scheme. */
    val colors: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    /** Current Material typography scale. */
    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    /** Current Material shapes. */
    val shapes: Shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes

    /** Current app spacing scale. */
    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSpacing.current

    /** Current app decoration tokens. */
    val decoration: AppDecoration
        @Composable
        @ReadOnlyComposable
        get() = LocalAppDecoration.current

    /** Full theme configuration currently provided to the tree. */
    val config: AppThemeConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalAppThemeConfig.current
}

/** The three schemes this app has: the Activity's two, and the Overlay's one. */
object AppThemeDefaults {
    fun light(): AppThemeConfig = config(lightScheme())

    fun dark(): AppThemeConfig = config(darkScheme())

    /**
     * DS-2: the Overlay's scheme, which is the dark one at every hour of the day.
     *
     * Not a preference. The Overlay is drawn on top of something it does not own, so it cannot
     * borrow contrast from the background — a light control over a dark game is a white slab, and
     * a light control over a white form disappears. One high-contrast dark scheme is the only
     * answer that works over both.
     */
    fun overlay(): AppThemeConfig = config(darkScheme())

    private fun config(scheme: ColorScheme) =
        AppThemeConfig(
            colorScheme = scheme,
            typography = appTypography(),
            shapes = appShapes(),
            spacing = AppSpacing(),
            decoration = AppDecoration(),
        )
}

/** Provides [config] to Compose and applies the matching Material theme. */
@Composable
fun ClickifyTheme(
    config: AppThemeConfig = AppThemeDefaults.light(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppThemeConfig provides config,
        LocalAppSpacing provides config.spacing,
        LocalAppDecoration provides config.decoration,
    ) {
        MaterialTheme(
            colorScheme = config.colorScheme,
            typography = config.typography,
            shapes = config.shapes,
            // IL-3: the one place both surfaces pass through, so neither can forget the language.
            content = { Localised(content) },
        )
    }
}

/**
 * The theme every Overlay window uses (`DS-2`).
 *
 * Its own entry point rather than an argument, so that "the Overlay is dark" is something the
 * window cannot forget rather than something each call site has to remember. Every window went
 * through `ClickifyTheme` with its default before this, which is how the floating control came to
 * be a white slab on a dark launcher.
 */
@Composable
fun OverlayTheme(content: @Composable () -> Unit) {
    ClickifyTheme(config = AppThemeDefaults.overlay(), content = content)
}
