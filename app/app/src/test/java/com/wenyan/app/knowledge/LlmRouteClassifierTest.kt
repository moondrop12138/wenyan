package com.wenyan.app.knowledge

import com.wenyan.app.json.Json
import com.wenyan.app.json.JsonObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LlmRouteClassifier 五条路径（mock 分类客户端，不触网）：
 * 正常返回 / 超时 / 坏 JSON / 非法文件名 / 空集（合法弃权）。
 * 另覆盖未配置 Provider → 不可用。
 */
class LlmRouteClassifierTest {

    private val docA = "knowledge/03-依恋理论与情绪调节.md"
    private val docB = "practical/00-导读与使用分级.md"

    private class FixedConfig(
        private val config: RouteLlmConfig? =
            RouteLlmConfig("https://api.test.example/v1", "key", "route-model"),
    ) : RouteLlmConfigProvider {
        override fun current(): RouteLlmConfig? = config
    }

    /** mock 分类客户端：可编程响应（文本/挂起/抛错），并记录请求体供断言 */
    private class MockTransport(
        private val respond: suspend (String) -> String?,
    ) : RouteChatTransport {
        val bodies = mutableListOf<String>()

        override suspend fun post(config: RouteLlmConfig, bodyJson: String): String? {
            bodies.add(bodyJson)
            return respond(bodyJson)
        }
    }

    private fun testCatalog(): RoutingCatalog = RoutingCatalog.parse(
        """{"entries":[{"file":"$docA","title":"依恋理论与情绪调节","summary":"依恋类型与情绪调节"},
            {"file":"$docB","title":"导读与使用分级","summary":"知识库使用导读"}]}"""
    )!!

    private fun classifier(transport: RouteChatTransport): LlmRouteClassifier =
        LlmRouteClassifier(FixedConfig(), testCatalog(), transport)

    @Test
    fun `normal response returns whitelisted files in order`() = runTest {
        val transport = MockTransport {
            "好的，为你选择：\n```json\n[\"$docA\", \"$docB\"]\n```\n以上供参考"
        }
        val result = classifier(transport).route("他三个小时没回我消息，好焦虑")

        assertEquals(listOf(docA, docB), result)
        // 非流式小请求契约：stream=false + max_tokens=200 + query 进 user 消息 + 目录进 system
        val body = transport.bodies.single()
        assertTrue(body.contains("\"stream\":false"))
        assertTrue(body.contains("\"max_tokens\":200"))
        assertTrue(body.contains("他三个小时没回我消息，好焦虑"))
        assertTrue(body.contains(docA))
    }

    @Test
    fun `timeout returns null`() = runTest {
        // 虚拟时间：20s 挂起远超 10s 总超时，withTimeout 触发 → null（测试秒级完成）
        val transport = MockTransport {
            delay(20_000)
            null
        }
        assertNull(classifier(transport).route("怎么回"))
    }

    @Test
    fun `bad json returns null`() = runTest {
        val transport = MockTransport { "抱歉，我无法按要求输出。" }
        assertNull(classifier(transport).route("怎么回"))
    }

    @Test
    fun `non whitelisted filename fails to null`() = runTest {
        // 任一文件名不在目录白名单 → 整体判失败（宁走离线兜底，不注入未审计文档）
        val transport = MockTransport { "[\"$docA\", \"不在目录里的文档.md\"]" }
        assertNull(classifier(transport).route("怎么回"))
    }

    @Test
    fun `empty array is legal abstain`() = runTest {
        val transport = MockTransport { "[] " }
        assertEquals(emptyList<String>(), classifier(transport).route("怎么回"))
    }

    @Test
    fun `more than max routes are clamped to five`() = runTest {
        // 6 篇候选目录 + 模型返回 6 个文件名 → 截断为前 5（MAX_ROUTES 上限）
        val docs = (1..6).joinToString(",") { i ->
            """{"file":"knowledge/doc$i.md","title":"文档$i","summary":"摘要$i"}"""
        }
        val sixCatalog = RoutingCatalog.parse("""{"entries":[$docs]}""")!!
        val sixNames = (1..6).map { i -> "knowledge/doc$i.md" }
        val transport = MockTransport { Json.arr().also { arr -> sixNames.forEach(arr::put) }.toString() }
        val classifier = LlmRouteClassifier(FixedConfig(), sixCatalog, transport)

        assertEquals(sixNames.take(5), classifier.route("怎么回"))
    }

    @Test
    fun `wrapped object output extracts files array end to end`() = runTest {
        // 对象包裹容错（{"files":[...]}）：前后噪声 + 包裹对象，仍应取出其中的字符串数组
        val transport = MockTransport { """好的：{"files":["$docA","$docB"]} 以上""" }
        assertEquals(listOf(docA, docB), classifier(transport).route("怎么回"))
    }

    @Test
    fun `wrapped array selection prefers files key over earlier keys`() {
        // 双端 keys() 迭代序无契约（Android 保插入序 / 桌面 HashMap 无序）：旧实现按迭代序取首个
        // 字符串数组字段，同一输出在两端可能选出不同数组。固定 "files" 优先后必选 files 字段。
        val parsed = Json.obj("""{"aaa":["不在目录里的文档.md"],"files":["$docA"]}""")
        assertEquals(listOf(docA), parsed.selectWrappedStrings())
    }

    @Test
    fun `wrapped array selection falls back to lexicographically first string array`() {
        // 无 "files" 字段时按 key 字典序兜底（bbb > aaa → 必取 aaa），与平台迭代序无关
        val parsed = Json.obj("""{"bbb":["$docB"],"aaa":["$docA"]}""")
        assertEquals(listOf(docA), parsed.selectWrappedStrings())
    }

    @Test
    fun `wrapped array selection returns null without string array field`() {
        assertNull(selectWrappedStringArray(Json.obj("""{"note":"没有数组","count":2}""")))
    }

    /** JsonArray → List<String>（按元素原样 opt） */
    private fun JsonObject.selectWrappedStrings(): List<String> =
        selectWrappedStringArray(this)!!.let { arr -> (0 until arr.length()).map { arr.optString(it) } }

    @Test
    fun `unconfigured provider returns null`() = runTest {
        val classifier = LlmRouteClassifier(
            FixedConfig(config = null),
            testCatalog(),
            MockTransport { "[]" },
        )
        assertNull(classifier.route("怎么回"))
    }
}
