package com.petcompanion.data.mapper

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StringListConverter {
    private val gson = Gson()

    // ==========================================
    // 📍 ของเดิมของท่าน (แปลง List<String>)
    // ==========================================
    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        if (list == null) return null
        return gson.toJson(list)
    }

    @TypeConverter
    fun toStringList(data: String?): List<String>? {
        if (data == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(data, listType)
    }

    // ==========================================
    // 📍 ของใหม่ (แปลง Map<Int, Int> สำหรับเก็บคำตอบ User)
    // ==========================================
    @TypeConverter
    fun fromAnswerMap(map: Map<Int, Int>?): String {
        return gson.toJson(map ?: emptyMap<Int, Int>())
    }

    @TypeConverter
    fun toAnswerMap(json: String?): Map<Int, Int> {
        if (json.isNullOrEmpty()) return emptyMap()
        val type = object : TypeToken<Map<Int, Int>>() {}.type
        return gson.fromJson(json, type)
    }
}