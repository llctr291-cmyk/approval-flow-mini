package com.demo.approval.common;

import org.springframework.http.HttpStatus;

/** 业务错误码，统一映射成 HTTP 状态，前端按 code 分支处理。 */
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    /** 版本号不匹配：单据已被他人处理，前端应刷新后重试。 */
    VERSION_CONFLICT(HttpStatus.CONFLICT),
    /** 同一人在同一节点同一轮次重复投票。 */
    ALREADY_VOTED(HttpStatus.CONFLICT),
    /** 并发重复请求（唯一键冲突）。 */
    DUPLICATE_REQUEST(HttpStatus.CONFLICT),
    /** 当前状态不允许该操作，例如流程已结束。 */
    INVALID_STATE(HttpStatus.UNPROCESSABLE_ENTITY),
    /** 流程定义本身不合法。 */
    INVALID_DEFINITION(HttpStatus.UNPROCESSABLE_ENTITY);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
