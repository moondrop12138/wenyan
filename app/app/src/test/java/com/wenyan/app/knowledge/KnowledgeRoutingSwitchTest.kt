package com.wenyan.app.knowledge

import com.wenyan.app.log.AppLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * knowledgeRouting 开关（"llm" 默认 | "offline" 显式关闭）与平台侧装配接缝：
 * - 默认值归一：只有显式 "offline" 才关；null/空/非法值（含未设置 = 默认）一律 "llm"（双端存储层共用 KnowledgeRouting.normalize）
 * - offline：装配返回 null 分类器且**不解析主模型配置**（零解密），路由请求零调用（mock transport 计数验证）
 * - llm：装配出分类器，一次 buildInjection 恰好一次路由请求；未配置主模型/缺目录时不发请求
 * - 埋点：knowledge_route_hit/empty 沿用原事件名，追加 route_source=llm/fallback/offline
 */
class KnowledgeRoutingSwitchTest {

    private val docA = "knowledge/03-依恋理论与情绪调节.md"
    private val offlineDoc = "practical/实战话术编排器：从一句回复到后续分支.md"

    /** mock 路由客户端：计数每次调用（offline 零调用断言的核心），并可编程响应 */
    private class CountingTransport(
        private val respond: suspend (RouteLlmConfig, String) -> String?,
    ) : RouteChatTransport {
        var calls = 0
            private set

        override suspend fun post(config: RouteLlmConfig, bodyJson: String): String? {
            calls++
            return respond(config, bodyJson)
        }
    }

    private class FakeReader : KnowledgeAssetReader {
        val docs = mutableMapOf<String, String>()
        var routesJson = "{}"

        override fun read(relativePath: String): String? = docs[relativePath]

        override fun readRoutesJson(): String? = routesJson
    }

    private fun routesJsonFor(vararg keywordToDoc: Pair<String, String>): String {
        val files = org.json.JSONObject()
        val routesArr = org.json.JSONArray()
        for ((keyword, path) in keywordToDoc) {
            files.put(path, org.json.JSONObject().put("title", path))
            routesArr.put(
                org.json.JSONObject()
                    .put("keywords", org.json.JSONArray(listOf(keyword)))
                    .put("docs", org.json.JSONArray(listOf(path)))
            )
        }
        return org.json.JSONObject()
            .put("version", 1)
            .put("files", files)
            .put("routes", routesArr)
            .toString()
    }

    private fun testCatalog(): RoutingCatalog = RoutingCatalog.parse(
        """{"entries":[{"file":"$docA","title":"依恋理论与情绪调节","summary":"依恋类型与情绪调节"}]}"""
    )!!

    private fun fixedConfig() = RouteLlmConfig("https://api.test.example/v1", "key", "route-model")

    /** AppLogger.sink 换入捕获 sink（全局单例，测后恢复），返回期间产生的全部行 */
    private fun captureLogs(block: () -> Unit): List<String> {
        val lines = mutableListOf<String>()
        val original = AppLogger.sink
        AppLogger.sink = { _, line -> lines.add(line) }
        try {
            block()
        } finally {
            AppLogger.sink = original
        }
        return lines
    }

    // ===== 开关默认值（双端存储层读取统一经 KnowledgeRouting.normalize 归一） =====

    @Test
    fun `knowledge routing default is llm`() {
        assertEquals(KnowledgeRouting.LLM, KnowledgeRouting.normalize(null))        // 未设置 = 默认 llm
        assertEquals(KnowledgeRouting.LLM, KnowledgeRouting.normalize(""))
        assertEquals(KnowledgeRouting.LLM, KnowledgeRouting.normalize("llm"))
        assertEquals(KnowledgeRouting.LLM, KnowledgeRouting.normalize("bogus"))     // 非显式 offline 视同默认开
        assertEquals(KnowledgeRouting.OFFLINE, KnowledgeRouting.normalize("offline")) // 显式关闭才关
    }

    // ===== llm（显式开启）：装配出分类器，路由请求恰好一次 =====

    @Test
    fun `llm routing assembles classifier and issues exactly one route request`() {
        val reader = FakeReader()
        reader.routesJson = routesJsonFor("怎么回" to offlineDoc)
        reader.docs[docA] = "# 依恋\n\n## 类型\n焦虑型。"
        val transport = CountingTransport { _, _ -> "[\"$docA\"]" }

        val logs = captureLogs {
            runBlocking {
                val classifier = currentRouteClassifier(
                    catalog = testCatalog(),
                    isLlmRouting = { true },
                    resolveMainModelConfig = { fixedConfig() },
                    transport = transport,
                )
                assertNotNull(classifier)
                val (injected, refs) = KnowledgeEngine(reader).buildInjection("这句怎么回", classifier)
                assertEquals(listOf("03-依恋理论与情绪调节.md"), refs)
                assertTrue(injected.contains("【知识文档 #1】《03-依恋理论与情绪调节.md》"))
            }
        }

        assertEquals(1, transport.calls)
        // 埋点：命中走 knowledge_route_hit，route_source=llm，docs=命中文档名
        assertTrue(logs.any { it.startsWith("knowledge_route_hit ") })
        assertTrue(logs.any { it.contains("route_source=llm") && it.contains("docs=03-依恋理论与情绪调节.md") })
    }

