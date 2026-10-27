package com.petcompanion.data.local

import android.util.Base64

/**
 * เข้ารหัสเนื้อหาข้อสอบล็อกด้วย deviceId
 * — XOR + Base64: เร็ว, ไม่ต้องพึ่ง external lib
 * — ถ้า copy DB ไปเครื่องอื่น → decrypt จะได้ข้อความเพี้ยน
 * — ไม่ใช่ AES-grade แต่ป้องกัน copy-paste DB ได้ดี
 */
object QuizCrypto {

    private const val SALT = "qgrowth_locked_2025"

    fun encrypt(text: String, deviceId: String): String {
        val key = buildKey(deviceId)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val result = ByteArray(textBytes.size) { i ->
            (textBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String, deviceId: String): String {
        return try {
            val key = buildKey(deviceId)
            val encodedBytes = Base64.decode(encoded, Base64.NO_WRAP)
            val result = ByteArray(encodedBytes.size) { i ->
                (encodedBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            String(result, Charsets.UTF_8)
        } catch (_: Exception) {
            encoded // fallback: คืนค่าเดิมถ้า decode ไม่ได้
        }
    }

    fun encryptList(list: List<String>, deviceId: String): List<String> =
        list.map { encrypt(it, deviceId) }

    fun decryptList(list: List<String>, deviceId: String): List<String> =
        list.map { decrypt(it, deviceId) }

    private fun buildKey(deviceId: String): ByteArray {
        val raw = "$deviceId$SALT"
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return hash // 32 bytes
    }
}
