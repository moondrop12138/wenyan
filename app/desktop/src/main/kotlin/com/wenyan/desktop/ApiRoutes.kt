package com.wenyan.desktop

import com.wenyan.app.data.db.MemoryFactEntity
import com.wenyan.app.data.db.MessageEntity
import com.wenyan.app.data.db.ModelEntity
import com.wenyan.app.data.db.ProviderEntity
import com.wenyan.app.data.db.SessionEntity
import com.wenyan.app.data.db.TargetEntity
import com.wenyan.app.data.image.DesktopImageCompressor
import com.wenyan.app.llm.MAX_IMAGES_PER_REQUEST
import io.ktor.utils.io.core.readAvailable
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCallPipeline
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
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
    // v1.9.0 透传 kind；v1.9.1 透传 expiresAt/source（前端记忆页徽标/转永久用）
    .put("kind", kind)
    .put("expiresAt", expiresAt ?: JSONObject.NULL)
    .put("source", source)

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

/** L7: 图片上传总大小上限（50MB，防全量读内存） */
private const val MAX_UPLOAD_TOTAL_BYTES = 50L * 1024 * 1024

/** H1: CSRF 允许的 Host 白名单（剥端口后全等比较） */
private val ALLOWED_HOSTS = setOf("127.0.0.1", "localhost", "::1")

/**
 * H1: Host 头取主机名："127.0.0.1:18923" → "127.0.0.1"；"[::1]:18923" → "::1"。
 * 前缀匹配可被 127.0.0.1.evil.com 绕过（DNS 重绑定），必须全等比较。
 */
private fun hostName(hostHeader: String): String {
    val h = hostHeader.trim().lowercase()
    return if (h.startsWith("[")) h.substringBefore(']').removePrefix("[")
    else h.substringBefore(':')
}

/**
 * M2: SSE 桥——生产者与写循环都挂 call 所属作用域。
 * 客户端断开 → call 协程取消 → 写循环取消 → producer.cancel() → 内部 LLM flow
 * 经 awaitClose 取消 EventSource（停止白烧 token）；不再 runBlocking 占死 IO 线程。
 */
private fun kotlinx.coroutines.CoroutineScope.launchSseBridge(
    channel: ByteChannel,
    produce: suspend (send: (JSONObject) -> Unit) -> Unit,
) {
    // 有界缓冲：解耦 LLM 回调线程与网络写入；写端挂掉时 trySend 静默丢弃（对端已不存在）
    val events = Channel<JSONObject>(capacity = Channel.BUFFERED)
    val producer = launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            produce { events.trySend(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val err = JSONObject().put("type", "error")
                .put("code", "STREAM_BROKEN").put("message", (e.message ?: "流中断").take(200))
            events.trySend(err)
        } finally {
            events.close()
        }
    }
    launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            for (event in events) {
                channel.writeStringUtf8("data: $event\n\n")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // M2: 对端断开/写失败——取消内部流即可；channel 在 finally 关闭，无需再写错误帧
        } finally {
            producer.cancel()
            channel.close(null)
        }
    }
}

