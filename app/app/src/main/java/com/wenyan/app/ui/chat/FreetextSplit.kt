package com.wenyan.app.ui.chat

/**
 * v1.3.1 freetext 话术拆分器（纯函数，无 UI 依赖，可单测）。
 *
 * 把 freetext 回复拆成「话术 + 正文」两部分：
 * - 模型输出中以引导词（可以发/可以直接发/可以回/直接回/这样回/发这句/回这句/发这段话，
 *   及可以+引导词的组合）引出的话术段 → reply（完整内容，不截断）；
 * - 其余文本 → body（正文/解释）。
 * 无话术段时 reply 为空串，调用方退化为纯文本渲染。
 *
 * 与 RealChatRepository.extractReplySection（服务对话状态机，截 120 字）语义同源，
 * 引导词表一致（REPLY_SECTION_PATTERN），但这里额外加了前后汉字边界防正文嵌句误命中。
 */
internal data class FreetextSplit(
    val reply: String,
    val body: String,
)

internal object FreetextSplitter {

    /**
     * 话术引导词（v1.3.1 修复：去掉行首锚定——模型常把"可以回："写在段中）：
     * - 前看/后看汉字字母数字边界：拒绝"可以发了/这样回我/回这句话/你直接回她"等嵌句；
     * - 组合词（可以发这段话/可以直接回/可以这样回/可以回这句/可以发这句）先于单分支，防整体漏掉；
     * - 后随消费组（可选冒号/中文句号/全角句点/空白）：让 after 紧贴话术，QUOTE 的 ^ 锚定仍有效。
     */
    private val MARKER = Regex(
        "(?<![一-鿿A-Za-z0-9])(可以发这段话|可以直接发|可以直接回|可以这样回|可以回这句|可以发这句|可以发|可以回|直接回|这样回|发这句|回这句|发这段话)(?![一-鿿A-Za-z0-9])([：:。．.]?\\s*)"
    )

    /** 话术引号：行首锚定，懒惰匹配取完整内容（支持中文弯引号“”与英文/直角引号） */
    private val QUOTE = Regex("^[\"“「『](.+?)[\"”」』]")

    fun split(content: String): FreetextSplit {
        val raw = content.trim()
        if (raw.isEmpty()) return FreetextSplit("", "")

        val marker = MARKER.find(raw) ?: return FreetextSplit("", raw)
        val after = raw.substring(marker.range.last + 1)
        val trimmed = after.trimStart()

        // 提取话术：引号优先，其次首个非空行
        var reply = ""
        var quoteEnd = -1
        val quoted = QUOTE.find(trimmed)
        if (quoted != null) {
            reply = quoted.groupValues[1].trim()
            quoteEnd = raw.length - after.length + after.indexOf(trimmed) + quoted.range.last + 1
        } else {
            val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() }
            if (firstLine != null) {
                reply = firstLine.trim().trimStart('"', '“', '「', '『').trimEnd('"', '”', '」', '』').trim()
            }
        }
        if (reply.isBlank()) return FreetextSplit("", raw)

        // 话术段起点：用 group(1)（引导词本身）而非整个 match——(?m)^\s* 可能把行首换行吞进 match
        val segStart = marker.groups[1]?.range?.first ?: marker.range.first
        val segEnd = if (quoteEnd >= 0) {
            // 引号路径：右引号后；若紧接换行（话术行结束）则一并跳过
            val e = quoteEnd
            if (raw.getOrNull(e) == '\n') e + 1 else e
        } else {
            // 无引号路径：话术行行尾换行之后（避免正文残留话术行的换行）
            val nl = raw.indexOf('\n', segStart)
            if (nl < 0) raw.length else nl + 1
        }
        // 正文：引导词行之前 + 话术段之后。
        // 段中引导词时正文两部分可能原本同行（如 "…加戏。可以回：话术"），
        // 话术被提走后若拼接处无换行则补一个，保持正文分段；引号路径右引号后的换行保留。
        val before = raw.substring(0, segStart)
        val tail = raw.substring(segEnd)
        val body = buildString {
            append(before)
            if (before.isNotBlank() && tail.isNotBlank() &&
                !before.endsWith('\n') && !tail.startsWith('\n')
            ) {
                append('\n')
            }
            append(tail)
        }
            .trim()
            .trimStart('。', '，', '、', '；', '：')

        return FreetextSplit(reply, body)
    }
}
