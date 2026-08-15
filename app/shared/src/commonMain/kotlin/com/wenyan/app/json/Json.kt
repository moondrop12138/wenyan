package com.wenyan.app.json

/**
 * O4: KMP 公共 JSON 抽象。
 * commonMain 不依赖 org.json；实际实现由 androidMain（android.jar org.json）
 * 与 jvmMain（外部 org.json）分别提供。只覆盖项目用到的子集。
 */
interface JsonObject {
    fun put(key: String, value: Any?): JsonObject
    fun optString(key: String, fallback: String = ""): String
    fun optInt(key: String, fallback: Int = 0): Int
    fun optLong(key: String, fallback: Long = 0): Long
    fun optBoolean(key: String, fallback: Boolean = false): Boolean
    fun optJSONObject(key: String): JsonObject?
    fun optJSONArray(key: String): JsonArray?
    fun getJSONObject(key: String): JsonObject
    fun has(key: String): Boolean
    fun isNull(key: String): Boolean
    fun getString(key: String): String
    fun keys(): List<String>
    override fun toString(): String
}

interface JsonArray {
    fun length(): Int
    fun optJSONObject(index: Int): JsonObject?
    fun getJSONObject(index: Int): JsonObject
    fun opt(index: Int): Any?
    fun optString(index: Int, fallback: String = ""): String
    fun getString(index: Int): String
    fun put(value: Any?): JsonArray
    override fun toString(): String
}

expect object Json {
    /** 对应 org.json.JSONObject.NULL 的哨兵值 */
    val NULL: Any

    fun obj(): JsonObject
    fun obj(json: String): JsonObject
    fun arr(): JsonArray
    fun arr(json: String): JsonArray
}
