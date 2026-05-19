package com.giwon.babylog.common

import org.slf4j.LoggerFactory
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 클라이언트가 한국어 에러 메시지를 즉시 표시할 수 있도록 모든 예외를
 * { success, error } 응답으로 매핑.
 *
 * - 4xx: 사용자 입력/요청 문제 (디버그 로그)
 * - 5xx: 서버 측 문제 (warn 로그 + stack)
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(e: IllegalArgumentException): ApiResponse<Nothing> {
        log.debug("400 bad request: {}", e.message)
        return ApiResponse.error(e.message ?: "잘못된 요청이에요")
    }

    /** queryForObject 가 결과 0건일 때 던지는 예외 → 404. */
    @ExceptionHandler(EmptyResultDataAccessException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(e: EmptyResultDataAccessException): ApiResponse<Nothing> {
        log.debug("404 not found: {}", e.message)
        return ApiResponse.error("요청한 데이터를 찾을 수 없어요")
    }

    @ExceptionHandler(NoSuchElementException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNoSuchElement(e: NoSuchElementException): ApiResponse<Nothing> {
        log.debug("404 no such element: {}", e.message)
        return ApiResponse.error(e.message ?: "요청한 데이터를 찾을 수 없어요")
    }

    /** @Valid 검증 실패 → 400. 첫 번째 필드 에러 메시지만 노출 (한국어). */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(e: MethodArgumentNotValidException): ApiResponse<Nothing> {
        val first = e.bindingResult.fieldErrors.firstOrNull()
        val msg = first?.let { "${it.field}: ${it.defaultMessage ?: "유효하지 않은 값"}" }
            ?: "입력값이 유효하지 않아요"
        log.debug("400 validation: {}", msg)
        return ApiResponse.error(msg)
    }

    /** JSON 파싱 실패 / 잘못된 request body → 400. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUnreadable(e: HttpMessageNotReadableException): ApiResponse<Nothing> {
        log.debug("400 unreadable body: {}", e.message)
        return ApiResponse.error("요청 본문 형식이 잘못됐어요")
    }

    /** 필수 쿼리 파라미터 누락 → 400. */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingParam(e: MissingServletRequestParameterException): ApiResponse<Nothing> {
        log.debug("400 missing param: {}", e.message)
        return ApiResponse.error("필수 파라미터가 없어요: ${e.parameterName}")
    }

    /** path/query 파라미터 타입 불일치 → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ApiResponse<Nothing> {
        log.debug("400 type mismatch: name={} value={}", e.name, e.value)
        return ApiResponse.error("파라미터 형식이 잘못됐어요: ${e.name}")
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleServerError(e: Exception): ApiResponse<Nothing> {
        log.warn("500 server error", e)
        return ApiResponse.error(e.message ?: "서버 오류가 발생했어요")
    }
}
