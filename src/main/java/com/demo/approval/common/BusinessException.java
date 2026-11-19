package com.demo.approval.common;

/** 可预期的业务异常，由 GlobalExceptionHandler 转成 ApiError。 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static BusinessException notFound(String what, String id) {
        return new BusinessException(ErrorCode.NOT_FOUND, what + " 不存在: " + id);
    }

    public static BusinessException versionConflict(long expected, long actual) {
        return new BusinessException(ErrorCode.VERSION_CONFLICT,
                "单据已被他人处理，请刷新后重试（expectedVersion=" + expected + ", currentVersion=" + actual + "）");
    }

    public static BusinessException concurrentModified() {
        return new BusinessException(ErrorCode.VERSION_CONFLICT, "并发冲突：其他人刚刚处理了这张单据，请刷新后重试");
    }
}
