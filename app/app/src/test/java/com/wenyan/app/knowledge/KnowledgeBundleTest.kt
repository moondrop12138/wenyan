package com.wenyan.app.knowledge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 知识库打包完整性测试（AC-17）
 * 直接从文件系统读取 assets 目录（JVM 单测可访问工程文件），
 * 校验 41 份文档齐全 + routes.json 覆盖 SKILL.md 全部主题（v1.9.0 practical 21 份含伦理转译）。
 */
class KnowledgeBundleTest {

    private val assetsDir: File = File(
        System.getProperty("user.dir"),
        "src/main/assets/knowledge"
    )

    private val routesFile: File = File(assetsDir, "routes-v2.json")

    private fun listMd(subDir: String): List<String> {
        val dir = File(assetsDir, subDir)
        return dir.listFiles { f -> f.extension == "md" }?.map { it.name }?.sorted() ?: emptyList()
    }

    @Test
    fun `exactly 20 knowledge docs`() {
        assertEquals(20, listMd("knowledge").size)
    }

    @Test
    fun `exactly 21 practical docs`() {
        assertEquals(21, listMd("practical").size)
    }

    @Test
    fun `routes json exists and valid`() {
        assertTrue(routesFile.exists())
        val root = JSONObject(routesFile.readText())
        assertEquals(1, root.getInt("version"))
        assertTrue(root.has("routes"))
        assertTrue(root.getJSONArray("routes").length() >= 15)
    }

    @Test
    fun `every route doc exists in assets`() {
        val root = JSONObject(routesFile.readText())
        val routes = root.getJSONArray("routes")
        for (i in 0 until routes.length()) {
            val docs = routes.getJSONObject(i).getJSONArray("docs")
            for (j in 0 until docs.length()) {
                val path = docs.getString(j)
                val file = File(assetsDir, path)
                assertTrue("route doc missing: $path", file.exists())
            }
        }
    }

    @Test
    fun `every md file is referenced by at least one route or files index`() {
        val root = JSONObject(routesFile.readText())
        val filesIndex = root.getJSONObject("files")
        val knowledge = listMd("knowledge")
        val practical = listMd("practical")
        val all = (knowledge.map { "knowledge/$it" } + practical.map { "practical/$it" })
        assertEquals(41, all.size)
        for (path in all) {
            assertTrue("files index missing: $path", filesIndex.has(path))
        }
    }

    @Test
    fun `crisis and safety doc routed`() {
        val root = JSONObject(routesFile.readText())
        val routes = root.getJSONArray("routes")
        val allDocs = buildList {
            for (i in 0 until routes.length()) {
                val docs = routes.getJSONObject(i).getJSONArray("docs")
                for (j in 0 until docs.length()) add(docs.getString(j))
            }
        }
        assertTrue(allDocs.any { it.contains("17-中国法律安全与危机转介") })
        assertTrue(allDocs.any { it.contains("实战话术编排器") })
    }
}
