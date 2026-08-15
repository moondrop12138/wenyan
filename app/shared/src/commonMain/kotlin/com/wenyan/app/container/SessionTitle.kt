package com.wenyan.app.container

/**
 * 会话标题纯函数（v1.2.1，JVM 可单测）：
 * 抽屉标题三级回退 + 主模型拟题的素材提取 / prompt 构建 / 输出清洗。
 */
object SessionTitle {

    /**
     * 抽屉标题三级回退：
     * DB 存储标题（非空）> 首条 USER 消息前 30 字 > "新会话"。
     * 旧会话迁移后 title 为空串，自动落到第二级，行为与 v1.2.0 一致。
     */
    fun resolveSessionTitle(sessionTitle: String?, firstUserText: String?): String {
        val stored = sessionTitle?.trim().orEmpty()
        if (stored.isNotBlank()) return stored.take(30)
        return firstUserText
            ?.replace(Regex("\\s+"), " ")
            ?.take(30)
            ?.takeIf { it.isNotBlank() }
            ?: "新会话"
    }

    /**
     * 素材提取：首句用户输入（≤30 字）+ 模型回复（≤40 字），折叠空白。
     * 结构化回复（五步法 JSON）时由调用方先取 reply 字段再传入。
     */
    fun buildTitleMaterial(userText: String, replyText: String): Pair<String, String> {
        val userLine = userText.replace(Regex("\\s+"), " ").trim().take(30)
        val replyLine = replyText.replace(Regex("\\s+"), " ").trim().take(40)
        return userLine to replyLine
    }

    /** 标题生成 prompt：只输出标题本身，≤12 字，无标点/空格/emoji，不解释 */
    fun buildTitlePrompt(userLine: String, replyLine: String): String = buildString {
        append("给这段对话拟一个简洁的标题。要求：只输出标题本身，不超过12个字，不含标点、空格、emoji，不解释。\n")
        append("用户说：").append(userLine).append("\n")
        if (replyLine.isNotBlank()) append("温言回：").append(replyLine).append("\n")
        append("标题：")
    }

    /** 清洗模型输出：去"标题："前缀、标点、emoji、空白，截断 12 字；空/无效返回空串 */
    fun sanitizeTitle(raw: String): String =
        raw.trim()
            .removePrefix("标题")
            .removePrefix("：")
            .removePrefix(":")
            .trim()
            .replace(Regex("\\p{P}"), "")            // 标点
            .replace(Regex("[\\p{So}\\p{Cn}\\p{Sk}]"), "") // 符号/emoji
            .replace(Regex("\\s+"), "")
            .take(12)
            .trim()
}
