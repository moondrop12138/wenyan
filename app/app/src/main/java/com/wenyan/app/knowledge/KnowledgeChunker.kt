package com.wenyan.app.knowledge

/**
 * 知识文档分块截断（llm-contract §7 / prompt-architecture §2.3）
 *
 * 策略：
 * - 按 `## ` 二级标题分块（跳过 # 大标题与前言）
 * - 关键词命中优先：包含用户问题关键词的块优先入选
 * - 单份预算 ≤ 4K token（中文约 1 字符 ≈ 0.6 token，按字符数近似）
 * 纯 JVM 可测。
 */
object KnowledgeChunker {

    const val MAX_CHARS_PER_DOC = 6000 // ≈ 3.6K-4K token（中文保守）

    /** M4: 超预算块截断标记 */
    const val TRUNCATED_MARK = "…[已截断]"

    data class Chunk(
        val heading: String,
        val body: String,
        val score: Int,
    )

    /**
     * 将文档按 ## 分块，返回块列表（无 Android 依赖）
     */
    fun split(markdown: String): List<Chunk> {
        val lines = markdown.split("\n")
        val chunks = mutableListOf<Chunk>()
        var currentHeading = "前言"
        val currentBody = StringBuilder()

        fun flush() {
            val body = currentBody.toString().trim()
            if (body.isNotEmpty()) {
                chunks.add(Chunk(currentHeading, body, score = 0))
            }
            currentBody.setLength(0)
        }

        for (line in lines) {
            if (line.startsWith("## ")) {
                flush()
                currentHeading = line.removePrefix("## ").trim()
            } else if (line.startsWith("# ")) {
                // 大标题跳过（不入正文）
            } else {
                currentBody.append(line).append('\n')
            }
        }
        flush()
        return chunks
    }

    /**
     * 按关键词命中 + 预算截断：优先含关键词的块，直到字符预算耗尽。
     * @param keywords 用户问题中的关键词（用于命中选择）
     * @param maxChars 单份文档字符预算
     */
    fun selectChunks(
        chunks: List<Chunk>,
        keywords: List<String>,
        maxChars: Int = MAX_CHARS_PER_DOC,
    ): String {
        if (chunks.isEmpty()) return ""

        val scored = chunks.map { chunk ->
            val hit = keywords.count { kw -> chunk.body.contains(kw) || chunk.heading.contains(kw) }
            chunk.copy(score = hit)
        }

        // 命中块优先（按命中数降序），未命中块按原顺序兜底
        val selected = mutableListOf<Chunk>()
        var used = 0
        val byHit = scored.filter { it.score > 0 }.sortedByDescending { it.score }
        val rest = scored.filter { it.score == 0 }

        for (chunk in (byHit + rest)) {
            if (used >= maxChars) break
            val size = chunk.heading.length + chunk.body.length + 2
            if (used + size > maxChars) {
                if (selected.isEmpty()) {
                    // M4: 首个块就超预算时也截断到预算 + 省略标记（防单份文档 token 预算失效）
                    val budget = (maxChars - chunk.heading.length - 4).coerceAtLeast(0)
                    selected.add(chunk.copy(body = chunk.body.take(budget) + TRUNCATED_MARK))
                }
                break
            }
            selected.add(chunk)
            used += size
        }

        return buildString {
            selected.forEachIndexed { index, chunk ->
                if (index > 0) append("\n\n")
                append("## ").append(chunk.heading).append('\n').append(chunk.body)
            }
        }
    }

    /**
     * 全文分块截断：先 split 再 selectChunks（便捷入口）
     */
    fun truncate(markdown: String, keywords: List<String>): String =
        selectChunks(split(markdown), keywords)
}
