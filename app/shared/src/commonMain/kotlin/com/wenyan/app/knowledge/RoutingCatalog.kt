package com.wenyan.app.knowledge

import com.wenyan.app.json.Json

/**
 * LLM 路由目录（assets/knowledge/routing-catalog.json，结构 {"entries":[{file,title,summary}]}）
 *
 * 数据由 scripts/gen_routes.py 门禁保证：41 条、file 与 routes-v2.json 的 files 键一致、
 * summary ≤ 30 字符；运行时只做容错解析，不重复强校验。
 * 加载走 KnowledgeAssetReader.readRoutingCatalogJson 接缝，双端（Android assets / 桌面 jar 资源）可用。
 * 纯 JVM 可测。
 */
class RoutingCatalog private constructor(
    val entries: List<Entry>,
) {

    data class Entry(
        val file: String,
        val title: String,
        val summary: String,
    )

    /** 目录白名单：LLM 输出的文件名必须全部落在此集合内，否则整体判失败 */
    val allowedFiles: Set<String> = entries.map { it.file }.toSet()

    /** system 提示词目录段：每行 file|title|summary */
    fun toPromptLines(): String = entries.joinToString("\n") { "${it.file}|${it.title}|${it.summary}" }

    companion object {

        /**
         * 容错解析：缺文件/空串/坏 JSON/条目为空 → null（分类器视为不可用，走离线兜底）
         */
        fun parse(rawJson: String?): RoutingCatalog? {
            if (rawJson.isNullOrBlank()) return null
            return try {
                val root = Json.obj(rawJson)
                val arr = root.optJSONArray("entries") ?: return null
                val parsed = buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val file = obj.optString("file", "").trim()
                        if (file.isEmpty()) continue
                        add(
                            Entry(
                                file = file,
                                title = obj.optString("title", "").trim(),
                                summary = obj.optString("summary", "").trim(),
                            )
                        )
                    }
                }
                if (parsed.isEmpty()) null else RoutingCatalog(parsed)
            } catch (e: Exception) {
                null
            }
        }
    }
}
