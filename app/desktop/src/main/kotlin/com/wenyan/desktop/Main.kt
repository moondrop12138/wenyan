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
const val DESKTOP_VERSION = "1.9.3"

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

    // L19 修复：探测（bind→close）与实际绑定之间存在 TOCTOU——双实例并发启动可能拿到
    // 同一端口，后绑定者 BindException 直接崩溃。改为「探测→尝试启动→失败重探」循环
    // （各限 50 次），端口竞争时自然落到下一空闲端口。
    var port = -1
    var bindAttempts = 0
    while (true) {
        val candidate = findFreePort(18923)
        try {
            println("[wenyan-desktop] starting on http://127.0.0.1:$candidate")
            embeddedServer(CIO, port = candidate, host = "127.0.0.1") {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; prettyPrint = false })
                }
                // L18 修复：路径参数/请求体解析异常（toLong/JSON）原一律 500——客户端错误应为 400；
                // 不存在的资源 id 仍由各路由自身 404。
                install(io.ktor.server.plugins.statuspages.StatusPages) {
                    exception<NumberFormatException> { call, _ ->
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    }
                    exception<org.json.JSONException> { call, _ ->
                        call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    }
                }
                routing {
                    apiRoutes(service, ChatEngine(service), token)
                    staticPage()
                }
            }.start(wait = false)
            port = candidate
            break
        } catch (e: java.net.BindException) {
            if (++bindAttempts >= 50) {
                println("[wenyan-desktop] no free port available, exiting")
                kotlin.system.exitProcess(1)
            }
        }
    }

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

/**
 * 从 basePort 起探测空闲端口。
 * L19：探测与实际绑定间仍有 TOCTOU，本函数仅作候选筛选；真正的兜底在调用方的
 * 「启动失败即重探」循环里（50 次全忙则报错退出，不再返回必败端口）。
 */
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
