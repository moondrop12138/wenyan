package com.wenyan.app.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识引擎端到端测试（路由 → 注入格式，AC-06/AC-17）
 */
class KnowledgeEngineTest {

    private class FakeReader : KnowledgeAssetReader {
        val docs = mutableMapOf<String, String>()
        var routesJson = "{}"
        var variantsJson: String? = null

        override fun read(relativePath: String): String? = docs[relativePath]

        override fun readRoutesJson(): String? = routesJson

        override fun readQueryVariantsJson(): String? = variantsJson
    }

    private fun makeEngine(reader: FakeReader): KnowledgeEngine = KnowledgeEngine(reader)

    // F66 精简：buildRoutesJson 原签名 Triple(keywords, docPath, docContent) 的第三个分量
    // 被解构后从未使用（各测试另行设置 reader.docs[docPath]），4 处调用点传入的文档内容
    // 全部被丢弃——改为 Pair(keywords, docPath)
    private fun buildRoutesJson(vararg routes: Pair<List<String>, String>): String {
        val files = org.json.JSONObject()
        val routesArr = org.json.JSONArray()
        for ((keywords, path) in routes) {
            files.put(path, org.json.JSONObject().put("title", path))
            routesArr.put(
                org.json.JSONObject()
                    .put("keywords", org.json.JSONArray(keywords))
                    .put("docs", org.json.JSONArray(listOf(path)))
            )
        }
        val root = org.json.JSONObject()
        root.put("version", 1)
        root.put("files", files)
        root.put("routes", routesArr)
        return root.toString()
    }

    @Test
    fun `buildInjection wraps doc in required format`() {
        val reader = FakeReader()
        val docPath = "practical/实战话术编排器：从一句回复到后续分支.md"
        reader.routesJson = buildRoutesJson(
            Pair(listOf("怎么回", "回复"), docPath)
        )
        reader.docs[docPath] = "# 实战话术编排器\n\n## 时机\n回复要快。"

        val engine = makeEngine(reader)
        val (injected, refs) = runBlocking { engine.buildInjection("这句怎么回") }

        assertTrue(injected.startsWith("【知识文档 #1】《实战话术编排器：从一句回复到后续分支.md》"))
        assertTrue(injected.contains("【知识文档结束 #1】"))
        assertEquals(listOf("实战话术编排器：从一句回复到后续分支.md"), refs)
    }

    @Test
    fun `no route match returns empty injection`() {
        val reader = FakeReader()
        reader.routesJson = buildRoutesJson(
            Pair(listOf("怎么回"), "practical/x.md")
        )
        reader.docs["practical/x.md"] = "# x\n\n## a\nb"
        val engine = makeEngine(reader)
        val (injected, refs) = runBlocking { engine.buildInjection("完全无关的话题内容") }
        assertEquals("", injected)
        assertTrue(refs.isEmpty())
    }

    @Test
    fun `crisis keyword routes to safety doc`() {
        val reader = FakeReader()
        val safetyPath = "knowledge/17-中国法律安全与危机转介.md"
        reader.routesJson = buildRoutesJson(
            Pair(listOf("家暴", "跟踪", "自杀"), safetyPath)
        )
        reader.docs[safetyPath] = "# 安全\n\n## 危机\n先安全。"
        val engine = makeEngine(reader)
        val (_, refs) = runBlocking { engine.buildInjection("我被他跟踪了") }
        assertEquals(listOf("17-中国法律安全与危机转介.md"), refs)
    }
    @Test
    fun `hybrid variant router covers doc not in routes`() {
        val reader = FakeReader()
        val routedPath = "practical/routed.md"
        val variantOnlyPath = "practical/提高气场：从内到外的力量感塑造指南.md"
        reader.routesJson = buildRoutesJson(
            Pair(listOf("怎么回"), routedPath)
        )
        reader.docs[routedPath] = "# 路由内\n\n## 内容\n回复。"
        reader.docs[variantOnlyPath] = "# 提高气场\n\n## 方法\n稳住自己。"
        reader.variantsJson = org.json.JSONObject()
            .put(variantOnlyPath, org.json.JSONArray(listOf("怎么提升气场", "气场弱怎么办")))
            .toString()

        val engine = makeEngine(reader)
        val (injected, refs) = runBlocking { engine.buildInjection("怎么提升气场") }
        assertEquals(listOf("提高气场：从内到外的力量感塑造指南.md"), refs)
        assertTrue(injected.startsWith("【知识文档 #1】《提高气场：从内到外的力量感塑造指南.md》"))
    }

