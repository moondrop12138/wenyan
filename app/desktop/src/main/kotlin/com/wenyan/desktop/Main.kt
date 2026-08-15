package com.wenyan.desktop

import com.wenyan.app.data.db.AppDatabase
import com.wenyan.app.llm.DesktopMetricsStore
import com.wenyan.app.llm.UsageMetrics
import com.wenyan.app.log.AppLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.awt.Desktop
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI

/**
 * 温言桌面版入口：Ktor Server (127.0.0.1) + 自动打开浏览器。
 *
 * - 端口从 18923 起探测空闲，bind 127.0.0.1 不对外
 * - 数据层：共享 Room entity/DAO（desktop BundledSQLiteDriver），首启注入预设提供商
 * - API Key：机器指纹派生 AES-256（不落盘），与 Android 端密文不互通（独立建档）
 */
const val DESKTOP_VERSION = "1.9.2"

fun main() {
    // 更新检查走系统代理（本机 Git 代理场景直连 api.github.com 会失败；用户走系统代理时 HttpURLConnection 自动生效）
    System.setProperty("java.net.useSystemProxies", "true")

    // O6: 启动时恢复用量指标并接管后续写盘
    UsageMetrics.attachStore(DesktopMetricsStore())

    // O4: 桌面日志 sink（stdout）
    AppLogger.sink = { level, line -> println("[Wenyan/$level] $line") }

    val service = WenyanService()
    // 首次启动注入预设提供商/模型（幂等）
    runBlocking { service.seedIfEmpty() }

    // H5: 每次启动生成随机 CSRF token（前端 bootstrap 获取后随请求带 X-Wenyan-Token 头）
    val token = java.util.UUID.randomUUID().toString().replace("-", "")

    val port = findFreePort(18923)
    println("[wenyan-desktop] starting on http://127.0.0.1:$port")

    embeddedServer(CIO, port = port, host = "127.0.0.1") {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; prettyPrint = false })
        }
        routing {
            apiRoutes(service, ChatEngine(service), token)
            staticPage()
        }
    }.start(wait = false)

    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        try {
            Desktop.getDesktop().browse(URI("http://127.0.0.1:$port"))
            println("[wenyan-desktop] browser opened")
        } catch (e: Exception) {
            println("[wenyan-desktop] failed to open browser: ${e.message}")
        }
    }

    Thread.currentThread().join()
}

/** 从 basePort 起探测空闲端口 */
private fun findFreePort(basePort: Int): Int {
    var port = basePort
    repeat(50) {
        try {
            ServerSocket().use { it.bind(InetSocketAddress("127.0.0.1", port)) }
            return port
        } catch (_: BindException) {
            port++
        }
    }
    return basePort
}
