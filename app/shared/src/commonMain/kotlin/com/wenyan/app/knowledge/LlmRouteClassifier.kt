package com.wenyan.app.knowledge

import com.wenyan.app.json.Json
import com.wenyan.app.json.JsonArray
import com.wenyan.app.json.JsonObject
import com.wenyan.app.llm.LlmClient
import com.wenyan.app.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

/**
 * 路由分类器的 Provider 配置（baseUrl/key/model）。
 * 由平台侧实现（读设置存储 / 灰度开关），shared 不读设置存储。
 */
fun interface RouteLlmConfigProvider {

    /** 返回 null = 未配置或未启用，分类器视为不可用（调用方走离线兜底） */
    fun current(): RouteLlmConfig?
}

/** 一次路由请求所需的 Provider 三元组 */
data class RouteLlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/**
 * 平台侧 LLM 路由装配（双端同构唯一来源；安卓 RealChatRepository / 桌面 ChatEngine 各自供给
 * 开关读取与主模型三元组解析两路回调，语义与默认值在此收敛）：
 * 1. [catalog] 为 null（平台无 routing-catalog.json）→ null，分类器不可用
 * 2. [isLlmRouting] 为 false（knowledgeRouting=offline）→ null，**且短路不解析主模型配置**
 *    （零解密、零网络——offline 档「完全不发起路由请求」的保证点）
 * 3. [resolveMainModelConfig] 返回 null（主模型未配置/无 Key）→ null，走离线兜底
 * 其余情况返回持 [transport] 的当前分类器，由调用方传入 KnowledgeEngine.buildInjection。
 */
suspend fun currentRouteClassifier(
    catalog: RoutingCatalog?,
    isLlmRouting: suspend () -> Boolean,
    resolveMainModelConfig: suspend () -> RouteLlmConfig?,
    transport: RouteChatTransport = OkHttpRouteChatTransport(),
): LlmRouteClassifier? {
    if (catalog == null) return null
    if (!isLlmRouting()) return null
    val config = resolveMainModelConfig() ?: return null
    return LlmRouteClassifier({ config }, catalog, transport)
}

/**
 * 非流式 chat 请求的传输接缝：生产走 OkHttpRouteChatTransport，测试注入 mock 分类客户端。
 */
fun interface RouteChatTransport {

    /** @return assistant 正文文本；失败返回 null 或抛异常（两者等价，均视为分类失败） */
    suspend fun post(config: RouteLlmConfig, bodyJson: String): String?
}

/**
 * LLM 参与知识路由的分类器（HybridVariantRouter 之上的第一优先级，失败即回退离线）。
 *
 * 一次非流式 chat 请求：system = 路由指令 + 目录（file|title|summary），user = query，
 * max_tokens = 200，总超时 10 秒。容错解析首个 JSON（容忍代码围栏与前后噪声），
 * 输出 0-5 个文件名；任一文件名不在目录白名单 → 整体判失败。
 * 任何失败返回 null 由调用方走离线兜底；空数组 = 合法弃权（不注入文档）。
 */
