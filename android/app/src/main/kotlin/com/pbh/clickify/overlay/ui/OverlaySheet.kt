package com.pbh.clickify.overlay.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.pbh.clickify.overlay.OverlayScreen
import com.pbh.clickify.overlay.overlayScreen
import kotlinx.coroutines.launch

/** How much of the display the bottom sheet takes before it is dragged. */
private const val PEEK_FRACTION = 0.46f

/** The most it will ever take, leaving the control a row of its own above it. */
private const val FULL_FRACTION = 0.92f

/**
 * The gutter every row in the panel is measured from (`OV-38`).
 *
 * One number, used by the header, the body and the footer alike. Before it there were three — the
 * header was indented 63 pixels, the body 48 and the footer something else again — and the result
 * read as a list of things that had been placed rather than laid out.
 */
val PANEL_GUTTER = 16.dp

/** The size every icon button in the panel is, so a row of them has one rhythm. */
val PANEL_ICON_BUTTON = 40.dp

/** The glyph inside one. The difference between the two is what the insets below compensate for. */
val PANEL_ICON = 20.dp

/**
 * What a row of icon buttons is padded by so its **glyphs** land on [PANEL_GUTTER].
 *
 * An icon button is larger than its icon, because a touch target is larger than a thing to look
 * at. Padding the row by the gutter therefore puts the glyph a further 10dp in, which is the gap
 * that made the header look indented next to the body.
 */
val PANEL_ROW_INSET = PANEL_GUTTER - (PANEL_ICON_BUTTON - PANEL_ICON) / 2

/** The same compensation for a text button, whose label sits 12dp inside it. */
val PANEL_TEXT_BUTTON_INSET = PANEL_GUTTER - 12.dp

/**
 * OV-34, OV-37: the panel, as a sheet on whichever edge the phone's shape asks for.
 *
 * **In portrait** it is a bottom sheet with two phases, in this order, because that is what a sheet
 * on this platform does and a user who has used one other Android application already knows it:
 *
 *  1. **Drag to expand.** A swipe up grows the sheet towards [FULL_FRACTION]. The body does not
 *     move while there is still sheet to gain.
 *  2. **Scroll.** Once the sheet is as tall as it goes, the same continuing swipe scrolls the body.
 *     Swiping back down scrolls the body to its top first, and only then shrinks the sheet.
 *
 * The handoff is a [NestedScrollConnection] rather than two separate gestures, so one unbroken
 * finger movement crosses between them with nothing to re-grab.
 *
 * **In landscape** it is a full-height sheet against the end edge, and there is only the second
 * phase: it is already as tall as it can be, so the body scrolls from the first pixel. That falls
 * out of the same [OverlaySheetState] with its two heights equal, rather than being a second
 * implementation — [OverlaySheetState.consume] simply never has anything to take.
 *
 * **The sheet is sized by the window, not by an offset inside it.** The obvious implementation —
 * a window covering the display with the sheet placed inside it — would take every touch on the
 * screen, and `OV-21` needs the opposite: Markers stay draggable while the panel is open, which is
 * the only way to place a swipe's destination while looking at the swipe's settings. So the window
 * is no bigger than the sheet and everything outside it belongs to whatever is underneath.
 *
 * Dragging down does not dismiss. The sheet stops at its peek height and the header's own close
 * button is the way out — a Step being edited holds unsaved changes (`OV-36`), and a gesture that
 * threw them away by being slightly too long would be a gesture nobody could use confidently.
 */
