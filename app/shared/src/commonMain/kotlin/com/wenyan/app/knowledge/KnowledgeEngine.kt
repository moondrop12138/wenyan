package com.wenyan.app.knowledge

import com.wenyan.app.json.Json
import com.wenyan.app.log.AppLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 知识文档读取抽象（Android assets 实现由生产代码提供，测试注入内存实现）
 */
interface KnowledgeAssetReader {
    /** 读取 assets/knowledge 下文档全文 */
    fun read(relativePath: String): String?

    /** 读取 assets/knowledge/routes.json */
    fun readRoutesJson(): String?

    /** 读取 assets/knowledge/route_query_variants.json；未提供时返回 null（生产使用混合路由补漏） */
    fun readQueryVariantsJson(): String? = null
}

/**
 * 知识引擎（AC-06 知识路由 + 注入）
 *
 * 流程：输入 → 路由命中 1-3 份文档 → 每份分块截断（≤4K token）→ 输出注入文本。
 * 依赖 AssetReader 抽象，JVM 单测可注入内存实现。
 */
class KnowledgeEngine(
    private val reader: KnowledgeAssetReader,
    private val index: KnowledgeIndex? = null,
    private val maxDocs: Int = 3,
) {

    private val lazyIndex: KnowledgeIndex by lazy {
        index ?: KnowledgeIndex(reader.readRoutesJson() ?: "{}")
    }

    private val lazyHybrid: HybridVariantRouter? by lazy {
        val variants = parseQueryVariants(reader.readQueryVariantsJson())
        if (variants.isEmpty()) null else HybridVariantRouter(lazyIndex, variants)
    }

    // M3/M11: 进程内文档 LRU 缓存（路径 → 内容）。access-order 模式下连 get 都会改链表结构——
    // Android 有 _streamingState 单流守卫基本串行；桌面每 HTTP 请求独立 IO 协程，
    // 并发访问存在真实数据竞争（条目丢失/脏读）。M11 修复：所有访问包进同一把 Mutex。
    private val docCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 64
    }

    private val cacheMutex = Mutex()

    private suspend fun readCached(path: String): String? = cacheMutex.withLock {
        docCache[path]?.let { return@withLock it }
        val content = reader.read(path) ?: return@withLock null
        docCache[path] = content
        content
    }

    /**
     * 路由 + 注入：返回按 prompt-architecture §2.3 格式拼装的 system-知识文本
     * @return Pair(注入文本, 引用的文件名列表)
     */
    suspend fun buildInjection(userInput: String): Pair<String, List<String>> {
        val docPaths = route(userInput).take(maxDocs)
        if (docPaths.isEmpty()) {
            AppLogger.d("knowledge_route_empty")
            return "" to emptyList()
        }

        val keywords = extractKeywords(userInput)
        val injected = mutableListOf<String>()
        val refs = mutableListOf<String>()

        docPaths.forEachIndexed { index, path ->
            val markdown = readCached(path) ?: return@forEachIndexed
            val truncated = KnowledgeChunker.truncate(markdown, keywords)
            if (truncated.isNotBlank()) {
                val fileName = path.substringAfterLast('/')
                injected.add(
                    buildString {
                        append("【知识文档 #").append(index + 1).append("】《")
                        append(fileName).append("》\n")
                        append(truncated)
                        append("\n【知识文档结束 #").append(index + 1).append("】")
                    }
                )
                refs.add(fileName)
            }
        }
        // 只记录命中文档名（元数据），不记录用户输入原文
        AppLogger.i("knowledge_route_hit", "docs" to refs.joinToString(","))
        return injected.joinToString("\n\n") to refs
    }

    private fun route(input: String): List<String> = lazyHybrid?.route(input) ?: lazyIndex.route(input)

    private fun parseQueryVariants(rawJson: String?): Map<String, List<String>> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        return try {
            val root = Json.obj(rawJson)
            root.keys().associateWith { key ->
                val arr = root.optJSONArray(key) ?: return@associateWith emptyList()
                buildList { for (i in 0 until arr.length()) add(arr.getString(i)) }
            }.filterValues { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun extractKeywords(input: String): List<String> {
        // 取 2-4 字词作为命中关键词（去掉空白与标点后切分）
        val cleaned = input.replace(Regex("[\\s，。！？、,.!?；;：:（）()《》「」\"'“”]"), "")
        if (cleaned.isEmpty()) return emptyList()
        // M3: 仅对前 N 字符生成 n-gram，避免长输入 O(n²) 子串爆炸
        val head = cleaned.take(KEYWORD_SCAN_CHAR_LIMIT)
        // M12 修复：原「4-gram 全量 → 3-gram → 2-gram」拼接后 distinct().take(12)——
        // 输入 ≥15 字时前 12 个全是 4 字窗口，2/3 字词永远选不中且只覆盖前 15 字符，
        // 含关键词的知识块拿不到命中分。改为按长度轮转交错（各长度最多取配额内位置），
        // 保证三种粒度都能入选且覆盖更长的输入前缀。
        val g4 = nGrams(head, 4).distinct()
        val g3 = nGrams(head, 3).distinct()
        val g2 = nGrams(head, 2).distinct()
        return buildList {
            val maxLen = maxOf(g4.size, g3.size, g2.size)
            for (i in 0 until maxLen) {
                if (i < g4.size) add(g4[i])
                if (i < g3.size) add(g3[i])
                if (i < g2.size) add(g2[i])
            }
        }.take(KEYWORD_LIMIT)
    }

    private fun nGrams(text: String, size: Int): List<String> {
        if (text.length < size) return listOf(text)
        return buildList {
            for (i in 0..text.length - size) add(text.substring(i, i + size))
        }
    }

    private companion object {
        /** M3: n-gram 只对输入前 N 字符生成，限制关键词扫描窗口 */
        const val KEYWORD_SCAN_CHAR_LIMIT = 200

        /** M12: 关键词上限（与原 take(12) 一致） */
        const val KEYWORD_LIMIT = 12
    }
}