class LlmRouteClassifier(
    private val configProvider: RouteLlmConfigProvider,
    private val catalog: RoutingCatalog,
    private val transport: RouteChatTransport = OkHttpRouteChatTransport(),
) {

    /**
     * @return 0-5 个目录内文件名（空数组 = 弃权）；失败返回 null
     */
    suspend fun route(query: String): List<String>? {
        val config = configProvider.current() ?: return null
        // 空输入直接弃权，与离线路由（blank → emptyList）语义一致，不浪费一次网络请求
        if (query.isBlank()) return emptyList()

        val raw = try {
            withTimeout(ROUTE_TIMEOUT_MS) { transport.post(config, buildRequestJson(config, query)) }
        } catch (e: CancellationException) {
            // 只吞总超时；外部作用域的取消必须继续传播
            if (e is TimeoutCancellationException) {
                AppLogger.w("llm_route_timeout", "timeout_ms" to ROUTE_TIMEOUT_MS)
                null
            } else {
                throw e
            }
        } catch (e: Exception) {
            AppLogger.w("llm_route_failed", "error" to (e.message ?: e.javaClass.simpleName))
            null
        } ?: return null

        return parseModelOutput(raw)
    }

    /** 请求体：OpenAI 兼容、非流式、max_tokens=200（ChatRequestBuilder 固定 stream=true，故此处单独构造） */
    private fun buildRequestJson(config: RouteLlmConfig, query: String): String {
        val body = Json.obj()
        body.put("model", config.model)
        body.put("stream", false)
        body.put("max_tokens", MAX_TOKENS)

        val messages = Json.arr()
        val system = Json.obj()
        system.put("role", "system")
        system.put("content", SYSTEM_PROMPT + "\n" + catalog.toPromptLines())
        messages.put(system)

        val user = Json.obj()
        user.put("role", "user")
        user.put("content", query)
        messages.put(user)

        body.put("messages", messages)
        return body.toString()
    }

    /**
     * 容错解析模型输出 → 0-5 个白名单内文件名。
     * 坏 JSON / 任一文件名越白名单 → null（宁走离线兜底，也不注入未审计文档）。
     */
    private fun parseModelOutput(raw: String): List<String>? {
        val arr = extractModelArray(raw) ?: run {
            AppLogger.w("llm_route_bad_json")
            return null
        }
        val names = buildList {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i, "").trim()
                if (value.isNotEmpty()) add(value)
            }
        }.distinct().take(MAX_ROUTES)
        if (names.any { it !in catalog.allowedFiles }) {
            AppLogger.w("llm_route_invalid_doc", "count" to names.size)
            return null
        }
        return names
    }

    /**
     * 从模型原始输出中提取首个 JSON 数组（容忍代码围栏与前后噪声，口径同 AnalysisParser 但面向数组）：
     * 1. 剥起始 ``` 围栏（容忍 json 语言标记）
     * 2. 逐个 '[' 候选起点做字符串感知配平，取首个「全字符串元素」的数组
     *    （空数组 = 合法弃权，接受；含非字符串元素的候选视为噪声继续向后找）
     * 3. 无数组时找首个配平 {...}，取其中字符串数组字段（容忍 {"files":[...]} 包裹；
 *    选取与 keys() 迭代序无关——"files" 优先，否则按 key 字典序，双端确定性一致）
     */
    private fun extractModelArray(raw: String): JsonArray? {
        val text = unwrapFence(raw.trim())

        var from = text.indexOf('[')
        while (from >= 0) {
            balancedAt(text, from, '[', ']')?.let { candidate ->
                val parsed = runCatching { Json.arr(candidate) }.getOrNull()
                if (parsed != null && isStringArray(parsed)) return parsed
            }
            from = text.indexOf('[', from + 1)
        }

        from = text.indexOf('{')
        while (from >= 0) {
            balancedAt(text, from, '{', '}')?.let { candidate ->
                val parsed = runCatching { Json.obj(candidate) }.getOrNull() ?: return@let
                selectWrappedStringArray(parsed)?.let { return it }
            }
            from = text.indexOf('{', from + 1)
        }
        return null
    }

    /** 起始 ``` 围栏剥离（```json / ``` 语言标记 + 闭合围栏均容忍；无闭合则取剩余全文） */
    private fun unwrapFence(trimmed: String): String {
        if (!trimmed.startsWith("```")) return trimmed
        var body = trimmed.removePrefix("```")
        if (body.startsWith("json", ignoreCase = true)) body = body.substring(4)
        if (body.startsWith("\r\n")) body = body.substring(2) else if (body.startsWith("\n")) body = body.substring(1)
        val end = body.lastIndexOf("```")
        if (end >= 0) body = body.substring(0, end)
        return body.trim()
    }

    /** L13 同款：从 [start] 起做字符串感知的括号配平（[open, close] 对），失败返回 null */
    private fun balancedAt(text: String, start: Int, open: Char, close: Char): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escape -> escape = false
                c == '\\' -> escape = true
                c == '"' && !escape -> inString = !inString
                !inString -> when (c) {
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private companion object {
        /** 总超时 10 秒（协程 withTimeout + 传输层 callTimeout 双保险） */
        const val ROUTE_TIMEOUT_MS = 10_000L

        const val MAX_TOKENS = 200

        /** 输出上限 0-5 个文件名 */
        const val MAX_ROUTES = 5

        const val SYSTEM_PROMPT =
            "你是恋爱知识库的路由器。根据用户输入，从下面的目录中选出最相关的 0-5 篇文档。\n" +
                "目录每行格式：文件名|标题|摘要。\n" +
                "只输出一个 JSON 字符串数组（元素为目录中的文件名），按相关度排序，最多 5 个；" +
                "没有合适的文档就输出 []。不要输出任何解释或其他文本。"
    }
}

