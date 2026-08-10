package com.wenyan.desktop

import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.ModelEntity
import com.wenyan.app.data.db.ProviderEntity
import com.wenyan.app.data.db.SessionEntity
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.data.image.DesktopImageCompressor
import com.wenyan.app.llm.MAX_IMAGES_PER_REQUEST
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ktor REST 路由：把共享 Room entity 序列化为 JSON 供网页前端消费。
 * 手写 org.json（与 ChatRequestBuilder 一致的风格，不引 kotlinx-serialization 注解实体）。
 */

// ===== entity → JSON 序列化 =====

fun ProviderEntity.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("baseUrl", baseUrl)
    .put("hasApiKey", apiKeyEncrypted != null)
    .put("isPreset", isPreset)
    .put("connectionStatus", connectionStatus)
    .put("sortOrder", sortOrder)

fun ModelEntity.toJson() = JSONObject()
    .put("id", id)
    .put("providerId", providerId)
    .put("name", name)
    .put("supportsVision", supportsVision)
    .put("isDefault", isDefault)
    .put("showInSheet", showInSheet)
    .put("sortOrder", sortOrder)

fun TargetEntity.toJson() = JSONObject()
    .put("id", id)
    .put("codeName", codeName)
    .put("mbti", mbti ?: JSONObject.NULL)
    .put("score", score ?: JSONObject.NULL)
    .put("relationStatus", relationStatus ?: JSONObject.NULL)
    .put("timeline", timeline)
    .put("note", note)
    .put("createdAt", createdAt)

fun MemoryFactEntity.toJson() = JSONObject()
    .put("id", id)
    .put("targetId", targetId)
    .put("text", text)
    .put("createdAt", createdAt)

fun SessionEntity.toJson() = JSONObject()
    .put("id", id)
    .put("createdAt", createdAt)
    .put("title", title)
    .put("targetId", targetId ?: JSONObject.NULL)

fun MessageEntity.toJson() = JSONObject()
    .put("id", id)
    .put("sessionId", sessionId)
    .put("role", role)
    .put("type", type)
    .put("content", content)
    .put("createdAt", createdAt)

private fun <T> JSONArray.of(items: List<T>, mapper: (T) -> JSONObject): JSONArray {
    items.forEach { put(mapper(it)) }
    return this
}

