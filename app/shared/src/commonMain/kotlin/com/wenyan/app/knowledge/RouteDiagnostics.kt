package com.wenyan.app.knowledge

/**
 * 路由成本透明：进程内路由调用诊断（buildInjection 调用计数，本进程，未持久化）。
 *
 * - 写入调用链：KnowledgeEngine.buildInjection 两返回点 → record（runCatching 包裹，诊断永不污染主链路）
 * - 读取链：snapshot → RealSettingsRepository.routeDiagnostics → SettingsViewModel → UsageMetricsDialog
 * - 并发模式对齐 UsageMetrics：synchronized(lock) 全程加锁，record/snapshot/reset 三者全部同步；
 *   snapshot 返回 counts 深拷贝与 List 拷贝；lastAtMillis 仅存 Long，格式化放 UI 层
 * - 时钟：shared 仅 android+jvm 可用 System.currentTimeMillis（LlmClient 先例）
 * - 桌面端本轮只写不读（ChatEngine 经 shared record 计数，但 /api/metrics 与桌面 UI 不暴露），是有意为之
 */
object RouteDiagnostics {

    data class Snapshot(
        val counts: Map<String, Long>,
        val lastSource: String?,
        val lastRouted: List<String>,
        val lastInjected: List<String>,
        val lastAtMillis: Long?,
    )

    private val lock = Any()
    private val countsBySource = LinkedHashMap<String, Long>()
    private var lastSource: String? = null
    private var lastRouted: List<String> = emptyList()
    private var lastInjected: List<String> = emptyList()
    private var lastAtMillis: Long? = null

    /**
     * 记录一次 buildInjection 调用（route_source 分来源计数 + 最近一次候选/注入/时间）。
     * 内部 runCatching 永不抛：诊断失败不得影响聊天主链路。
     */
    fun record(source: String, routed: List<String>, injected: List<String>) {
        runCatching {
            synchronized(lock) {
                countsBySource[source] = (countsBySource[source] ?: 0L) + 1L
                lastSource = source
                lastRouted = routed.toList()
                lastInjected = injected.toList()
                lastAtMillis = System.currentTimeMillis()
            }
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            counts = countsBySource.toMap(),
            lastSource = lastSource,
            lastRouted = lastRouted.toList(),
            lastInjected = lastInjected.toList(),
            lastAtMillis = lastAtMillis,
        )
    }

    /** 供 wipeAll 复位（不改变任何持久化存储，本对象本身即非持久化） */
    fun reset() = synchronized(lock) {
        countsBySource.clear()
        lastSource = null
        lastRouted = emptyList()
        lastInjected = emptyList()
        lastAtMillis = null
    }
}