    // ---- LLM 路由降级链（分类器每次调用注入，平台侧开关决定是否传 null） ----

    private fun makeClassifier(
        catalogDoc: String,
        respond: suspend (RouteLlmConfig, String) -> String?,
    ): LlmRouteClassifier = LlmRouteClassifier(
        configProvider = { RouteLlmConfig("https://api.test.example/v1", "key", "route-model") },
        catalog = RoutingCatalog.parse(
            """{"entries":[{"file":"$catalogDoc","title":"目录文档","summary":"摘要"}]}"""
        )!!,
        transport = respond,
    )

    @Test
    fun `llm classifier result takes priority over hybrid`() {
        val reader = FakeReader()
        val hybridPath = "practical/routed.md"
        val llmPath = "knowledge/03-依恋理论与情绪调节.md"
        reader.routesJson = buildRoutesJson(Pair(listOf("怎么回"), hybridPath))
        reader.docs[hybridPath] = "# 路由内\n\n## 内容\n回复。"
        reader.docs[llmPath] = "# 依恋\n\n## 类型\n焦虑型。"

        val engine = KnowledgeEngine(reader)
        // contains 明明命中 hybridPath，但 LLM 结果优先
        val (_, refs) = runBlocking {
            engine.buildInjection("这句怎么回", makeClassifier(llmPath) { _, _ -> "[\"$llmPath\"]" })
        }
        assertEquals(listOf("03-依恋理论与情绪调节.md"), refs)
    }

    @Test
    fun `llm empty result abstains without falling back to hybrid`() {
        val reader = FakeReader()
        val hybridPath = "practical/routed.md"
        reader.routesJson = buildRoutesJson(Pair(listOf("怎么回"), hybridPath))
        reader.docs[hybridPath] = "# 路由内\n\n## 内容\n回复。"

        val engine = KnowledgeEngine(reader)
        // 空集 = 合法弃权：不回退到 hybrid，不注入任何文档
        val (injected, refs) = runBlocking {
            engine.buildInjection("这句怎么回", makeClassifier(hybridPath) { _, _ -> "[]" })
        }
        assertEquals("", injected)
        assertTrue(refs.isEmpty())
    }

    @Test
    fun `llm classifier failure falls back to hybrid`() {
        val reader = FakeReader()
        val routedPath = "practical/routed.md"
        val variantOnlyPath = "practical/提高气场：从内到外的力量感塑造指南.md"
        reader.routesJson = buildRoutesJson(Pair(listOf("怎么回"), routedPath))
        reader.docs[routedPath] = "# 路由内\n\n## 内容\n回复。"
        reader.docs[variantOnlyPath] = "# 提高气场\n\n## 方法\n稳住自己。"
        reader.variantsJson = org.json.JSONObject()
            .put(variantOnlyPath, org.json.JSONArray(listOf("怎么提升气场", "气场弱怎么办")))
            .toString()

        // 分类失败（transport 返回坏输出 → route 返回 null）→ 走现有 HybridVariantRouter
        val engine = KnowledgeEngine(reader)
        val (_, refs) = runBlocking {
            engine.buildInjection("怎么提升气场", makeClassifier(variantOnlyPath) { _, _ -> "模型胡言乱语" })
        }
        assertEquals(listOf("提高气场：从内到外的力量感塑造指南.md"), refs)
    }
}
