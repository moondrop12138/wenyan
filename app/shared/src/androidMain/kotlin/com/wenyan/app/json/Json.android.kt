package com.wenyan.app.json

/** O4: Android actual —— 使用 android.jar 内置 org.json */
actual object Json {
    actual val NULL: Any = org.json.JSONObject.NULL

    actual fun obj(): JsonObject = AndroidJsonObject(org.json.JSONObject())
    actual fun obj(json: String): JsonObject = AndroidJsonObject(org.json.JSONObject(json))
    actual fun arr(): JsonArray = AndroidJsonArray(org.json.JSONArray())
    actual fun arr(json: String): JsonArray = AndroidJsonArray(org.json.JSONArray(json))
}

private class AndroidJsonObject(internal val delegate: org.json.JSONObject) : JsonObject {
    override fun put(key: String, value: Any?): JsonObject {
        delegate.put(key, unwrap(value))
        return this
    }

    override fun optString(key: String, fallback: String): String = delegate.optString(key, fallback)
    override fun optInt(key: String, fallback: Int): Int = delegate.optInt(key, fallback)
    override fun optLong(key: String, fallback: Long): Long = delegate.optLong(key, fallback)
    override fun optBoolean(key: String, fallback: Boolean): Boolean = delegate.optBoolean(key, fallback)
    override fun optJSONObject(key: String): JsonObject? = delegate.optJSONObject(key)?.let { AndroidJsonObject(it) }
    override fun optJSONArray(key: String): JsonArray? = delegate.optJSONArray(key)?.let { AndroidJsonArray(it) }
    override fun has(key: String): Boolean = delegate.has(key)
    override fun isNull(key: String): Boolean = delegate.isNull(key)
    override fun keys(): List<String> = delegate.keys().asSequence().toList()
    override fun toString(): String = delegate.toString()
}

private class AndroidJsonArray(internal val delegate: org.json.JSONArray) : JsonArray {
    override fun length(): Int = delegate.length()
    override fun optJSONObject(index: Int): JsonObject? = delegate.optJSONObject(index)?.let { AndroidJsonObject(it) }
    override fun opt(index: Int): Any? = wrap(delegate.opt(index))

    // F123 修复：Android org.json 对数组中显式 null 元素的 optString 返回字面量 "null"
    // （桌面端返回 fallback，M7 同款平台分歧）——isNull 预检后双端一致，
    // 模型产出的 null 元素不再变成 "null" 字符串混进解析结果。
    override fun optString(index: Int, fallback: String): String =
        if (delegate.isNull(index)) fallback else delegate.optString(index, fallback)

    /** 显式 null 按缺失处理返回空串（桌面端 org.json 此处抛 JSONException；本端不再产出 "null" 字面量） */
    override fun getString(index: Int): String =
        if (delegate.isNull(index)) "" else delegate.getString(index)

    override fun put(value: Any?): JsonArray {
        delegate.put(unwrap(value))
        return this
    }

    override fun toString(): String = delegate.toString()
}

private fun unwrap(value: Any?): Any? = when (value) {
    is AndroidJsonObject -> value.delegate
    is AndroidJsonArray -> value.delegate
    else -> value
}

private fun wrap(value: Any?): Any? = when (value) {
    is org.json.JSONObject -> AndroidJsonObject(value)
    is org.json.JSONArray -> AndroidJsonArray(value)
    else -> value
}
