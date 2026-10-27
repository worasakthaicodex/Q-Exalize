package com.petcompanion.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

fun copyImageToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        // 1. เปิดไฟล์ต้นฉบับจาก Gallery
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null

        // 2. สร้างไฟล์ใหม่ใน "พื้นที่ส่วนตัว" ของแอป (Internal Storage)
        val fileName = "profile_${System.currentTimeMillis()}.jpg"
        val file = File(context.filesDir, fileName)
        val outputStream = FileOutputStream(file)

        // 3. ก๊อปปี้ข้อมูล (ดูดไฟล์)
        inputStream.copyTo(outputStream)

        // 4. ปิดการเชื่อมต่อ
        inputStream.close()
        outputStream.close()

        // 5. ส่ง Path ของไฟล์ใหม่กลับไป เพื่อเอาไปเซฟลง Room Database
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}