/** 对象包裹输出的约定数组字段名（"files" 优先于字典序兜底） */
private const val WRAPPER_FILES_KEY = "files"

private fun isStringArray(arr: JsonArray): Boolean {
    for (i in 0 until arr.length()) {
        if (arr.opt(i) !is String) return false
    }
    return true
}

/**
 * 对象包裹输出的确定性字符串数组选取（容错解析第 3 步，公开纯函数便于直接单测）。
 * 双端 keys() 迭代序无契约（Android org.json 底层保插入序 / 桌面 org.json-java 底层 HashMap
 * 无序），按迭代序取首个字符串数组字段会让同一模型输出在两端选出不同数组、路由结果分歧 →
 * 选取规则与迭代序无关：约定字段名 "files" 优先，其余按 key 字典序取首个全字符串元素的字段；
 * 无则 null。
 */
fun selectWrappedStringArray(parsed: JsonObject): JsonArray? {
    parsed.optJSONArray(WRAPPER_FILES_KEY)?.takeIf { isStringArray(it) }?.let { return it }
    for (key in parsed.keys().sorted()) {
        val arr = parsed.optJSONArray(key) ?: continue
        if (isStringArray(arr)) return arr
    }
    return null
}

/**
 * 生产传输：OkHttp 异步非流式 chat 请求（现有 llm/OkHttp 设施）。
 * - 端点策略沿用 LlmClient（https 放行；http 仅本地/私网，公网明文拒绝），不放宽不收紧
 * - callTimeout=10 秒总超时，与 route() 的 withTimeout 双保险；withTimeout 取消时同步 cancel 调用
 */
class OkHttpRouteChatTransport(
    private val client: OkHttpClient = defaultClient(),
) : RouteChatTransport {

    override suspend fun post(config: RouteLlmConfig, bodyJson: String): String? =
        suspendCancellableCoroutine { cont ->
            LlmClient.endpointPolicyViolation(config.baseUrl)?.let { reason ->
                AppLogger.w("llm_route_blocked", "reason" to reason)
                cont.resumeWith(Result.success(null))
                return@suspendCancellableCoroutine
            }
            val request = Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                // L15: 与 LlmClient 统一 User-Agent（部分网关按 UA 做访问控制）
                .header("User-Agent", "wenyan-llm-client")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val call = client.newCall(request)
            // 总超时触发 withTimeout 取消协程 → 这里同步取消底层调用，连接不悬挂
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) {
                            AppLogger.w("llm_route_transport_error", "error" to (e.message ?: e.javaClass.simpleName))
                            cont.resumeWith(Result.success(null))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val content = response.use { resp ->
                            val payload = runCatching { resp.body?.string() }.getOrNull()
                            if (!resp.isSuccessful) {
                                AppLogger.w("llm_route_http_error", "code" to resp.code)
                                null
                            } else {
                                payload?.let(::extractAssistantContent)
                            }
                        }
                        if (cont.isActive) cont.resumeWith(Result.success(content))
                    }
                },
            )
        }

    /** 非流式响应体 → choices[0].message.content；结构不符/空正文 → null（视为失败） */
    private fun extractAssistantContent(payload: String): String? = try {
        val root: JsonObject = Json.obj(payload)
        val content = root.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optStringOrNull("content")
        content?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    companion object {

        /**
         * 进程级共享路由客户端（F11 同款：连接池 + Dispatcher 线程池全进程复用）。
         * routeClassifier() 每次消息发送都会装配新分类器/传输层，客户端必须是单例，
         * 否则每条消息泄漏一个独立连接池与 Dispatcher 线程池。
         */
        private val sharedClient: OkHttpClient by lazy { buildClient() }

        /** 路由专用小客户端：callTimeout 封顶 10 秒；readTimeout 同步放宽到 10 秒容忍慢 provider（保持 3 秒会让 10 秒预算形同虚设），connectTimeout 保持 3 秒快速失败 */
        fun defaultClient(): OkHttpClient = sharedClient

        private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .apply {
                val ps = ProxySelector.getDefault()
                if (ps != null) proxySelector(ps)
            }
            .build()
    }
}
