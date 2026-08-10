package com.wenyan.desktop

import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * 静态网页（阶段 3）：classpath:/static 下的 index.html/app.js/styles.css。
 * / 重定向到 /static/index.html，前端 hash 路由（#/chat #/settings…）由 JS 接管。
 */
fun Route.staticPage() {
    get("/") { call.respondRedirect("/static/index.html", permanent = false) }
    staticResources("/static", "static")
}
