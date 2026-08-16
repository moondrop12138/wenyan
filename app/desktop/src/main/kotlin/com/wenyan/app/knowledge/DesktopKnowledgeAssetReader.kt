package com.wenyan.app.knowledge

/**
 * 桌面版知识文档读取：从 classpath 资源读取（desktop 构建把 app assets/knowledge 拷入 resources/knowledge）。
 *
 * 与 AndroidKnowledgeAssetReader 同契约：read 返回 null 表示文档不存在（静默跳过）。
 */
class DesktopKnowledgeAssetReader : KnowledgeAssetReader {

    override fun read(relativePath: String): String? = try {
        val stream = javaClass.getResourceAsStream("/knowledge/$relativePath") ?: return null
        stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: Exception) {
        null
    }

    override fun readRoutesJson(): String? = read("routes-v2.json")

    override fun readQueryVariantsJson(): String? = read("route_query_variants.json")
}
