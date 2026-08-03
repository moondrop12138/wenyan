package com.goutoujunshi.app.llm

/**
 * LLM 错误归一（llm-contract §4 错误码映射）
 * UI 只认文案 + 可重试标记 + 错误码。
 */
enum class LlmErrorCode(val userMessage: String, val retryable: Boolean) {
    UNAUTHORIZED("API Key 无效，请到设置检查", false),
    FORBIDDEN("服务拒绝访问，请检查账户状态", false),
    MODEL_NOT_FOUND("模型不存在，请检查模型名（可能已退役）", false),
    RATE_LIMITED("请求过于频繁或额度已用尽，稍后重试", true),
    SERVER_ERROR("模型服务异常，请稍后重试", true),
    CONNECT_TIMEOUT("连接超时，请检查网络或服务地址", true),
    READ_TIMEOUT("连接中断，可重试或停止", true),
    STREAM_ERROR("模型返回错误：", false),
    EMPTY_CONTENT("模型未返回内容，请重试", true),
    PARSE_ERROR("响应格式异常，请重试或更换模型", false),
    UNKNOWN("请求失败，请稍后重试", true),
    ;

    val code: Int = ordinal
}

/**
 * 错误映射器（ErrorMapper 单例）
 * HTTP 状态码 / 流中 error.type / 超时 / 空内容 / 解析失败 → LlmErrorCode
 */
object ErrorMapper {

    fun fromHttpStatus(status: Int): LlmErrorCode = when (status) {
        401 -> LlmErrorCode.UNAUTHORIZED
        403 -> LlmErrorCode.FORBIDDEN
        404 -> LlmErrorCode.MODEL_NOT_FOUND
        429 -> LlmErrorCode.RATE_LIMITED
        in 500..599 -> LlmErrorCode.SERVER_ERROR
        else -> LlmErrorCode.UNKNOWN
    }

    /**
     * 流中 error.type 归一（llm-contract §4：rate_limit → 429 语义）
     */
    fun fromStreamErrorType(type: String?): LlmErrorCode = when (type?.lowercase()) {
        "rate_limit", "rate_limited", "insufficient_quota", "insufficient_quota_error" ->
            LlmErrorCode.RATE_LIMITED
        "invalid_api_key", "authentication_error" -> LlmErrorCode.UNAUTHORIZED
        "model_not_found" -> LlmErrorCode.MODEL_NOT_FOUND
        else -> LlmErrorCode.STREAM_ERROR
    }
}
