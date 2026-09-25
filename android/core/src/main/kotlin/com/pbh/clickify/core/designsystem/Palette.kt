package com.pbh.clickify.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * One accent, and a grey family that stays in one temperature (`DS-2`).
 *
 * The **Overlay**'s palette is the one that had to be designed rather than chosen. It is drawn on
 * top of whatever the user is automating — a game, a dark chat, a white form — so it cannot borrow
 * contrast from a background it does not own. That rules out dynamic colour, whose whole idea is to
 * take its colour from somewhere else, and it is why the Overlay is dark at every hour of the day
 * regardless of the system setting.
 *
 * The accent is amber rather than the blue-violet that every generated interface reaches for. It
 * also has a second job: it is the one hue that is legible against both a bright and a dark screen,
 * which is exactly the problem this palette exists to solve.
 */
internal object Palette {
    /** Not `#000000`. A true black reads as a hole cut in the screen rather than as a surface. */
    val Ink = Color(0xFF14161A)
    val InkRaised = Color(0xFF22262E)
    val InkEdge = Color(0xFF2E333C)

    /**
     * The hairline that gives an Overlay window an edge (`DS-4`).
     *
     * Chosen to be visible against **both** ends of what it might be drawn over. A dark control on
     * a dark wallpaper has no edge at all — it was tested and it disappeared — and a shadow cannot
     * supply one, because a shadow on black is black. Mid-grey is the only value that reads as a
     * border over a game and over a white form alike.
     */
    val Outline = Color(0xFF4E5665)

    val Chalk = Color(0xFFECEEF2)
    val ChalkMuted = Color(0xFF9AA3AF)

    /** The one accent. Anything that wants a second one is asking for emphasis it has not earned. */
    val Amber = Color(0xFFE8B33C)
    val AmberDeep = Color(0xFF7A5C0C)
    val AmberInk = Color(0xFF241900)
    val AmberWash = Color(0xFFFCE9BC)
    val AmberDim = Color(0xFF3A2E10)

    /** Saturation kept under 80%: an alarm that shouts is an alarm that gets ignored. */
    val Rust = Color(0xFFFF7A6B)
    val RustDeep = Color(0xFFA8281B)
    val RustInk = Color(0xFF2E0A06)
    val RustWash = Color(0xFFFFDAD4)

    val Moss = Color(0xFF7FD1A8)
    val MossDeep = Color(0xFF2C6A4E)

    /** Warm off-white, in the same temperature as the greys above. */
    val Paper = Color(0xFFFAF9F7)
    val PaperRaised = Color(0xFFFFFFFF)
    val PaperEdge = Color(0xFFEDEBE6)
    val PaperText = Color(0xFF16181C)
    val PaperMuted = Color(0xFF5B626D)
}