    // ===== offline：零解密、零路由请求，仍走本地离线路由 =====

    @Test
    fun `offline routing makes zero route requests`() {
        val reader = FakeReader()
        reader.routesJson = routesJsonFor("怎么回" to offlineDoc)
        reader.docs[offlineDoc] = "# 话术\n\n## 时机\n回复要快。"
        // 一旦发起路由请求/解析主模型配置（含 Keystore 解密）即测试失败
        val transport = CountingTransport { _, _ ->
            fail("offline 模式不得发起路由请求")
            null
        }

        val logs = captureLogs {
            runBlocking {
                val classifier = currentRouteClassifier(
                    catalog = testCatalog(),
                    isLlmRouting = { false },
                    resolveMainModelConfig = { fail("offline 模式不得解析主模型配置"); null },
                    transport = transport,
                )
                assertNull(classifier)
                // 平台侧拿不到分类器 → 传 null，引擎走离线路由（本地关键词路由照常命中）
                val (injected, refs) = KnowledgeEngine(reader).buildInjection("这句怎么回", classifier)
                assertEquals(listOf("实战话术编排器：从一句回复到后续分支.md"), refs)
                assertTrue(injected.startsWith("【知识文档 #1】《实战话术编排器：从一句回复到后续分支.md》"))
            }
        }

        assertEquals(0, transport.calls)
        // 埋点：route_source=offline
        assertTrue(logs.any { it.contains("route_source=offline") })
    }

    // ===== llm 档但主模型未配置：不发请求，走离线兜底 =====

    @Test
    fun `llm routing without model config never issues requests`() {
        val reader = FakeReader()
        reader.routesJson = routesJsonFor("怎么回" to offlineDoc)
        reader.docs[offlineDoc] = "# 话术\n\n## 时机\n回复要快。"
        val transport = CountingTransport { _, _ -> "[\"$docA\"]" }

        val logs = captureLogs {
            runBlocking {
                val classifier = currentRouteClassifier(
                    catalog = testCatalog(),
                    isLlmRouting = { true },
                    resolveMainModelConfig = { null },   // 未配置主模型/Key
                    transport = transport,
                )
                assertNull(classifier)
                KnowledgeEngine(reader).buildInjection("这句怎么回", classifier)
            }
        }

        assertEquals(0, transport.calls)
        assertTrue(logs.any { it.contains("route_source=offline") })
    }

    // ===== 目录缺失：分类器不可用且不解析配置 =====

    @Test
    fun `missing catalog makes classifier unavailable without resolving config`() {
        runBlocking {
            val classifier = currentRouteClassifier(
                catalog = null,
                isLlmRouting = { true },
                resolveMainModelConfig = { fail("目录缺失时不得解析主模型配置"); null },
            )
            assertNull(classifier)
        }
    }

    // ===== fallback：分类器失败（坏输出）→ 回退离线路由，route_source=fallback =====

    @Test
    fun `classifier failure falls back to offline routing with fallback source`() {
        val reader = FakeReader()
        reader.routesJson = routesJsonFor("怎么回" to offlineDoc)
        reader.docs[offlineDoc] = "# 话术\n\n## 时机\n回复要快。"
        val transport = CountingTransport { _, _ -> "模型胡言乱语" }   // 无 JSON → 解析失败 → null

        val logs = captureLogs {
            runBlocking {
                val classifier = currentRouteClassifier(
                    catalog = testCatalog(),
                    isLlmRouting = { true },
                    resolveMainModelConfig = { fixedConfig() },
                    transport = transport,
                )
                val (injected, refs) = KnowledgeEngine(reader).buildInjection("这句怎么回", classifier)
                assertEquals(listOf("实战话术编排器：从一句回复到后续分支.md"), refs)
                assertTrue(injected.contains("实战话术编排器"))
            }
        }

        assertEquals(1, transport.calls)   // 请求已发起但失败
        assertTrue(logs.any { it.contains("route_source=fallback") })
    }

    // ===== 空路由（knowledge_route_empty）同样带 route_source（offline 档） =====

    @Test
    fun `empty route logs route_source on knowledge_route_empty`() {
        val reader = FakeReader()   // 无路由表 → 离线路由空集
        val logs = captureLogs {
            runBlocking { KnowledgeEngine(reader).buildInjection("完全无关的话题内容", null) }
        }
        assertTrue(logs.any { it.startsWith("knowledge_route_empty ") && it.contains("route_source=offline") })
    }
}
