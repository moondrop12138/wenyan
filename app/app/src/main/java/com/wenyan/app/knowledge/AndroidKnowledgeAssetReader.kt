package com.wenyan.app.knowledge

import android.content.Context

/**
 * Android assets 实现（assets/knowledge/ 打包 40 份 md + routes-v2.json）。
 * F28：routes.json 字节级副本已删除，唯一路由表为 routes-v2.json
 */
class AndroidKnowledgeAssetReader(context: Context) : KnowledgeAssetReader {

    private val appContext = context.applicationContext

    override fun read(relativePath: String): String? = try {
        appContext.assets.open("knowledge/$relativePath")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    } catch (e: Exception) {
        null
    }

    override fun readRoutesJson(): String? = read("routes-v2.json")

    override fun readQueryVariantsJson(): String? = read("route_query_variants.json")

    override fun readRoutingCatalogJson(): String? = read("routing-catalog.json")
}
