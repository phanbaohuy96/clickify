package com.pbh.clickify.overlay.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pbh.clickify.domain.scenario.ScenarioLimits
import com.pbh.clickify.domain.scenario.TemplateSearch

/** OV-38: every icon button in the panel is this one, so a row of them has one rhythm. */
@Composable
internal fun PanelIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(PANEL_ICON_BUTTON)) {
        Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(PANEL_ICON))
    }
}

/** OV-38: what a section of the panel is called, in the one style they all share. */
@Composable
internal fun PanelSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A label with a minus and a plus, which is a slider that cannot be nudged by a scrolling sheet. */
@Composable
internal fun Stepper(
    label: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onLess, modifier = Modifier.padding(end = 2.dp)) { Text("−") }
        TextButton(onClick = onMore) { Text("+") }
    }
}

/** TP-30: nudged by a step, and never out of the range SM-16 would have clamped it into anyway. */
internal fun TemplateSearch.byThreshold(delta: Double): TemplateSearch =
    copy(threshold = (threshold + delta).coerceIn(ScenarioLimits.matchThreshold))

internal fun TemplateSearch.byWait(delta: Int): TemplateSearch =
    copy(waitMilliseconds = (waitMilliseconds + delta).coerceIn(ScenarioLimits.waitMilliseconds))

internal fun Int.asSeconds(): String = "%.1fs".format(this / MILLISECONDS_IN_A_SECOND)

private const val MILLISECONDS_IN_A_SECOND = 1_000f
