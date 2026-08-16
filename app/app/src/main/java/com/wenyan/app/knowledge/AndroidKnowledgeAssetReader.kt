package com.wenyan.app.knowledge

import android.content.Context

/**
 * Android assets 实现（assets/knowledge/ 打包 40 份 md + routes.json）
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
}
