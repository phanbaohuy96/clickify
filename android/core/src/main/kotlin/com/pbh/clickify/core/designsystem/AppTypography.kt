package com.pbh.clickify.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Trimmed so a line box is the line and not the line plus its upholstery.
 *
 * It matters here more than in most apps: the floating control is measured in millimetres of
 * somebody else's screen, and the default line box would make it a third taller for nothing.
 */
private val Trimmed =
    LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

/**
 * The scale (`DS-1`).
 *
 * Split in two, and the split is the real one rather than a way of shortening a function: what is
 * set in the display face and what is not. Space Grotesk names things; IBM Plex Sans says things.
 */
internal fun appTypography(): Typography =
    Typography(
        headlineLarge = display(32.sp, 38.sp, FontWeight.Bold, (-0.8).sp),
        headlineMedium = display(26.sp, 32.sp, FontWeight.SemiBold, (-0.5).sp),
        headlineSmall = display(21.sp, 27.sp, FontWeight.SemiBold, (-0.3).sp),
        titleMedium = display(16.sp, 21.sp, FontWeight.Medium, (-0.1).sp),
        titleSmall = display(14.sp, 19.sp, FontWeight.Medium),
        labelLarge = display(14.sp, 18.sp, FontWeight.SemiBold),
        // Positive tracking on small type, which is the opposite of what a headline wants.
        labelMedium = display(12.sp, 16.sp, FontWeight.Medium, 0.3.sp),
        bodyLarge = text(16.sp, 24.sp, FontWeight.Normal),
        bodyMedium = text(14.sp, 21.sp, FontWeight.Normal),
        bodySmall = text(12.5.sp, 18.sp, FontWeight.Normal),
        labelSmall = text(11.sp, 14.sp, FontWeight.Medium, 0.4.sp),
    )

/** Space Grotesk: names, numbers, and anything that labels a control. */
private fun display(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight,
    tracking: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = DisplayFamily,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    lineHeightStyle = Trimmed,
)

/** IBM Plex Sans: anything meant to be read as a sentence, and the smallest label of all. */
private fun text(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight,
    tracking: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = BodyFamily,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = tracking,
    lineHeightStyle = Trimmed,
)
