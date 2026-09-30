package com.wenyan.app.ui.chat

import com.wenyan.app.ui.contract.AnalysisMode

/**
 * 输入四分路由（v1.2）。无状态纯函数对象（照 FreetextSplitter 模式）——
 * F75 修复：原先路由逻辑内联在 ChatViewModel，单测只能验证一份手工复制的本地副本，
 * 生产逻辑改动测试照样全绿；抽出后 RouteByInputShapeTest 直接测试真实实现。
 *
 * 优先级：FIVE_STEP > RELAYED > GREETING > REPLY。
 * 关键修复：把「转述对方的话」从 REPLY 里拆出来——此前"她说我们只是朋友"
 * 会被当成用户自己的发言走共情+推进话术，方向完全反了（应先解读对方在划清边界）。
 */
internal object InputShapeRouter {

    /** "她说/他说/TA说/对方回/她回了句…" 等第三人称转述信号 */
    internal val RELAYED_PATTERN = Regex(
        "(他|她|TA|ta|对方|那人|那个|这人|这个)[^，。！？\\n]{0,4}(说|问|回|答|讲|提|发|写)"
    )

    /** 纯打招呼：你好/hi/在吗 类，长度≤10 字 */
    internal val GREETING_PATTERN = Regex(
        "^(你好|您好|hi|hello|hey|嗨|喂|在吗|在么|在不在|早|早上好|晚上好|下午好)[！!~。\\s]*$",
        RegexOption.IGNORE_CASE
    )

    fun route(text: String): AnalysisMode {
        val trimmed = text.trim()
        val isMultiLine = trimmed.contains('\n')
        val hasQuotes = trimmed.any { it == '"' || it == '“' || it == '”' || it == '\'' || it == '‘' || it == '’' }

        // 完整聊天记录优先，避免大段粘贴被转述信号截胡。
        // F33 精简：原 looksLikeChatLog = contains("：") && contains("\n") 恒被 isMultiLine
        // 蕴含（对同一串判同一个换行符），(A && B) || B ≡ B，是等价冗余分支，删除
        if (isMultiLine || hasQuotes || trimmed.length > 40) {
            return AnalysisMode.FIVE_STEP
        }

        if (RELAYED_PATTERN.containsMatchIn(trimmed)) return AnalysisMode.RELAYED
        if (trimmed.length <= 10 && GREETING_PATTERN.containsMatchIn(trimmed)) return AnalysisMode.GREETING
        return AnalysisMode.REPLY
    }
}
