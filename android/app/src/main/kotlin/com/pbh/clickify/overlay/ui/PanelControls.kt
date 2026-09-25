package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.pbh.clickify.R
import com.pbh.clickify.domain.scenario.GlobalActionKind
import com.pbh.clickify.domain.scenario.StepViolation

/** Nine digits is past every range in `ScenarioLimits`, and stops a paste becoming a hang. */
private const val MAX_DIGITS = 9

/**
 * Reports this field's focus to the panel, which is how the window learns to open a keyboard
 * (`OV-20`).
 *
 * It reports only **changes**, and it reports a loss on the way out. Both matter: the panel counts
 * focused fields to decide whether any of them holds the caret, and a field that vanished while
 * focused — the Action changed, a contact was removed — would otherwise leave that count stuck
 * above zero and the window focusable for as long as the panel stayed open.
 */
@Composable
private fun Modifier.reportingFocus(onFocus: (Boolean) -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (focused) onFocus(false) }
    }
    return onFocusChanged {
        if (it.isFocused != focused) {
            focused = it.isFocused
            onFocus(it.isFocused)
        }
    }
}

/**
 * A field holding one number, reporting its focus so the window can open a keyboard (`OV-20`).
 *
 * [range] decides what happens to a value outside it, and the two cases are deliberately different.
 * A range that belongs to **Clickify** — a repeat count, a delay — coerces silently and shows the
 * bounds, because "0 repeats" is not something anyone meant and there is nothing to explain. A
 * range that belongs to **the platform** is passed as null: the value goes through untouched and
 * `SM-17` refuses it in the violations list, where the user is told which device limit they met.
 */
@Composable
internal fun NumberField(
    label: String,
    value: Long,
    onValue: (Long) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    range: LongRange? = null,
) {
    var text by remember { mutableStateOf(value.toString()) }
    val typed = text.toLongOrNull()
    val outOfRange = range != null && typed != null && typed !in range

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(MAX_DIGITS)
            text = digits
            val parsed = digits.toLongOrNull() ?: 0L
            onValue(if (range == null) parsed else parsed.coerceIn(range.first, range.last))
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        isError = outOfRange,
        supportingText =
            if (range != null && outOfRange) {
                { Text(stringResource(R.string.step_range, range.first, range.last)) }
            } else {
                null
            },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier.reportingFocus(onFocus),
    )
}

/** The `setText` string (`SM-9`). The one field in this app that needs a full keyboard. */
@Composable
internal fun TextEntryField(
    value: String,
    onValue: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LabelledTextField(
        label = stringResource(R.string.step_text),
        value = value,
        onValue = onValue,
        onFocus = onFocus,
        modifier = modifier,
    )
}

/** A field holding one line of text, reporting its focus the same way [NumberField] does. */
@Composable
internal fun LabelledTextField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        modifier = modifier.fillMaxWidth().reportingFocus(onFocus),
    )
}

/** SM-10: named, because a number here would mean nothing to the person reading it. */
@Composable
internal fun GlobalActionKind.label(): String =
    stringResource(
        when (this) {
            GlobalActionKind.BACK -> R.string.global_back
            GlobalActionKind.HOME -> R.string.global_home
            GlobalActionKind.RECENTS -> R.string.global_recents
            GlobalActionKind.NOTIFICATIONS -> R.string.global_notifications
            GlobalActionKind.QUICK_SETTINGS -> R.string.global_quick_settings
            GlobalActionKind.LOCK_SCREEN -> R.string.global_lock_screen
            GlobalActionKind.TAKE_SCREENSHOT -> R.string.global_screenshot
        },
    )

/**
 * SM-17, said out loud.
 *
 * Each one names the bound that was met and, where the bound is the device's, says so — the user
 * has not done anything wrong, they have met a limit belonging to the phone in their hand.
 */
@Composable
internal fun StepViolation.describe(): String =
    when (this) {
        is StepViolation.TooManyPaths -> stringResource(R.string.violation_too_many_paths, count, maximum)
        is StepViolation.TooFewPaths -> stringResource(R.string.violation_too_few_paths, minimum)
        is StepViolation.DurationTooLong ->
            stringResource(R.string.violation_duration_too_long, milliseconds, maximum)

        StepViolation.ZeroDuration -> stringResource(R.string.violation_zero_duration)
        is StepViolation.PointOutsideScreen -> stringResource(R.string.violation_point_outside, point.x, point.y)
        is StepViolation.TextTooLong -> stringResource(R.string.violation_text_too_long, length, maximum)
        is StepViolation.TemplateMissing -> stringResource(R.string.recognition_missing)
    }