/** API 路由（/api 前缀） */
fun Route.apiRoutes(service: WenyanService, chatEngine: ChatEngine) {

    get("/api/health") {
        call.respondText("""{"ok":true,"version":"0.1.0-desktop"}""", ContentType.Application.Json)
    }

    // ===== 提供商 / 模型 =====

    get("/api/providers") {
        call.respondJson(JSONArray().of(service.listProviders()) { it.toJson() })
    }

    post("/api/providers") {
        val body = JSONObject(call.receiveText())
        val id = service.addProvider(
            name = body.getString("name"),
            baseUrl = body.getString("baseUrl"),
            apiKey = body.optString("apiKey", null),
            isPreset = body.optBoolean("isPreset", false),
        )
        call.respondJson(JSONObject().put("id", id))
    }

    put("/api/providers/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val body = JSONObject(call.receiveText())
        service.updateProvider(
            id = id,
            name = body.getString("name"),
            baseUrl = body.getString("baseUrl"),
            apiKey = if (body.has("apiKey")) body.optString("apiKey", null) else null,
        )
        call.respondJson(JSONObject().put("ok", true))
    }

    delete("/api/providers/{id}") {
        service.deleteProvider(call.parameters["id"]!!.toLong())
        call.respondJson(JSONObject().put("ok", true))
    }

    get("/api/providers/{id}/models") {
        call.respondJson(JSONArray().of(service.listModels(call.parameters["id"]!!.toLong())) { it.toJson() })
    }

    get("/api/models") {
        call.respondJson(JSONArray().of(service.listAllModels()) { it.toJson() })
    }

    post("/api/models") {
        val body = JSONObject(call.receiveText())
        val id = service.addModel(
            providerId = body.getLong("providerId"),
            name = body.getString("name"),
            supportsVision = body.optBoolean("supportsVision", false),
        )
        call.respondJson(JSONObject().put("id", id))
    }

    delete("/api/models/{id}") {
        service.deleteModel(call.parameters["id"]!!.toLong())
        call.respondJson(JSONObject().put("ok", true))
    }

    // ===== 档案（target）=====

    get("/api/targets") {
        call.respondJson(JSONArray().of(service.listTargets()) { it.toJson() })
    }

    post("/api/targets") {
        val body = JSONObject(call.receiveText())
        val id = service.createTarget(body.getString("codeName"))
        call.respondJson(JSONObject().put("id", id))
    }

    put("/api/targets/{id}") {
        val id = call.parameters["id"]!!.toLong()
        val current = service.getTarget(id)
        if (current == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "target not found"))
            return@put
        }
        val body = JSONObject(call.receiveText())
        service.updateTarget(
            current.copy(
                codeName = body.optString("codeName", current.codeName),
                mbti = if (body.has("mbti")) body.optString("mbti", null) else current.mbti,
                score = if (body.has("score")) body.optInt("score") else current.score,
                relationStatus = if (body.has("relationStatus")) body.optString("relationStatus", null) else current.relationStatus,
                timeline = body.optString("timeline", current.timeline),
                note = body.optString("note", current.note),
            )
        )
        call.respondJson(JSONObject().put("ok", true))
    }

    delete("/api/targets/{id}") {
        service.deleteTarget(call.parameters["id"]!!.toLong())
        call.respondJson(JSONObject().put("ok", true))
    }

    // ===== 记忆事实 =====

    get("/api/targets/{id}/facts") {
        call.respondJson(JSONArray().of(service.listFacts(call.parameters["id"]!!.toLong())) { it.toJson() })
    }

    post("/api/targets/{id}/facts") {
        val body = JSONObject(call.receiveText())
        val factId = service.addFact(call.parameters["id"]!!.toLong(), body.getString("text"))
        call.respondJson(JSONObject().put("id", factId))
    }

    put("/api/facts/{id}") {
        val body = JSONObject(call.receiveText())
        service.updateFact(call.parameters["id"]!!.toLong(), body.getString("text"))
        call.respondJson(JSONObject().put("ok", true))
    }

    delete("/api/facts/{id}") {
        service.deleteFact(call.parameters["id"]!!.toLong())
        call.respondJson(JSONObject().put("ok", true))
    }

    // ===== 会话 / 消息 =====

    get("/api/sessions") {
        call.respondJson(JSONArray().of(service.listSessions()) { it.toJson() })
    }

    post("/api/sessions") {
        val body = JSONObject(call.receiveText())
        val id = service.createSession(
            targetId = if (body.has("targetId") && !body.isNull("targetId")) body.getLong("targetId") else null,
        )
        call.respondJson(JSONObject().put("id", id))
    }

    delete("/api/sessions/{id}") {
        service.deleteSession(call.parameters["id"]!!.toLong())
        call.respondJson(JSONObject().put("ok", true))
    }

    /** 会话绑定/解绑档案（targetId 为 null 即解绑），记忆注入随 targetId 走 */
    put("/api/sessions/{id}/target") {
        val id = call.parameters["id"]!!.toLong()
        val body = JSONObject(call.receiveText())
        val targetId = if (body.has("targetId") && !body.isNull("targetId")) body.getLong("targetId") else null
        service.updateSessionTarget(id, targetId)
        call.respondJson(JSONObject().put("ok", true))
    }

    get("/api/sessions/{id}/messages") {
        call.respondJson(JSONArray().of(service.listMessages(call.parameters["id"]!!.toLong())) { it.toJson() })
    }

    delete("/api/messages/{id}") {
        service.deleteMessage(call.parameters["id"]!!.toLong())
        call.respondJson(JSONObject().put("ok", true))
    }

    // ===== 阶段 2：聊天 / 测连接 / 图片 / onboarding =====

    /**
     * SSE 流式聊天（POST /api/chat/stream）。
     * 请求体：{sessionId, modelId, text, imageDataUrls?[]}
     * 事件帧（data: {...}\n\n）：chat/thinking/card/done/error
     * Ktor 2.3 无内置 SSE 插件：手动设置响应头 + 手动管道写帧（逐 token 实时 flush）。
     */
    post("/api/chat/stream") {
        val body = JSONObject(call.receiveText())
        val sessionId = body.getLong("sessionId")
        val modelId = body.getLong("modelId")
        val text = body.getString("text")
        val imageDataUrls = body.optJSONArray("imageDataUrls")?.let { arr ->
            buildList {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }.take(MAX_IMAGES_PER_REQUEST)
        } ?: emptyList()

        // SSE 响应头（禁缓存、保持连接、禁 nginx 缓冲）
        call.response.headers.append("Cache-Control", "no-cache")
        call.response.headers.append("Connection", "keep-alive")
        call.response.headers.append("X-Accel-Buffering", "no")

        // 挂起管道：手动写入 SSE 帧（autoFlush 保证逐 token 实时到达）
        val channel = ByteChannel(autoFlush = true)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                chatEngine.sendMessage(sessionId, modelId, text, imageDataUrls) { event ->
                    runBlocking { channel.writeStringUtf8("data: $event\n\n") }
                }
            } catch (e: Exception) {
                val err = JSONObject().put("type", "error")
                    .put("code", "STREAM_BROKEN").put("message", (e.message ?: "流中断").take(200))
                runCatching { runBlocking { channel.writeStringUtf8("data: $err\n\n") } }
            } finally {
                channel.close(null)
            }
        }

        call.respond(object : io.ktor.http.content.OutgoingContent.ReadChannelContent() {
            override val contentType: ContentType = ContentType.Text.EventStream
            override fun readFrom(): ByteReadChannel = channel
        })
    }

    /** 测连接红绿灯：对 provider 下每个模型发最小 chat，任一成功即绿灯并回写状态 */
    post("/api/providers/{id}/test") {
        call.respondJson(chatEngine.testConnection(call.parameters["id"]!!.toLong()))
    }

    /**
     * 图片上传（multipart/form-data，字段名 images，≤10 张）。
     * 压缩遵守 ImageSpec 契约（>20MB 拒、最长边 1568、JPEG 85%），返回 data url 列表供 chat/stream 使用。
     */
    post("/api/images/upload") {
        val multipart = call.receiveMultipart()
        // forEachPart 是普通 lambda，provider() 是 suspend——先收集 Input，再在本 suspend 块逐张读
        val inputs = mutableListOf<io.ktor.utils.io.core.Input>()
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) inputs.add(part.provider())
            part.dispose()
        }

        val dataUrls = mutableListOf<String>()
        var error: String? = null
        if (inputs.size > MAX_IMAGES_PER_REQUEST) {
            error = "一次最多上传 $MAX_IMAGES_PER_REQUEST 张图片"
        } else {
            for (input in inputs) {
                if (error != null) break
                val bytes = input.readBytes()
                try {
                    dataUrls.add(DesktopImageCompressor.compressToDataUrl(bytes))
                } catch (e: DesktopImageCompressor.ImageTooLargeException) {
                    error = e.message
                } catch (e: Exception) {
                    error = "图片处理失败：${e.message}"
                }
            }
        }
        if (error != null) {
            call.respondJson(JSONObject().put("ok", false).put("error", error))
        } else {
            call.respondJson(JSONObject().put("ok", true).put("dataUrls", JSONArray(dataUrls)))
        }
    }

    /** onboarding 状态：未配置任何带 Key 的提供商 → 前端进引导页 */
    get("/api/onboarding") {
        call.respondJson(JSONObject().put("needsOnboarding", service.needsOnboarding()))
    }

    // ===== 阶段 4：更新检查 / 数据管理 =====

    /** 更新检查（GitHub Releases，versionName 段比较，与手机版同语义） */
    get("/api/update") {
        val r = com.wenyan.app.data.update.DesktopUpdateChecker.check(DESKTOP_VERSION)
        call.respondJson(JSONObject()
            .put("status", r.status).put("current", r.current)
            .put("latest", r.latest).put("notes", r.notes)
            .put("downloadUrl", r.downloadUrl).put("error", r.error))
    }

    /** 全量数据导出（JSON 下载，Key 密文脱敏） */
    get("/api/export") {
        val json = service.exportAllJson()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm").format(java.util.Date())
        call.response.headers.append(
            "Content-Disposition",
            "attachment; filename=\"wenyan-backup-$stamp.json\"",
        )
        call.respondText(json.toString(2), ContentType.Application.Json)
    }

    /** 清空全部数据并重新注入预设（危险操作，前端已二次确认） */
    post("/api/data/clear") {
        service.clearAll()
        service.seedIfEmpty()
        call.respondJson(JSONObject().put("ok", true))
    }
}

/** 统一 JSON 响应 */
private suspend fun io.ktor.server.application.ApplicationCall.respondJson(json: JSONObject) {
    respondText(json.toString(), ContentType.Application.Json)
}

private suspend fun io.ktor.server.application.ApplicationCall.respondJson(json: JSONArray) {
    respondText(json.toString(), ContentType.Application.Json)
}
