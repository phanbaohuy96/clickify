package com.pbh.clickify.feature.scenario.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pbh.clickify.R
import com.pbh.clickify.core.ui.BaseScreen
import com.pbh.clickify.core.ui.InterfaceLanguage
import com.pbh.clickify.domain.repository.StoredScenario
import com.pbh.clickify.feature.onboarding.readPermissionStatus
import com.pbh.clickify.overlay.OverlayService

/**
 * The Activity surface: set the app up, manage Scenarios, and hand one to the Overlay.
 *
 * Nothing is authored here, and since `OV-26` nothing is even watched here: opening a Scenario
 * starts [OverlayService] and then sends this Activity to the back, because the application the
 * user wants to automate is somewhere else. See [ADR-0015] on why the two surfaces are separate.
 */
@Composable
fun ScenarioListScreen(
    onSetUp: () -> Unit,
    onPreview: (java.util.UUID) -> Unit,
    viewModel: ScenarioListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(context.readPermissionStatus().ready) }
    var confirmingDelete by remember { mutableStateOf<StoredScenario?>(null) }
    var renaming by remember { mutableStateOf<StoredScenario?>(null) }
    val languageTag by viewModel.state.collectAsStateWithLifecycle()

    // PM-9: a permission can be turned off while the app is in the background, and that is a
    // supported thing to do rather than an error. The answer is re-read, never remembered.
    LifecycleResumeEffect(Unit) {
        ready = context.readPermissionStatus().ready
        onPauseOrDispose {}
    }

    BaseScreen(
        viewModel = viewModel,
        title = stringResource(R.string.app_name),
        actions = {
            LanguageMenu(
                chosen = languageTag.languageTag,
                onChoose = viewModel::chooseLanguage,
            )
            TextButton(onClick = onSetUp) { Text(stringResource(R.string.onboarding_reopen)) }
        },
        onEffect = { effect ->
            when (effect) {
                is ScenarioListEffect.OpenOverlay ->
                    if (!ready) {
                        onSetUp()
                    } else {
                        OverlayService.open(context, effect.scenarioId)
                        // OV-26: the whole point of the Overlay is that the user is somewhere
                        // else. Staying in front would put the floating control on top of the one
                        // application nobody wants to automate.
                        context.findActivity()?.moveTaskToBack(true)
                    }
            }
        },
    ) { state, padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!ready) {
                    item { SetUpCard(onSetUp) }
                }
                if (state.scenarios.isEmpty() && !state.loading) {
                    item { EmptyState() }
                }
                items(state.scenarios, key = { it.scenario.id }) { stored ->
                    ScenarioRow(
                        stored = stored,
                        onOpen = { viewModel.open(stored) },
                        onPreview = { onPreview(stored.scenario.id) },
                        onRename = { renaming = stored },
                        onDuplicate = { viewModel.duplicate(stored) },
                        onDelete = { confirmingDelete = stored },
                    )
                }
            }

            ExtendedFloatingActionButton(
                onClick = { viewModel.createScenario(context.getString(R.string.scenario_default_name)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.scenario_new)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    renaming?.let { target ->
        RenameDialog(
            stored = target,
            onRename = { viewModel.rename(target, it) },
            onDismiss = { renaming = null },
        )
    }

    confirmingDelete?.let { target ->
        // Deleting a Scenario removes a directory of the user's own work, and there is no undo.
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text(stringResource(R.string.scenario_delete_title, target.scenario.name)) },
            text = { Text(stringResource(R.string.scenario_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    confirmingDelete = null
                }) { Text(stringResource(R.string.scenario_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text(stringResource(R.string.step_cancel))
                }
            },
        )
    }
}

@Composable
private fun SetUpCard(onSetUp: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSetUp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.onboarding_not_ready),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** An empty list is the first thing a new user sees, so it says what to do rather than "none". */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.scenario_none_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.scenario_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Roughly 65 characters, which is where a line stops being comfortable to read.
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}

/**
 * Renaming, with the Save this app otherwise does not have.
 *
 * A dialogue rather than an editable row, because the list is a list of things to open and a text
 * field in it would be a target for the tap that meant "open this one".
 */
@Composable
private fun RenameDialog(
    stored: StoredScenario,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(stored.scenario.id) { mutableStateOf(stored.scenario.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scenario_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.scenario_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(name)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.step_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.step_cancel)) }
        },
    )
}

@Composable
private fun ScenarioRow(
    stored: StoredScenario,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !stored.readOnly, onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stored.scenario.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        if (stored.readOnly) {
                            // FS-14: it can still be deleted, because that is done on purpose.
                            stringResource(R.string.scenario_too_new)
                        } else {
                            pluralStringResource(
                                R.plurals.overlay_steps,
                                stored.scenario.steps.size,
                                stored.scenario.steps.size,
                            )
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(horizontal = 2.dp))
            RowMenu(
                readOnly = stored.readOnly,
                onPreview = onPreview,
                onRename = onRename,
                onDuplicate = onDuplicate,
                onDelete = onDelete,
            )
        }
    }
}

/**
 * Rename, duplicate and delete, behind one control.
 *
 * FS-14: a file this build cannot decode offers only Delete. Renaming it would write this build's
 * schema over a document whose Steps are in the part it could not read, and duplicating it would
 * make a second copy of the same loss — which is the requirement, said in the interface.
 */
@Composable
private fun RowMenu(
    readOnly: Boolean,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.scenario_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!readOnly) {
                // MP-1: reading a Scenario, kept off the row's own tap, which opens the Overlay.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.map_menu_preview)) },
                    onClick = {
                        open = false
                        onPreview()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.scenario_rename)) },
                    onClick = {
                        open = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.scenario_duplicate)) },
                    onClick = {
                        open = false
                        onDuplicate()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scenario_delete)) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * IL-2: the five languages, under their own names, plus the phone's own.
 *
 * In the Activity rather than in the Overlay, and that is not an oversight: the Overlay is what
 * the user opens when they are somewhere else entirely, and a settings menu is not what they went
 * there for. This is the surface that already holds the set-up entry.
 */
@Composable
private fun LanguageMenu(
    chosen: String?,
    onChoose: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        // Its own name rather than a globe, because Material's core icon set has no globe and
        // because the name is the better label anyway: it says what will change *and* what it is
        // currently set to, in one word the user can already read.
        TextButton(onClick = { open = true }) {
            Text(InterfaceLanguage.entries.firstOrNull { it.tag == chosen }?.nativeName ?: stringResource(R.string.language))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_system)) },
                trailingIcon = { if (chosen == null) Icon(Icons.Default.Check, contentDescription = null) },
                onClick = {
                    open = false
                    onChoose(null)
                },
            )
            InterfaceLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.nativeName) },
                    trailingIcon = {
                        if (chosen == language.tag) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        open = false
                        onChoose(language.tag)
                    },
                )
            }
        }
    }
}

/** The Activity behind a Compose `LocalContext`, which is wrapped at least once by the theme. */
internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
