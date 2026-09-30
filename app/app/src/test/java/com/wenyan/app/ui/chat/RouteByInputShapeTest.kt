package com.wenyan.app.ui.chat

import com.wenyan.app.ui.contract.AnalysisMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 输入四分路由单测（v1.2，InputShapeRouter.route）。
 *
 * 回归案例（2026-08-04 用户反馈）：「她说我们只是朋友」曾被路由到 REPLY，
 * 模型当成用户自己的发言给"继续推进"话术，方向完全反了——
 * 它是【对方话语的转述】，必须判 RELAYED（先解读对方意图）。
 *
 * F75 修复：本测试原先只验证一份手工复制的本地 route() 副本与复制正则，
 * 生产路由逻辑（ChatViewModel.routeByInputShape）被改动后测试照样全绿，形同虚设。
 * 现路由逻辑已抽为无状态 InputShapeRouter（照 FreetextSplitter 模式，ChatViewModel 直接委托），
 * 这里直接调用真实实现，生产逻辑改动即会变红。
 */
class RouteByInputShapeTest {

    // ===== 回归：截图翻车案例 =====

    @Test
    fun `relayed quote - 她说我们只是朋友 must be RELAYED`() {
        assertEquals(AnalysisMode.RELAYED, InputShapeRouter.route("她说我们只是朋友"))
    }

    @Test
    fun `user question - 那我还该追她吗 must be REPLY`() {
        assertEquals(AnalysisMode.REPLY, InputShapeRouter.route("那我还该追她吗"))
    }

    // ===== 四分边界 =====

    @Test
    fun `pasted chat - multi line with speakers must be FIVE_STEP`() {
        val chat = "小明：在吗\n小红：怎么了\n小明：周末一起吃饭？"
        assertEquals(AnalysisMode.FIVE_STEP, InputShapeRouter.route(chat))
    }

    @Test
    fun `pasted chat - quoted text must be FIVE_STEP`() {
        assertEquals(AnalysisMode.FIVE_STEP, InputShapeRouter.route("她说“我们只是朋友”"))
    }

    @Test
    fun `relayed - 他问我周末有空吗`() {
        assertEquals(AnalysisMode.RELAYED, InputShapeRouter.route("他问我周末有空吗"))
    }

    @Test
    fun `relayed - 对方回了句随便`() {
        assertEquals(AnalysisMode.RELAYED, InputShapeRouter.route("对方回了句随便"))
    }

    @Test
    fun `relayed - TA说要考虑一下`() {
        assertEquals(AnalysisMode.RELAYED, InputShapeRouter.route("TA说要考虑一下"))
    }

    @Test
    fun `greeting - 你好`() {
        assertEquals(AnalysisMode.GREETING, InputShapeRouter.route("你好"))
    }

    @Test
    fun `greeting - 在吗`() {
        assertEquals(AnalysisMode.GREETING, InputShapeRouter.route("在吗"))
    }

    @Test
    fun `user question - 这句怎么回`() {
        assertEquals(AnalysisMode.REPLY, InputShapeRouter.route("这句怎么回"))
    }

    @Test
    fun `user question - 我今天心情不太好`() {
        assertEquals(AnalysisMode.REPLY, InputShapeRouter.route("我今天心情不太好"))
    }

    @Test
    fun `user question - 我该主动约她吗`() {
        // 含"她"但无引语动词（约 不是 说/问/回），是用户自己的提问
        assertEquals(AnalysisMode.REPLY, InputShapeRouter.route("我该主动约她吗"))
    }

    @Test
    fun `long input over 40 chars must be FIVE_STEP`() {
        val long = "我们认识三个月了一直暧昧但她最近回复越来越慢我不知道是不是哪里做错了要不要直接问清楚"
        assertEquals(AnalysisMode.FIVE_STEP, InputShapeRouter.route(long))
    }

    @Test
    fun `chat log takes priority over relayed signal`() {
        // 含"她说"但整体是多行聊天记录 → 五步法优先，不被转述信号截胡
        val chat = "她：在忙\n我：她说我们只是朋友是真的吗\n她：别想太多"
        assertEquals(AnalysisMode.FIVE_STEP, InputShapeRouter.route(chat))
    }
}
