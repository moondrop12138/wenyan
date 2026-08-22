package com.wenyan.app.llm

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
    // v1.7.1 终检：公网地址必须 https；本地模型服务（LM Studio/Ollama）用 http://localhost
    UNSUPPORTED_URL("仅支持 https:// 地址；本地服务请填 http://localhost", false),
    STREAM_ERROR("模型返回错误：", false),
    EMPTY_CONTENT("模型未返回内容，请重试", true),
    PARSE_ERROR("响应格式异常，请重试或更换模型", false),
    // H2: finish_reason=length，回答被模型按长度上限截断，不可重试（重试仍会截断）
    OUTPUT_TRUNCATED("回答已达长度上限被截断", false),
    // L2: context length exceeded 等 400/413/422 归一为上下文过长（不可重试，重试仍会超）
    CONTEXT_TOO_LONG("上下文过长，请缩短输入或更换模型", false),
    // L11: 非「上下文过长」类 400（参数校验失败、图片格式错等）——不可重试，文案不误导
    BAD_REQUEST("请求参数有误，请检查输入或更换模型", false),
    UNKNOWN("请求失败，请稍后重试", true),
    ;

    val code: Int = ordinal
}

/**
 * 错误映射器（ErrorMapper 单例）
 * HTTP 状态码 / 流中 error.type / 超时 / 空内容 / 解析失败 → LlmErrorCode
 */
object ErrorMapper {

    /** L11: 判定响应体是否为上下文超长类错误（供 400 细分；大小写不敏感） */
    fun looksLikeContextLength(bodyHint: String?): Boolean {
        if (bodyHint.isNullOrBlank()) return false
        val b = bodyHint.lowercase()
        return b.contains("context length") ||
            b.contains("maximum context") ||
            b.contains("context_length_exceeded") ||
            b.contains("too many tokens") ||
            b.contains("reduce the length")
    }

    /**
     * L11 修复：原所有 HTTP 400 一律映射「上下文过长」——图片 data url 格式错、
     * 参数校验失败也提示「请缩短输入」。现按响应体关键词细分：
     * 命中上下文类关键词才归 CONTEXT_TOO_LONG，否则归不可重试的 BAD_REQUEST。
     */
    fun fromHttpStatus(status: Int, bodyHint: String? = null): LlmErrorCode = when (status) {
        400 -> if (looksLikeContextLength(bodyHint)) LlmErrorCode.CONTEXT_TOO_LONG else LlmErrorCode.BAD_REQUEST
        401 -> LlmErrorCode.UNAUTHORIZED
        403 -> LlmErrorCode.FORBIDDEN
        404 -> LlmErrorCode.MODEL_NOT_FOUND
        413 -> LlmErrorCode.CONTEXT_TOO_LONG
        422 -> if (looksLikeContextLength(bodyHint)) LlmErrorCode.CONTEXT_TOO_LONG else LlmErrorCode.BAD_REQUEST
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
