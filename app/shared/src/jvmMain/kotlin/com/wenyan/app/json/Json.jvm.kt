package com.wenyan.app.json

/** O4: JVM actual —— 使用外部 org.json（与桌面端现状一致） */
actual object Json {
    actual val NULL: Any = org.json.JSONObject.NULL

    actual fun obj(): JsonObject = JvmJsonObject(org.json.JSONObject())
    actual fun obj(json: String): JsonObject = JvmJsonObject(org.json.JSONObject(json))
    actual fun arr(): JsonArray = JvmJsonArray(org.json.JSONArray())
    actual fun arr(json: String): JsonArray = JvmJsonArray(org.json.JSONArray(json))
}

private class JvmJsonObject(internal val delegate: org.json.JSONObject) : JsonObject {
    override fun put(key: String, value: Any?): JsonObject {
        delegate.put(key, unwrap(value))
        return this
    }

    override fun optString(key: String, fallback: String): String = delegate.optString(key, fallback)
    override fun optInt(key: String, fallback: Int): Int = delegate.optInt(key, fallback)
    override fun optLong(key: String, fallback: Long): Long = delegate.optLong(key, fallback)
    override fun optBoolean(key: String, fallback: Boolean): Boolean = delegate.optBoolean(key, fallback)
    override fun optJSONObject(key: String): JsonObject? = delegate.optJSONObject(key)?.let { JvmJsonObject(it) }
    override fun optJSONArray(key: String): JsonArray? = delegate.optJSONArray(key)?.let { JvmJsonArray(it) }
    override fun has(key: String): Boolean = delegate.has(key)
    override fun isNull(key: String): Boolean = delegate.isNull(key)
    override fun keys(): List<String> = delegate.keys().asSequence().toList()
    override fun toString(): String = delegate.toString()
}

private class JvmJsonArray(internal val delegate: org.json.JSONArray) : JsonArray {
    override fun length(): Int = delegate.length()
    override fun optJSONObject(index: Int): JsonObject? = delegate.optJSONObject(index)?.let { JvmJsonObject(it) }
    override fun opt(index: Int): Any? = wrap(delegate.opt(index))

    // 桌面端 org.json 对数组中显式 null 元素：optString 返回 fallback、getString 抛异常（与 Android
    // 端返回 "null" 字面量的分歧由 Android 侧 isNull 预检抹平），本实现保持平台原生语义。
    override fun optString(index: Int, fallback: String): String = delegate.optString(index, fallback)
    override fun getString(index: Int): String = delegate.getString(index)
    override fun put(value: Any?): JsonArray {
        delegate.put(unwrap(value))
        return this
    }

    override fun toString(): String = delegate.toString()
}

private fun unwrap(value: Any?): Any? = when (value) {
    is JvmJsonObject -> value.delegate
    is JvmJsonArray -> value.delegate
    else -> value
}

private fun wrap(value: Any?): Any? = when (value) {
    is org.json.JSONObject -> JvmJsonObject(value)
    is org.json.JSONArray -> JvmJsonArray(value)
    else -> value
}
