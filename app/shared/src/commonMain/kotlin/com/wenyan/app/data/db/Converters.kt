package com.wenyan.app.data.db

import androidx.room.TypeConverter
import com.wenyan.app.json.Json
import com.wenyan.app.json.JsonArray

/**
 * JSON 字段 TypeConverter（timeline / refDocs）
 * 用平台 org.json 序列化，不新增第三方依赖（db-schema §1）
 */
class Converters {
    @TypeConverter
    fun fromJsonArray(value: String): JsonArray = try {
        Json.arr(value)
    } catch (e: Exception) {
        // L4: 非法 JSON 返回空数组，不抛异常（防历史脏数据崩溃）
        Json.arr()
    }

    @TypeConverter
    fun toJsonArray(value: JsonArray): String = value.toString()

    @TypeConverter
    fun fromStringList(value: String): List<String> {
        // L3 修复：与兄弟方法 fromJsonArray 对齐——包 try/catch 防历史脏数据在 Room
        // 读取路径抛异常（当前无 List<String> 实体字段，属潜伏陷阱，提前拆除）。
        if (value.isEmpty()) return emptyList()
        val arr = try {
            Json.arr(value)
        } catch (e: Exception) {
            return emptyList()
        }
        return buildList {
            for (i in 0 until arr.length()) add(arr.optString(i, ""))
        }
    }

    @TypeConverter
    fun toStringList(value: List<String>): String {
        val arr = Json.arr()
        value.forEach { arr.put(it) }
        return arr.toString()
    }
}
