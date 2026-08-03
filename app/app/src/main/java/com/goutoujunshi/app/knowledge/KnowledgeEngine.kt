package com.goutoujunshi.app.knowledge

import com.goutoujunshi.app.log.AppLogger

/**
 * 知识文档读取抽象（Android assets 实现由生产代码提供，测试注入内存实现）
 */
interface KnowledgeAssetReader {
    /** 读取 assets/knowledge 下文档全文 */
    fun read(relativePath: String): String?

    /** 读取 assets/knowledge/routes.json */
    fun readRoutesJson(): String?
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

    /**
     * 路由 + 注入：返回按 prompt-architecture §2.3 格式拼装的 system-知识文本
     * @return Pair(注入文本, 引用的文件名列表)
     */
    fun buildInjection(userInput: String): Pair<String, List<String>> {
        val docPaths = lazyIndex.route(userInput).take(maxDocs)
        if (docPaths.isEmpty()) {
            AppLogger.d("knowledge_route_empty")
            return "" to emptyList()
        }

        val keywords = extractKeywords(userInput)
        val injected = mutableListOf<String>()
        val refs = mutableListOf<String>()

        docPaths.forEachIndexed { index, path ->
            val markdown = reader.read(path) ?: return@forEachIndexed
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

    private fun extractKeywords(input: String): List<String> {
        // 取 2-4 字词作为命中关键词（去掉空白与标点后切分）
        val cleaned = input.replace(Regex("[\\s，。！？、,.!?；;：:（）()《》「」\"'“”]"), "")
        if (cleaned.isEmpty()) return emptyList()
        return buildList {
            // 4 字、3 字、2 字窗口
            addAll(nGrams(cleaned, 4))
            addAll(nGrams(cleaned, 3))
            addAll(nGrams(cleaned, 2))
        }.distinct().take(12)
    }

    private fun nGrams(text: String, size: Int): List<String> {
        if (text.length < size) return listOf(text)
        return buildList {
            for (i in 0..text.length - size) add(text.substring(i, i + size))
        }
    }
}
