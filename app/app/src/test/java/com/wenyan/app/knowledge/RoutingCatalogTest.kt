package com.wenyan.app.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * routing-catalog.json 容错解析（KnowledgeAssetReader.readRoutingCatalogJson 接缝的数据侧）
 */
class RoutingCatalogTest {

    @Test
    fun `parse extracts entries and builds whitelist`() {
        val catalog = RoutingCatalog.parse(
            """{"entries":[
                {"file":"knowledge/01-证据分级与内容边界.md","title":"证据分级与内容边界","summary":"判断恋爱建议可信度"},
                {"file":"practical/00-导读与使用分级.md","title":"导读与使用分级","summary":"使用导读"}
            ]}"""
        )
        assertNotNull(catalog)
        assertEquals(2, catalog!!.entries.size)
        assertEquals(
            setOf("knowledge/01-证据分级与内容边界.md", "practical/00-导读与使用分级.md"),
            catalog.allowedFiles,
        )
        // 目录段格式：file|title|summary，供 system 提示词拼接
        val lines = catalog.toPromptLines()
        assertTrue(lines.contains("knowledge/01-证据分级与内容边界.md|证据分级与内容边界|判断恋爱建议可信度"))
    }

    @Test
    fun `parse returns null on missing blank or broken json`() {
        assertNull(RoutingCatalog.parse(null))
        assertNull(RoutingCatalog.parse(""))
        assertNull(RoutingCatalog.parse("   "))
        assertNull(RoutingCatalog.parse("不是 JSON"))
        assertNull(RoutingCatalog.parse("""{"entries":[]}"""))
        // 全部条目 file 为空 → 视为空目录
        assertNull(RoutingCatalog.parse("""{"entries":[{"file":"","title":"t","summary":"s"}]}"""))
    }
}
