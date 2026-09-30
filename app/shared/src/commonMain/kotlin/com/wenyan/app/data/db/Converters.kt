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
}
