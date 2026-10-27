package com.petcompanion.notification

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton store สำหรับ in-app notifications
 * - เก็บใน SharedPreferences (คงอยู่แม้ปิด app)
 * - Expose StateFlow เพื่อให้ Composable observe แบบ reactive
 * - ต้องเรียก init(context) ใน Application.onCreate หรือ MainActivity.onCreate
 */
object NotificationStore {

    private const val PREFS_NAME = "notif_store"
    private const val KEY_LIST   = "notif_list"
    private const val MAX_NOTIFS = 50  // จำกัดไม่ให้ล้นหน่วยความจำ

    private val _notifications = MutableStateFlow<List<InAppNotification>>(emptyList())
    val notifications: StateFlow<List<InAppNotification>> = _notifications.asStateFlow()

    /** จำนวนที่ยังไม่ได้อ่าน (ใช้แสดง badge) */
    val unreadCount: Int get() = _notifications.value.count { !it.isRead }

    // ── Init ─────────────────────────────────────────────────────────────────

    /** เรียก 1 ครั้งเมื่อแอปเริ่มต้น — โหลดรายการจาก SharedPreferences */
    fun init(context: Context) {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIST, "[]") ?: "[]"
        _notifications.value = parseList(json)
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * เพิ่มการแจ้งเตือนใหม่
     * @param fireSystem ถ้า true → ยิง Android system notification ด้วย (ขึ้นสถานะบาร์ + icon badge)
     */
    @Synchronized
    fun add(context: Context, notif: InAppNotification, fireSystem: Boolean = true) {
        val current = _notifications.value.toMutableList()
        // ป้องกัน duplicate (id เดียวกัน = แทนที่ของเก่า)
        current.removeAll { it.id == notif.id }
        current.add(0, notif)
        val trimmed = current.take(MAX_NOTIFS)
        _notifications.value = trimmed
        save(context, trimmed)
        if (fireSystem) {
            try {
                NotificationHelper.showNow(context, notif.id.hashCode(), notif.title, notif.body)
            } catch (_: Exception) { /* ไม่มีสิทธิ์แจ้งเตือน หรือ context หมดอายุ */ }
        }
    }

    /** ทำเครื่องหมาย "อ่านทั้งหมด" (เรียกเมื่อผู้ใช้เปิดหน้า Notif) */
    fun markAllRead(context: Context) {
        val updated = _notifications.value.map { it.copy(isRead = true) }
        _notifications.value = updated
        save(context, updated)
    }

    /** ลบการแจ้งเตือนทั้งหมด */
    fun clear(context: Context) {
        _notifications.value = emptyList()
        save(context, emptyList())
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun save(context: Context, list: List<InAppNotification>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LIST, Gson().toJson(list)).apply()
    }

    private fun parseList(json: String): List<InAppNotification> {
        return try {
            val type = object : TypeToken<List<InAppNotification>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
