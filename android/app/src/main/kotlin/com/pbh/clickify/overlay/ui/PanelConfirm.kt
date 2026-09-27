package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * OV-36, SM-18: a question drawn over the panel it is about.
 *
 * Not a `Dialog`. A dialogue is a window of its own, and every window this application owns is an
 * Overlay with its own token, its own focus rules and its own place in the stacking order —
 * `OV-13` already spends a re-attach per state change keeping Stop on top, and a fourth window
 * that can appear at any moment would join that queue. Drawn inside the panel's own window the
 * question inherits all of it and can be reasoned about as part of the sheet.
 *
 * The scrim answers [onDismiss], because tapping beside a question is a way of declining it that
 * needs no reading. The destructive answer is never the one under the thumb by default.
 */
@Composable
internal fun BoxScope.PanelConfirm(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = interactions, indication = null, onClick = onDismiss),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.padding(PANEL_GUTTER).widthIn(max = 360.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    TextButton(onClick = onDismiss) { Text(dismissLabel) }
                    Button(
                        onClick = onConfirm,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text(confirmLabel) }
                }
            }
        }
    }
}

/** Dark enough that the sheet behind reads as unavailable, light enough to keep it recognisable. */
private const val SCRIM_ALPHA = 0.62f
