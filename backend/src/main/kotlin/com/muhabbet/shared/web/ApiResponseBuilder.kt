package com.muhabbet.shared.web

import com.muhabbet.shared.dto.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.Instant

/**
 * Extension functions for building consistent API responses.
 * Every controller should use these instead of building ResponseEntity manually.
 */
object ApiResponseBuilder {

    fun <T> ok(data: T): ResponseEntity<ApiResponse<T>> =
        ResponseEntity.ok(
            ApiResponse(data = data, timestamp = Instant.now().toString())
        )

    fun <T> created(data: T): ResponseEntity<ApiResponse<T>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse(data = data, timestamp = Instant.now().toString()))

    fun noContent(): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.ok(
            ApiResponse(data = null, timestamp = Instant.now().toString())
        )
}
