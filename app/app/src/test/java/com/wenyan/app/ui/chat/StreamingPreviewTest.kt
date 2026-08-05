package com.wenyan.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * StreamingPreview 流式预览测试（v1.6：reply 优先 + empathy 兜底 + 转义/截断）
 */
class StreamingPreviewTest {

    @Test
    fun `reply extracted from v2 json`() {
        val raw = """{"input_kind":"user_question","empathy":"先接住你","reply":"今天先忙自己的事","reply_timing":"明早"}"""
        assertEquals("今天先忙自己的事", StreamingPreview.extractReplyPreview(raw))
    }

    @Test
    fun `reply not yet present falls back to empathy`() {
        val raw = """{"input_kind":"pasted_chat","empathy":"这件事确实让人心里发堵","facts":{"""
        assertNull(StreamingPreview.extractReplyPreview(raw))
        assertEquals("这件事确实让人心里发堵", StreamingPreview.extractEmpathyPreview(raw))
    }

    @Test
    fun `empathy partial prefix while streaming`() {
        val raw = """{"input_kind":"pasted_chat","empathy":"这件事确实"""
        assertEquals("这件事确实", StreamingPreview.extractEmpathyPreview(raw))
    }

    @Test
    fun `reply_timing not matched as reply`() {
        val raw = """{"reply_timing":"明早","reply":"话术来了"}"""
        assertEquals("话术来了", StreamingPreview.extractReplyPreview(raw))
    }

    @Test
    fun `json escapes unescaped in reply`() {
        val raw = """{"reply":"她说\"有空\"\n第二行"}"""
        assertEquals("她说\"有空\"\n第二行", StreamingPreview.extractReplyPreview(raw))
    }

    @Test
    fun `unicode escape unescaped`() {
        val raw = """{"reply":"\u4f60\u597d"}"""
        assertEquals("你好", StreamingPreview.extractReplyPreview(raw))
    }

    @Test
    fun `unterminated escape returns prefix`() {
        val raw = """{"reply":"前面\u4f"""
        assertEquals("前面", StreamingPreview.extractReplyPreview(raw))
    }

    @Test
    fun `no field yet returns null`() {
        assertNull(StreamingPreview.extractReplyPreview("""{"input_kind":"""))
        assertNull(StreamingPreview.extractEmpathyPreview("""{"input_kind":"""))
    }

    @Test
    fun `blank value treated as not produced`() {
        val raw = """{"empathy":"","reply":"""
        assertNull(StreamingPreview.extractEmpathyPreview(raw))
    }

    @Test
    fun `legacy five step json still extracts reply`() {
        val raw = """{"steps":[{"key":"emotion"}],"reply":"老话术","citations":[]}"""
        assertEquals("老话术", StreamingPreview.extractReplyPreview(raw))
    }

    @Test
    fun `empty string returns null`() {
        assertNull(StreamingPreview.extractReplyPreview(""))
        assertNull(StreamingPreview.extractEmpathyPreview(""))
    }
}
