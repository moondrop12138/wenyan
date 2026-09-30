package com.wenyan.app.data.repository

/**
 * Base URL 规范化与校验（v1.7.x 新增，防「填了完整端点 / 尾斜杠 / 混入非法字符」导致的 404）
 *
 * 背景：用户填 Base URL 时可能把完整端点（.../v1/chat/completions）、尾斜杠、
 * 或复制带入的逗号/空格/换行等异常字符填进去。温言请求端是 `"$baseUrl/chat/completions"` 硬拼
 * （LlmClient），任何多余后缀都会拼出坏 URL → 404，且错误提示被掩盖，极难定位。
 *
 * 规则：
 * 1. trim 首尾空白；空串直接拒绝（F22：此前空串原样放行落库，请求会拼出无 scheme 的相对路径）；
 * 2. 校验字符集：仅允许 URL 标准字符（RFC 3986 子集），**排除逗号/空格/中文/换行** 等易混入字符；
 * 3. 拒绝含 `?`/`#`（query/fragment）的输入（F22：本客户端 baseUrl 从不支持带 query——path 恒由
 *    客户端硬拼 "/chat/completions"，追加部分会被吞进 query/fragment 恒 404，正是本类要消灭的故障）；
 * 4. 去尾斜杠（防 `.../v1//chat/completions`）；
 * 5. 剥尾部 `/chat/completions`（用户误填完整端点 → 还原为根地址）；
 * 6. **不自动补 /v1**（DeepSeek 等预置 provider 就是无 /v1 的根地址，补了反而错）。
 */
object ProviderUrlNormalizer {

    /** URL 标准字符集（不含逗号、空格、控制字符；`-` 放末尾避免区间歧义） */
    private val ALLOWED = Regex("^[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+;=%\\-]+$")

    /**
     * 规范化 + 校验。
     * @return 规范化后的 URL；**null** 表示校验不过（空串 / 含非法字符 / 含 `?`/`#`，
     * 调用方应阻止保存并提示用户）。
     */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        // F22：空串交 UI 层拦截（此前原样返回空串可落库）
        if (trimmed.isEmpty()) return null
        // F22：显式拒绝 query/fragment（ALLOWED 字符集里的 ?/# 不再放行）
        if (!ALLOWED.matches(trimmed) || trimmed.contains('?') || trimmed.contains('#')) return null
        var url = trimmed
        while (url.endsWith("/")) url = url.dropLast(1)
        // 误填完整端点：.../v1/chat/completions → .../v1（LlmClient 会再拼 /chat/completions）
        url = url.removeSuffix("/chat/completions")
        return url
    }
}
