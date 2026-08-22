package com.wenyan.app.json

/**
 * O4: KMP 公共 JSON 抽象。
 * commonMain 不依赖 org.json；实际实现由 androidMain（android.jar org.json）
 * 与 jvmMain（外部 org.json）分别提供。只覆盖项目用到的子集。
 */
interface JsonObject {
    fun put(key: String, value: Any?): JsonObject
    fun optString(key: String, fallback: String = ""): String

    /**
     * M7 修复：显式 null → null（不做 "null" 字面量兜底）。
     * Android org.json 的 optString 对 {"text":null} 返回字面量 "null"（非空、通过校验），
     * 桌面端 org.json 正确返回 fallback——平台分歧导致垃圾事实被持久化进长期记忆表。
     * 读模型产出的可选字符串字段一律走本方法；isNull 预检后两端行为一致。
     */
    fun optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key, "")
    fun optInt(key: String, fallback: Int = 0): Int
    fun optLong(key: String, fallback: Long = 0): Long
    fun optBoolean(key: String, fallback: Boolean = false): Boolean
    fun optJSONObject(key: String): JsonObject?
    fun optJSONArray(key: String): JsonArray?
    fun getJSONObject(key: String): JsonObject
    fun has(key: String): Boolean

    /** M9/L9: 取任意标量（字符串/数字/布尔）的字符串形式；缺失或显式 null → null */
    fun optScalarString(key: String): String?

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
