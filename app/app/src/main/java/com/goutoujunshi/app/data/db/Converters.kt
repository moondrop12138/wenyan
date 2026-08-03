package com.goutoujunshi.app.data.db

import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * JSON 字段 TypeConverter（timeline / refDocs）
 * 用平台 org.json 序列化，不新增第三方依赖（db-schema §1）
 */
class Converters {
    @TypeConverter
    fun fromJsonArray(value: String): JSONArray = JSONArray(value)

    @TypeConverter
    fun toJsonArray(value: JSONArray): String = value.toString()

    @TypeConverter
    fun fromStringList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        val arr = JSONArray(value)
        return buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    }

    @TypeConverter
    fun toStringList(value: List<String>): String =
        JSONArray(value).toString()
}