/** API 路由（/api 前缀）。token 为启动时生成的随机 CSRF token。 */
fun Route.apiRoutes(service: WenyanService, chatEngine: ChatEngine, token: String) {

    // H5: CSRF 防护——所有写请求（POST/PUT/DELETE）校验 X-Wenyan-Token + Host（127.0.0.1/localhost）
    // H1/M1 修复：全部 /api 请求精确校验 Host（剥端口后与白名单全等比较，防 127.0.0.1.evil.com 绕过），
    // 写请求额外校验 X-Wenyan-Token；GET 接口（export/search 等）不再裸奔。
    // 合法前端本身就跑在 127.0.0.1:port，Host 校验恒过；token 校验仍仅对写请求。
    intercept(ApplicationCallPipeline.Call) {
        val host = call.request.headers["Host"] ?: ""
        val hostOk = host.isBlank() || hostName(host) in ALLOWED_HOSTS
        if (!hostOk) {
            call.respond(HttpStatusCode.Forbidden, mapOf("error" to "forbidden"))
            finish()
            return@intercept
        }
        val method = call.request.local.method
        if (method == HttpMethod.Post || method == HttpMethod.Put || method == HttpMethod.Delete) {
            if (call.request.headers["X-Wenyan-Token"] != token) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "forbidden"))
                finish()
            }
        }
    }

    /** 前端启动时获取 CSRF token（同源可读；跨源攻击者受同源策略限制读不到） */
    get("/api/bootstrap") {
        call.respondJson(JSONObject().put("token", token))
    }

    get("/api/health") {
        call.respondText("""{"ok":true,"version":"$DESKTOP_VERSION-desktop"}""", ContentType.Application.Json)
    }

    /** O6: 用量指标（进程内累计；仅计数/耗时，不含用户消息原文） */
    get("/api/metrics") {
        call.respondJson(org.json.JSONObject(com.wenyan.app.llm.UsageMetrics.toJson().toString()))
    }

    /** O3: 全文检索（?q=关键词，命中消息 → 前端跳转对应会话） */
    get("/api/search") {
        val q = call.request.queryParameters["q"] ?: ""
        val results = JSONArray().apply {
            service.searchMessages(q).forEach { m ->
                put(org.json.JSONObject()
                    .put("sessionId", m.sessionId).put("type", m.type)
                    .put("content", m.content).put("createdAt", m.createdAt))
            }
        }
        call.respondJson(JSONObject().put("results", results))
    }

    /** 诊断：报告正在运行的服务进程实际加载的 ImageIO 解码器（排查格式支持用） */
    get("/api/debug/imageio") {
        javax.imageio.ImageIO.scanForPlugins()
        val readers = mutableListOf<String>()
        for (fmt in arrayOf("png", "jpeg", "webp", "gif", "bmp")) {
            val it = javax.imageio.ImageIO.getImageReadersByFormatName(fmt)
            val names = mutableListOf<String>()
            while (it.hasNext()) names.add(it.next().javaClass.name)
            readers.add(fmt + "=" + (if (names.isEmpty()) "(none)" else names.joinToString(",")))
        }
        call.respondJson(JSONObject()
            .put("javaVersion", System.getProperty("java.version"))
            .put("javaHome", System.getProperty("java.home"))
            .put("readers", readers.joinToString(" | ")))
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
                // L20 修复：{"score":null} 应清空而非写 0（与 mbti/relationStatus 的 optString(...,null) 对称）
                score = if (body.has("score")) (if (body.isNull("score")) null else body.optInt("score")) else current.score,
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

    /** v1.9.1 临时事实转永久（清空到期时间） */
    post("/api/facts/{id}/permanent") {
        service.makePermanent(call.parameters["id"]!!.toLong())
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
        // M2: 生产者挂 call 作用域（原 CoroutineScope(Dispatchers.IO).launch + runBlocking 写入：
        // 客户端断开后无人读 channel，缓冲填满 → runBlocking 永久阻塞一个 IO 线程且内部流不取消）
        launchSseBridge(channel) { send ->
            chatEngine.sendMessage(sessionId, modelId, text, imageDataUrls) { event -> send(event) }
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

    // ===== 设置槽位（视觉模型 + v1.9.0 记忆控制；Properties KV，不动 Room schema） =====

    get("/api/settings") {
        call.respondJson(JSONObject()
            .put("visionModelId", service.getVisionModelId()?.let { JSONObject.wrap(it) } ?: JSONObject.NULL)
            .put("memoryAutoEnabled", service.isMemoryAutoEnabled()))
    }

    /** 部分更新设置；visionModelId 为 null 即清除槽位（对齐手机端 setVisionModelId 语义）；memoryAutoEnabled 布尔直接写 */
    put("/api/settings") {
        val body = JSONObject(call.receiveText())
        if (body.has("visionModelId")) {
            service.setVisionModelId(
                if (body.isNull("visionModelId")) null else body.getLong("visionModelId")
            )
        }
        if (body.has("memoryAutoEnabled")) {
            service.setMemoryAutoEnabled(body.getBoolean("memoryAutoEnabled"))
        }
        call.respondJson(JSONObject().put("ok", true))
    }

    /** v1.9.0 撤销最近一次自动记忆：删除日志对应事实，返回删除条数 */
    post("/api/memory/undo-last-write") {
        val removedIds = service.undoLastMemoryWrite()
        removedIds.forEach { service.deleteFact(it) }
        call.respondJson(JSONObject().put("ok", true).put("removed", removedIds.size))
    }

    /**
     * 通道 B 第二步：确认转述后走主模型纯文本分析（SSE 流式，帧格式同 /api/chat/stream）。
     * 请求体：{sessionId, modelId, transcription}
     */
    post("/api/chat/confirm-transcription") {
        val body = JSONObject(call.receiveText())
        val sessionId = body.getLong("sessionId")
        val modelId = body.getLong("modelId")
        val transcription = body.getString("transcription")

        call.response.headers.append("Cache-Control", "no-cache")
        call.response.headers.append("Connection", "keep-alive")
        call.response.headers.append("X-Accel-Buffering", "no")

        val channel = ByteChannel(autoFlush = true)
        // M2: 同 /api/chat/stream——生产者挂 call 作用域，断开即取消内部流
        launchSseBridge(channel) { send ->
            chatEngine.confirmTranscription(sessionId, modelId, transcription) { event -> send(event) }
        }

        call.respond(object : io.ktor.http.content.OutgoingContent.ReadChannelContent() {
            override val contentType: ContentType = ContentType.Text.EventStream
            override fun readFrom(): ByteReadChannel = channel
        })
    }

    /**
     * 图片上传（multipart/form-data，字段名 images，≤10 张）。
     * 压缩遵守 ImageSpec 契约（>20MB 拒、最长边 1568、JPEG 85%），返回 data url 列表供 chat/stream 使用。
     */
    post("/api/images/upload") {
        val multipart = call.receiveMultipart()
        // 关键：PartData.FileItem 的字节必须在 forEachPart 回调内（part 存活期间）读出——
        // part.dispose() 后 provider() 返回的 Input 立即失效，延迟读取只会得到 0 字节。
        // forEachPart 是普通 lambda 不能 suspend，但 provider() 返回的 Input 同步阻塞读可直接用
        // （M4: 分块边读边累计，超限立即中断，不整段载入内存）。
        val imagesBytes = mutableListOf<ByteArray>()
        var totalBytes = 0L
        var tooLarge = false
        multipart.forEachPart { part ->
            // M4 修复：边读边累计、超限立即中断读取——原 provider().readBytes() 先全量读进内存
            // 再检查上限，数 GB 请求在检查前就 OOM。超限后置位，由下方统一返回错误文案。
            if (!tooLarge && part is PartData.FileItem) {
                val buf = java.io.ByteArrayOutputStream()
                // Ktor 2.x：FileItem.provider() 给 kotlinx-io Input；
                // readAvailable 是 io.ktor.utils.io.core 包级扩展（文件头已导入）
                val input = part.provider()
                try {
                    val chunk = ByteArray(64 * 1024)
                    while (!input.endOfInput) {
                        val n = input.readAvailable(chunk, 0, chunk.size)
                        if (n <= 0) break
                        if (totalBytes + n > MAX_UPLOAD_TOTAL_BYTES) {
                            tooLarge = true
                            break
                        }
                        buf.write(chunk, 0, n)
                        totalBytes += n
                    }
                } finally {
                    input.close()
                }
                imagesBytes.add(buf.toByteArray())
            }
            part.dispose()
        }

        val dataUrls = mutableListOf<String>()
        var error: String? = null
        if (tooLarge || totalBytes > MAX_UPLOAD_TOTAL_BYTES) {
            error = "上传总大小超过 50MB 限制"
        } else if (imagesBytes.isEmpty()) {
            error = "未收到图片"
        } else if (imagesBytes.size > MAX_IMAGES_PER_REQUEST) {
            error = "一次最多上传 $MAX_IMAGES_PER_REQUEST 张图片"
        } else {
            for (bytes in imagesBytes) {
                if (error != null) break
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

    /** O1: 从备份 JSON 恢复（校验 + 清空重建 + FK 重映射；Key 脱敏需重新输入） */
    post("/api/import") {
        val json = runCatching { org.json.JSONObject(call.receiveText()) }.getOrNull()
        if (json == null) {
            call.respondJson(JSONObject().put("ok", false).put("error", "备份文件不是有效 JSON"))
            return@post
        }
        val (ok, error) = service.importAllJson(json)
        call.respondJson(JSONObject().put("ok", ok).put("error", error))
    }
}

/** 统一 JSON 响应 */
private suspend fun io.ktor.server.application.ApplicationCall.respondJson(json: JSONObject) {
    respondText(json.toString(), ContentType.Application.Json)
}

private suspend fun io.ktor.server.application.ApplicationCall.respondJson(json: JSONArray) {
    respondText(json.toString(), ContentType.Application.Json)
}
