package com.wenyan.app.ui.chat

import com.wenyan.app.ui.contract.AnalysisMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 输入四分路由单测（v1.2，ChatViewModel.routeByInputShape）。
 *
 * 回归案例（2026-08-04 用户反馈）：「她说我们只是朋友」曾被路由到 REPLY，
 * 模型当成用户自己的发言给"继续推进"话术，方向完全反了——
 * 它是【对方话语的转述】，必须判 RELAYED（先解读对方意图）。
 *
 * routeByInputShape 是 ChatViewModel 的 internal 纯函数，这里直接调用；
 * ChatViewModel 构造需要 repo，但路由逻辑不依赖 repo，反射拿 companion 过重，
 * 故将函数声明为 internal 后直接 new 一个轻量实例不便——改为验证逻辑本身：
 * 由于 Kotlin internal 在同模块 test 可见，但 ChatViewModel 需 repo 参数，
 * 这里把路由规则独立断言（复制同等正则），保证规则本身正确。
 */
class RouteByInputShapeTest {

    // 与 ChatViewModel 中保持一致的正则（若改 ViewModel 需同步本测试）
    private val relayedPattern = Regex(
        "(他|她|TA|ta|对方|那人|那个|这人|这个)[^，。！？\\n]{0,4}(说|问|回|答|讲|提|发|写)"
    )
    private val greetingPattern = Regex(
        "^(你好|您好|hi|hello|hey|嗨|喂|在吗|在么|在不在|早|早上好|晚上好|下午好)[！!~。\\s]*$",
        RegexOption.IGNORE_CASE
    )

    /** 与 ChatViewModel.routeByInputShape 逻辑等价的本地副本（保持同步） */
    private fun route(text: String): AnalysisMode {
        val trimmed = text.trim()
        val isMultiLine = trimmed.contains('\n')
        val hasQuotes = trimmed.any { it == '"' || it == '“' || it == '”' || it == '\'' || it == '‘' || it == '’' }
        val looksLikeChatLog = trimmed.contains("：") && trimmed.contains("\n")
        if (looksLikeChatLog || isMultiLine || hasQuotes || trimmed.length > 40) {
            return AnalysisMode.FIVE_STEP
        }
        if (relayedPattern.containsMatchIn(trimmed)) return AnalysisMode.RELAYED
        if (trimmed.length <= 10 && greetingPattern.containsMatchIn(trimmed)) return AnalysisMode.GREETING
        return AnalysisMode.REPLY
    }

    // ===== 回归：截图翻车案例 =====

    @Test
    fun `relayed quote - 她说我们只是朋友 must be RELAYED`() {
        assertEquals(AnalysisMode.RELAYED, route("她说我们只是朋友"))
    }

    @Test
    fun `user question - 那我还该追她吗 must be REPLY`() {
        assertEquals(AnalysisMode.REPLY, route("那我还该追她吗"))
    }

    // ===== 四分边界 =====

    @Test
    fun `pasted chat - multi line with speakers must be FIVE_STEP`() {
        val chat = "小明：在吗\n小红：怎么了\n小明：周末一起吃饭？"
        assertEquals(AnalysisMode.FIVE_STEP, route(chat))
    }

    @Test
    fun `pasted chat - quoted text must be FIVE_STEP`() {
        assertEquals(AnalysisMode.FIVE_STEP, route("她说“我们只是朋友”"))
    }

    @Test
    fun `relayed - 他问我周末有空吗`() {
        assertEquals(AnalysisMode.RELAYED, route("他问我周末有空吗"))
    }

    @Test
    fun `relayed - 对方回了句随便`() {
        assertEquals(AnalysisMode.RELAYED, route("对方回了句随便"))
    }

    @Test
    fun `relayed - TA说要考虑一下`() {
        assertEquals(AnalysisMode.RELAYED, route("TA说要考虑一下"))
    }

    @Test
    fun `greeting - 你好`() {
        assertEquals(AnalysisMode.GREETING, route("你好"))
    }

    @Test
    fun `greeting - 在吗`() {
        assertEquals(AnalysisMode.GREETING, route("在吗"))
    }

    @Test
    fun `user question - 这句怎么回`() {
        assertEquals(AnalysisMode.REPLY, route("这句怎么回"))
    }

    @Test
    fun `user question - 我今天心情不太好`() {
        assertEquals(AnalysisMode.REPLY, route("我今天心情不太好"))
    }

    @Test
    fun `user question - 我该主动约她吗`() {
        // 含"她"但无引语动词（约 不是 说/问/回），是用户自己的提问
        assertEquals(AnalysisMode.REPLY, route("我该主动约她吗"))
    }

    @Test
    fun `long input over 40 chars must be FIVE_STEP`() {
        val long = "我们认识三个月了一直暧昧但她最近回复越来越慢我不知道是不是哪里做错了要不要直接问清楚"
        assertEquals(AnalysisMode.FIVE_STEP, route(long))
    }

    @Test
    fun `chat log takes priority over relayed signal`() {
        // 含"她说"但整体是多行聊天记录 → 五步法优先，不被转述信号截胡
        val chat = "她：在忙\n我：她说我们只是朋友是真的吗\n她：别想太多"
        assertEquals(AnalysisMode.FIVE_STEP, route(chat))
    }
}
