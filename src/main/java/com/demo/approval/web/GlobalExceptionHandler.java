package com.demo.approval.web;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;
import com.demo.approval.web.dto.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException e) {
        return ResponseEntity.status(e.code().httpStatus()).body(ApiError.of(e.code().name(), e.getMessage()));
    }

    /** 唯一约束冲突 = 并发重复请求（重复投票 / 同一个幂等键同时到达）。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> duplicate(DataIntegrityViolationException e) {
        return ResponseEntity.status(ErrorCode.DUPLICATE_REQUEST.httpStatus())
                .body(ApiError.of(ErrorCode.DUPLICATE_REQUEST.name(), "重复请求，请刷新后查看最新状态"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(ApiError.of(ErrorCode.BAD_REQUEST.name(), e.getMessage()));
    }
}
