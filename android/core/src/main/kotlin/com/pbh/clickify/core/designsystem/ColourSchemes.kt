package com.pbh.clickify.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The Overlay's scheme, and the Activity's in the dark.
 *
 * `surface` and `surfaceContainer*` step apart deliberately: the control, the panel and a card
 * inside the panel are three depths, and on a dark scheme depth is the only thing distinguishing
 * them — a shadow is invisible against a black background.
 */
internal fun darkScheme(): ColorScheme =
    darkColorScheme(
        primary = Palette.Amber,
        onPrimary = Palette.AmberInk,
        primaryContainer = Palette.AmberDim,
        onPrimaryContainer = Palette.AmberWash,
        secondary = Palette.ChalkMuted,
        onSecondary = Palette.Ink,
        secondaryContainer = Palette.InkEdge,
        onSecondaryContainer = Palette.Chalk,
        tertiary = Palette.Moss,
        onTertiary = Palette.Ink,
        background = Palette.Ink,
        onBackground = Palette.Chalk,
        surface = Palette.Ink,
        onSurface = Palette.Chalk,
        surfaceVariant = Palette.InkEdge,
        onSurfaceVariant = Palette.ChalkMuted,
        surfaceContainer = Palette.InkRaised,
        surfaceContainerHigh = Palette.InkEdge,
        surfaceContainerHighest = Palette.InkEdge,
        outline = Palette.Outline,
        outlineVariant = Palette.InkEdge,
        error = Palette.Rust,
        onError = Palette.RustInk,
        errorContainer = Palette.RustDeep,
        onErrorContainer = Palette.RustWash,
    )

/** The Activity's scheme in the light. The Overlay never uses it — see [Palette]. */
internal fun lightScheme(): ColorScheme =
    lightColorScheme(
        primary = Palette.AmberDeep,
        onPrimary = Color.White,
        primaryContainer = Palette.AmberWash,
        onPrimaryContainer = Palette.AmberInk,
        secondary = Palette.PaperMuted,
        onSecondary = Color.White,
        secondaryContainer = Palette.PaperEdge,
        onSecondaryContainer = Palette.PaperText,
        tertiary = Palette.MossDeep,
        onTertiary = Color.White,
        background = Palette.Paper,
        onBackground = Palette.PaperText,
        surface = Palette.PaperRaised,
        onSurface = Palette.PaperText,
        surfaceVariant = Palette.PaperEdge,
        onSurfaceVariant = Palette.PaperMuted,
        surfaceContainer = Palette.Paper,
        surfaceContainerHigh = Palette.PaperEdge,
        surfaceContainerHighest = Palette.PaperEdge,
        outline = Color(0xFFB9B5AC),
        outlineVariant = Palette.PaperEdge,
        error = Palette.RustDeep,
        onError = Color.White,
        errorContainer = Palette.RustWash,
        onErrorContainer = Palette.RustInk,
    )
