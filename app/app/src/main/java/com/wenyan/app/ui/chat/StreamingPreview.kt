package com.wenyan.app.ui.chat

/**
 * 流式 JSON 正文预览器：
 * 模型按 prompt-architecture §4 输出一个 JSON 对象；流式期间直接展示累积原文会看到
 * {"empathy":"...","facts":{... 这种"思考代码"，体验极差（DeepSeek/ChatGPT 官方做法都是
 * 在 JSON 还没完整时只展示"正在思考/正在写回复"，正文只展示最终对用户有意义的字段）。
 *
 * 策略（v1.6）：
 * - 优先抽 "reply"（成品话术，= advice.styles[0].text）做打字机预览；
 * - reply 尚未出现时退回抽 "empathy"（共情段，模型按字段顺序先写它，预览能尽早出现）；
 * - 都抠不出 → 返回 null，UI 只显示"正在组织语言…"；
 * - 完整的 JSON 转义（\" \\ \n \t \b \f \r \uXXXX）按 JSON 规范还原；流式期间未结束的转义序列原样截断。
 */
object StreamingPreview {

    /** 从流式累积的 JSON 文本中抽取 reply 字段（成品话术）的预览文字；暂无则 null */
    fun extractReplyPreview(raw: String): String? = extractFieldPreview(raw, "reply")

    /** v1.6 兜底：reply 未出现时抽 empathy 字段（共情段）的预览文字；暂无则 null */
    fun extractEmpathyPreview(raw: String): String? = extractFieldPreview(raw, "empathy")

    /**
     * 从流式累积的 JSON 文本中抽取指定字段的字符串预览。
     * @return null 表示当前还没有可展示的内容；否则返回目前已累积的字段值前缀
     */
    fun extractFieldPreview(raw: String, key: String): String? {
        val keyIdx = findFieldKey(raw, key) ?: return null
        // 定位 key 后的冒号
        var i = keyIdx
        while (i < raw.length && raw[i] != ':') i++
        if (i >= raw.length) return null
        i++
        // 跳过空白
        while (i < raw.length && raw[i].isWhitespace()) i++
        if (i >= raw.length || raw[i] != '"') return null
        i++

        // 逐字符读取字符串，处理转义
        val sb = StringBuilder()
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' -> {
                    // 流式期间转义序列可能不完整：保留未消费的部分并退出
                    if (i + 1 >= raw.length) {
                        return sb.toString().takeIf { it.isNotEmpty() }
                    }
                    val n = raw[i + 1]
                    when (n) {
                        '"' -> { sb.append('"'); i += 2 }
                        '\\' -> { sb.append('\\'); i += 2 }
                        '/' -> { sb.append('/'); i += 2 }
                        'b' -> { sb.append('\b'); i += 2 }
                        'f' -> { sb.append('\u000C'); i += 2 }
                        'n' -> { sb.append('\n'); i += 2 }
                        'r' -> { sb.append('\r'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        'u' -> {
                            // \uXXXX 需要 4 位十六进制；收不齐就视为流式未到位，先返回已有内容
                            if (i + 6 > raw.length) {
                                return sb.toString().takeIf { it.isNotEmpty() }
                            }
                            val hex = raw.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                            } else {
                                return sb.toString().takeIf { it.isNotEmpty() }
                            }
                        }
                        else -> { sb.append(n); i += 2 }
                    }
                }
                c == '"' -> { i++; break }
                else -> { sb.append(c); i++ }
            }
        }
        val preview = sb.toString()
        // 空白预览视为"尚未产出"（无论字符串是否已闭合——避免 UI 渲染空气泡）
        return if (preview.isBlank()) null else preview
    }

    /** 在累积文本里找 "key" 这个字段（注意避免匹配到同前缀字段，如 reply vs reply_timing） */
    private fun findFieldKey(raw: String, key: String): Int? {
        val needle = "\"$key\""
        var searchFrom = 0
        while (true) {
            val idx = raw.indexOf(needle, searchFrom)
            if (idx < 0) return null
            val afterIdx = idx + needle.length
            // 排除 "reply_timing" 之类同前缀字段：后一个非空白字符必须是 ':'
            var j = afterIdx
            while (j < raw.length && raw[j].isWhitespace()) j++
            if (j < raw.length && raw[j] == ':') {
                return idx
            }
            searchFrom = afterIdx
        }
    }
}
