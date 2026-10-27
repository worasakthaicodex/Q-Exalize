package com.petcompanion.notification

import java.util.UUID

data class InAppNotification(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) {
    companion object {
        const val TYPE_HW_SUBMITTED   = "hw_submitted"    // ครู: มีนักเรียนส่งงานใหม่
        const val TYPE_HW_GRADED      = "hw_graded"       // นักเรียน: งานถูกตรวจแล้ว
        const val TYPE_ATT_OPENED     = "att_opened"      // นักเรียน: ครูเปิดเซสชันเช็คชื่อ
        const val TYPE_ATT_CHECKED    = "att_checked"     // ครู: นักเรียนเช็คชื่อเข้ามา
        const val TYPE_ROOM_APPROVED  = "room_approved"   // นักเรียน: ได้รับอนุญาตเข้าห้องแล้ว
        const val TYPE_EXAM_PUBLISHED = "exam_published"  // นักเรียน: ครู publish ข้อสอบแล้ว
    }

    val emoji: String get() = when (type) {
        TYPE_HW_SUBMITTED   -> "📝"
        TYPE_HW_GRADED      -> "✅"
        TYPE_ATT_OPENED     -> "🟢"
        TYPE_ATT_CHECKED    -> "☑️"
        TYPE_ROOM_APPROVED  -> "🎉"
        TYPE_EXAM_PUBLISHED -> "📋"
        else                -> "🔔"
    }
}