@Composable
fun OverlayPanelSheet(
    screen: OverlayScreen?,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    confirm: (@Composable BoxScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val current = screen ?: remember(context) { context.overlayScreen() }
    val landscape = current.landscape
    val displayHeight = current.profile.heightPixels.toFloat()
    val state =
        remember(displayHeight, landscape) {
            if (landscape) {
                OverlaySheetState(peekHeight = displayHeight, fullHeight = displayHeight)
            } else {
                OverlaySheetState(peekHeight = displayHeight * PEEK_FRACTION, fullHeight = displayHeight * FULL_FRACTION)
            }
        }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val connection = remember(state) { state.nestedScroll() }

    OverlaySurface(
        modifier = modifier.then(if (landscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth()),
        shape = current.sheetShape(),
        tone = MaterialTheme.colorScheme.surfaceContainer,
        // The panel is never glass. It holds text fields and a list of Steps, and the one thing
        // that must never be hard to read is the thing the user is editing.
        glass = false,
    ) {
        Box {
            Column(
                modifier =
                    Modifier
                        // The window sits on an edge, which is where the keyboard opens. On API
                        // 30+ an overlay window receives IME insets while it holds focus, which it
                        // does exactly when a field here is being typed into (`OV-20`).
                        .imePadding()
                        .then(with(density) { sheetExtent(current, state.height) }),
            ) {
                SheetGrip(landscape = landscape, state = state, onSettle = { scope.launch { state.settle() } })
                header()
                Column(
                    modifier =
                        Modifier
                            .weight(weight = 1f, fill = landscape)
                            .nestedScroll(connection)
                            .verticalScroll(scroll)
                            .padding(horizontal = PANEL_GUTTER),
                    content = body,
                )
                // OV-32: the sheet owns the bottom of the **display**, so its own content is what
                // keeps Save and Cancel off the gesture bar. Before this the window stopped short
                // and a strip of the application underneath showed through below the sheet.
                Box(
                    modifier =
                        Modifier.padding(
                            bottom = if (landscape) 0.dp else with(density) { current.bounds.bottomInset.toDp() },
                        ),
                ) { footer() }
            }
            // OV-36: the question, drawn over the sheet it is about rather than in a window of its
            // own. A `Dialog` here would be a second window with its own token and its own focus
            // rules; this one is part of the panel, which is the thing the answer applies to.
            //
            // It measures itself against the sheet with `matchParentSize` and so has no say in how
            // big the sheet is. `fillMaxSize` here instead grew the window to the whole display —
            // the panel is `WRAP_CONTENT`, so anything inside it that asks for everything gets it.
            confirm?.invoke(this)
        }
    }
}

/**
 * How much room the sheet takes: a height it may grow to, or every pixel it has.
 *
 * In landscape the window is already exactly the display's height, so the content fills it and
 * pads itself off the system bars (`OV-32`); in portrait the window wraps the content, and what
 * is set is the ceiling the drag moves (`OV-34`).
 */
private fun Density.sheetExtent(
    screen: OverlayScreen,
    height: Float,
): Modifier =
    if (screen.landscape) {
        Modifier.fillMaxHeight().padding(
            top = screen.bounds.topInset.toDp(),
            end = screen.bounds.rightInset.toDp(),
            bottom = screen.bounds.bottomInset.toDp(),
        )
    } else {
        Modifier.heightIn(max = height.toDp())
    }

/** OV-34, OV-37: the thing that says the sheet moves — or the space where it would be. */
@Composable
private fun SheetGrip(
    landscape: Boolean,
    state: OverlaySheetState,
    onSettle: () -> Unit,
) {
    if (landscape) {
        Box(Modifier.padding(top = 12.dp))
        return
    }
    Grabber(
        modifier =
            Modifier.draggable(
                state = rememberDraggableState { delta -> state.consume(delta) },
                orientation = Orientation.Vertical,
                onDragStopped = { onSettle() },
            ),
    )
}

/** The corners that are not against an edge are the ones that get rounded. */
private fun OverlayScreen.sheetShape(): Shape =
    if (landscape) {
        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    }

/**
 * How tall the sheet is allowed to be, and where it settles when let go (`OV-34`).
 *
 * A maximum rather than a height. The content is measured against it and the window wraps whatever
 * that comes to, so a Step with two fields is a short sheet and dragging it does nothing visible —
 * which is right. There is no empty space to expand into, and a sheet that grew into some anyway
 * would be a large dark rectangle over the screen the user is trying to look at.
 *
 * The two heights being **equal** is how landscape is expressed (`OV-37`): there is nothing to
 * expand into, so every pixel of a swipe is left for the body to scroll with.
 */
@Stable
class OverlaySheetState(
    private val peekHeight: Float,
    private val fullHeight: Float,
) {
    var height by mutableFloatStateOf(peekHeight)
        private set

    /**
     * Applies a scroll delta and reports back how much of it was used.
     *
     * Signs follow Compose's scroll convention: a negative [deltaY] is the finger moving up, which
     * grows the sheet. Returning the amount used in the same convention is what lets this be the
     * return value of `onPreScroll` directly.
     */
    fun consume(deltaY: Float): Float {
        val settled = (height - deltaY).coerceIn(peekHeight, fullHeight)
        val used = height - settled
        height = settled
        return used
    }

    /** Snaps to whichever of the two heights is nearer, once the finger is off. */
    suspend fun settle() {
        val target = if (height > (peekHeight + fullHeight) / 2f) fullHeight else peekHeight
        animate(initialValue = height, targetValue = target, animationSpec = spring()) { value, _ -> height = value }
    }

    internal fun nestedScroll(): NestedScrollConnection =
        object : NestedScrollConnection {
            /** Phase 1: every upward pixel grows the sheet until there is no sheet left to gain. */
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (available.y < 0) Offset(0f, consume(available.y)) else Offset.Zero

            /** Phase 2, in reverse: the body scrolls to its top first, then the sheet shrinks. */
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (available.y > 0) Offset(0f, consume(available.y)) else Offset.Zero

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                settle()
                return Velocity.Zero
            }
        }
}

/** The grabber. It is the affordance that says the sheet moves at all. */
@Composable
private fun Grabber(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(width = 36.dp, height = 4.dp),
            content = {},
        )
    }
}
