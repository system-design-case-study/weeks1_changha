package com.systemdesigncasestudy.weeks1changha.common.response;

public record ApiError(String code, String message, String requestId) {
}
