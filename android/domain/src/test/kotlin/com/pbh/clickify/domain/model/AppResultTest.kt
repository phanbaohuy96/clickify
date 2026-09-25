package com.pbh.clickify.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AppResultTest {
    @Test
    fun `map transforms the data of a success`() {
        val result: AppResult<Int> = AppResult.Success(2)

        assertEquals(AppResult.Success("2"), result.map { it.toString() })
    }

    @Test
    fun `map leaves a failure untouched and never runs the transform`() {
        val result: AppResult<Int> = AppResult.Failure(DomainError.Unknown)
        var transformed = false

        val mapped =
            result.map {
                transformed = true
                it.toString()
            }

        assertEquals(AppResult.Failure(DomainError.Unknown), mapped)
        assertEquals(false, transformed)
    }
}
