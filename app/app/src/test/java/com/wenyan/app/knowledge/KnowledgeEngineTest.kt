package com.wenyan.app.knowledge

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

    private fun buildRoutesJson(vararg routes: Triple<List<String>, String, String>): String {
        // Triple(keywords, docPath, docContent)
        val files = org.json.JSONObject()
        val routesArr = org.json.JSONArray()
        for ((keywords, path, content) in routes) {
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
            Triple(listOf("怎么回", "回复"), docPath, "# 实战话术编排器\n\n## 时机\n回复要快。")
        )
        reader.docs[docPath] = "# 实战话术编排器\n\n## 时机\n回复要快。"

        val engine = makeEngine(reader)
        val (injected, refs) = engine.buildInjection("这句怎么回")

        assertTrue(injected.startsWith("【知识文档 #1】《实战话术编排器：从一句回复到后续分支.md》"))
        assertTrue(injected.contains("【知识文档结束 #1】"))
        assertEquals(listOf("实战话术编排器：从一句回复到后续分支.md"), refs)
    }

    @Test
    fun `no route match returns empty injection`() {
        val reader = FakeReader()
        reader.routesJson = buildRoutesJson(
            Triple(listOf("怎么回"), "practical/x.md", "# x\n\n## a\nb")
        )
        reader.docs["practical/x.md"] = "# x\n\n## a\nb"
        val engine = makeEngine(reader)
        val (injected, refs) = engine.buildInjection("完全无关的话题内容")
        assertEquals("", injected)
        assertTrue(refs.isEmpty())
    }

    @Test
    fun `crisis keyword routes to safety doc`() {
        val reader = FakeReader()
        val safetyPath = "knowledge/17-中国法律安全与危机转介.md"
        reader.routesJson = buildRoutesJson(
            Triple(listOf("家暴", "跟踪", "自杀"), safetyPath, "# 安全\n\n## 危机\n先安全。")
        )
        reader.docs[safetyPath] = "# 安全\n\n## 危机\n先安全。"
        val engine = makeEngine(reader)
        val (_, refs) = engine.buildInjection("我被他跟踪了")
        assertEquals(listOf("17-中国法律安全与危机转介.md"), refs)
    }
    @Test
    fun `hybrid variant router covers doc not in routes`() {
        val reader = FakeReader()
        val routedPath = "practical/routed.md"
        val variantOnlyPath = "practical/提高气场：从内到外的力量感塑造指南.md"
        reader.routesJson = buildRoutesJson(
            Triple(listOf("怎么回"), routedPath, "# 路由内\n\n## 内容\n回复。")
        )
        reader.docs[routedPath] = "# 路由内\n\n## 内容\n回复。"
        reader.docs[variantOnlyPath] = "# 提高气场\n\n## 方法\n稳住自己。"
        reader.variantsJson = org.json.JSONObject()
            .put(variantOnlyPath, org.json.JSONArray(listOf("怎么提升气场", "气场弱怎么办")))
            .toString()

        val engine = makeEngine(reader)
        val (injected, refs) = engine.buildInjection("怎么提升气场")
        assertEquals(listOf("提高气场：从内到外的力量感塑造指南.md"), refs)
        assertTrue(injected.startsWith("【知识文档 #1】《提高气场：从内到外的力量感塑造指南.md》"))
    }
}
