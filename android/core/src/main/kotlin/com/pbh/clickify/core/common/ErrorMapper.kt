package com.pbh.clickify.core.common

import com.pbh.clickify.domain.model.DomainError

/**
 * Turns an unexpected [Throwable] into a [DomainError].
 *
 * The single place where an exception becomes something the interface may show, so that adding a
 * case in slice A1 is one edit rather than a search. Everything maps to
 * [DomainError.Unknown] until there is a second case to distinguish.
 */
object ErrorMapper {
    /** Maps [throwable] onto the domain's failure taxonomy. */
    fun map(
        @Suppress("UNUSED_PARAMETER") throwable: Throwable,
    ): DomainError = DomainError.Unknown
}
