package com.pbh.clickify.core.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** User-facing text that can be dynamic or resolved from Android string resources. */
sealed interface UiText {
    /** Runtime string that does not need resource lookup. */
    data class Dynamic(
        val value: String,
    ) : UiText

    /**
     * String resource reference resolved by Composables or Android [Context].
     *
     * [args] fill the resource's format placeholders. They are values the user is meant to read —
     * a count, a version number — and never text that itself needs translating; nested text is a
     * sign the message should be assembled by the screen that owns it instead.
     */
    data class Resource(
        @param:StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    /**
     * Resolves this text inside composition.
     *
     * The empty-[args] case calls the plain overload rather than spreading an empty array: the two
     * are different methods, and the formatting one strips a literal `%` from a string that has no
     * placeholders. `SpreadOperator` is suppressed for the other case because both APIs take a
     * vararg and there is no other call to make.
     */
    @Suppress("SpreadOperator")
    @Composable
    fun asString(): String =
        when (this) {
            is Dynamic -> value
            is Resource -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
        }

    /** Resolves this text from a non-composable Android [context]. */
    @Suppress("SpreadOperator")
    fun asString(context: Context): String =
        when (this) {
            is Dynamic -> value
            is Resource -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
        }
}
