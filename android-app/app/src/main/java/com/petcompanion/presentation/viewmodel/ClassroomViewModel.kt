package com.petcompanion.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.petcompanion.data.firebase.*
import com.petcompanion.notification.InAppNotification
import com.petcompanion.notification.NotificationHelper
import com.petcompanion.notification.NotificationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ClassroomViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs   = application.getSharedPreferences("classroom_prefs", Context.MODE_PRIVATE)
    private val manager = FirebaseManager.getInstance(application)
    private val repo    = ClassroomRepository(manager)

    // ── Room ID ────────────────────────────────────────────────────────────
    private val _roomId = MutableStateFlow(prefs.getString("room_id", "") ?: "")
    val roomId: StateFlow<String> = _roomId.asStateFlow()

    fun saveRoomId(id: String) {
        prefs.edit().putString("room_id", id.trim()).apply()
        _roomId.value = id.trim()
    }

    // ── Teacher UID — ครู: จาก Firebase Auth / นักเรียน: จาก prefs ที่บันทึกตอนสแกน QR ──
    private val _teacherUid = MutableStateFlow(
        manager.auth.currentUser?.uid
            ?: manager.getSavedStudentTeacherUid()
            ?: ""
    )
    val teacherUid: StateFlow<String> = _teacherUid.asStateFlow()

    init {
        // sync repo.teacherUid ทุกครั้งที่ _teacherUid เปลี่ยน
        viewModelScope.launch {
            _teacherUid.collect { uid -> repo.teacherUid = uid }
        }
        // ติดตาม auth state — ถ้าครู login/logout จะอัปเดต teacherUid อัตโนมัติ
        viewModelScope.launch {
            manager.currentUser.collect { user ->
                if (user != null) _teacherUid.value = user.uid
            }
        }
        // Firebase ใช้ google-services.json ตายตัว ไม่ต้อง reconnect
        // ── In-App Notification Triggers ───────────────────────────────────
        // ❗ ต้องใช้ Dispatchers.Main (ไม่ใช่ .immediate) อย่างชัดเจน
        //
        // viewModelScope ใช้ Dispatchers.Main.immediate เป็น default:
        //   → ถ้าอยู่บน main thread อยู่แล้ว coroutine จะ run INLINE ทันที
        //   → ทำให้ startNotificationWatchers() ถูกเรียกก่อน property
        //      allSubmissions / attSessions / myStudentProfile ถูก init → NPE
        //
        // Dispatchers.Main (ไม่ใช่ .immediate):
        //   → isDispatchNeeded() = true เสมอ → post เข้า Android Handler queue
        //   → รับประกันว่า constructor + property initializers ทั้งหมดเสร็จ
        //      ก่อนที่ coroutine body จะเริ่มทำงาน
        viewModelScope.launch(Dispatchers.Main) { startNotificationWatchers() }
    }

    /**
     * เริ่ม coroutines ที่คอย watch Firestore flows เพื่อสร้าง in-app notifications
     *
     * วิธีป้องกัน false notifications จาก "initial load":
     * - submissions: ใช้ submittedAt > vmStartTime (เฉพาะส่งหลังแอปเปิด)
     * - att sessions: ใช้ openedAt > vmStartTime (เฉพาะเปิดหลังแอปเปิด)
     * - graded: ใช้ SharedPreferences เก็บ "already notified" IDs → ทำงานได้แม้ restart app
     * - approved: เปรียบเทียบ prevStatus → ถูกต้องอยู่แล้ว
     */
    private fun startNotificationWatchers() {
        val appCtx   = getApplication<Application>()
        val vmStart  = System.currentTimeMillis()   // timestamp ที่ ViewModel เริ่มทำงาน

        // ── 1. ครู: มีนักเรียนส่งงานใหม่ ──────────────────────────────────
        // ใช้ submittedAt > vmStart → ไม่แจ้งเตือนงานที่ส่งมาก่อนเปิดแอป
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var prevIds = emptySet<String>()
                allSubmissions.collect { list ->
                    try {
                        if (!manager.isTeacher) { prevIds = list.map { it.id }.toSet(); return@collect }
                        val newSubs = list.filter { it.id !in prevIds && it.submittedAt > vmStart }
                        newSubs.forEach { sub ->
                            NotificationStore.add(appCtx, InAppNotification(
                                id    = "hw_sub_${sub.id}",
                                type  = InAppNotification.TYPE_HW_SUBMITTED,
                                title = "ส่งการบ้านใหม่",
                                body  = "${sub.studentName} ส่ง \"${sub.hwTitle}\""
                            ))
                        }
                        prevIds = list.map { it.id }.toSet()
                    } catch (_: Exception) { /* ไม่ให้ exception จาก emit หยุด watcher */ }
                }
            } catch (_: Exception) { /* coroutine ถูก cancel หรือ flow error */ }
        }

        // ── 2. นักเรียน: งานถูกตรวจแล้ว (graded) ─────────────────────────
        // ใช้ SharedPreferences เก็บ IDs ที่แจ้งไปแล้ว → คงอยู่แม้ restart app
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prefKey = "notif_graded_ids_$deviceId"
                val notifiedIds = try {
                    prefs.getStringSet(prefKey, null)?.toMutableSet() ?: mutableSetOf()
                } catch (_: Exception) { mutableSetOf() }
                allSubmissions.collect { list ->
                    try {
                        if (manager.isTeacher) return@collect
                        val toNotify = list.filter {
                            it.deviceId == deviceId &&
                            it.status == HomeworkSubmission.STATUS_GRADED &&
                            it.id !in notifiedIds
                        }
                        toNotify.forEach { sub ->
                            NotificationStore.add(appCtx, InAppNotification(
                                id    = "hw_grade_${sub.id}",
                                type  = InAppNotification.TYPE_HW_GRADED,
                                title = "งานถูกตรวจแล้ว",
                                body  = "\"${sub.hwTitle}\" ได้ ${sub.grade.ifBlank { "—" }}" +
                                        if (sub.feedback.isNotBlank()) "\nครู: ${sub.feedback}" else ""
                            ))
                            notifiedIds.add(sub.id)
                        }
                        if (toNotify.isNotEmpty()) {
                            prefs.edit().putStringSet(prefKey, notifiedIds.toSet()).apply()
                        }
                    } catch (_: Exception) { /* ไม่ให้ exception จาก emit หยุด watcher */ }
                }
            } catch (_: Exception) { }
        }

        // ── 3. นักเรียน: ครูเปิดเซสชันเช็คชื่อใหม่ ────────────────────────
        // ใช้ openedAt > vmStart → ไม่แจ้งเตือนเซสชันที่เปิดมาก่อนแอปเริ่ม
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var prevOpenIds = emptySet<String>()
                attSessions.collect { list ->
                    try {
                        val openNow = list.filter { it.isOpen }
                        if (manager.isTeacher) {
                            prevOpenIds = openNow.map { it.id }.toSet(); return@collect
                        }
                        val newOpen = openNow.filter { it.id !in prevOpenIds && it.openedAt > vmStart }
                        newOpen.forEach { session ->
                            NotificationStore.add(appCtx, InAppNotification(
                                id    = "att_open_${session.id}",
                                type  = InAppNotification.TYPE_ATT_OPENED,
                                title = "เปิดเช็คชื่อแล้ว!",
                                body  = "\"${session.label.ifBlank { "เซสชัน" }}\" — กดเข้าไปเช็คชื่อเดี๋ยวนี้"
                            ))
                        }
                        prevOpenIds = openNow.map { it.id }.toSet()
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }

        // ── 4. นักเรียน: ได้รับอนุญาตเข้าห้องแล้ว ─────────────────────────
        // เปรียบเทียบ prevStatus: pending → approved เท่านั้นที่แจ้ง
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var prevStatus: String? = null
                myStudentProfile.collect { profile ->
                    try {
                        val newStatus = profile?.status
                        if (prevStatus == StudentProfile.STATUS_PENDING &&
                            newStatus == StudentProfile.STATUS_APPROVED &&
                            profile != null) {
                            NotificationStore.add(appCtx, InAppNotification(
                                id    = "approved_${_roomId.value}_${profile.deviceId}",
                                type  = InAppNotification.TYPE_ROOM_APPROVED,
                                title = "เข้าห้องเรียนได้แล้ว!",
                                body  = "ครูอนุมัติให้ ${profile.name} เข้าห้องเรียนแล้ว"
                            ))
                        }
                        prevStatus = newStatus
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }

        // ── 5. นักเรียน: ครู publish ข้อสอบออนไลน์ ────────────────────────
        // ตรวจเฉพาะ exam ที่ publish ใหม่หลังแอปเปิด (prevPublishedIds = state ก่อนหน้า)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var prevPublishedIds = emptySet<String>()
                exams.collect { list ->
                    try {
                        val publishedNow = list.filter { it.isPublished }
                        if (manager.isTeacher) {
                            // ครู: ไม่ต้องแจ้ง แต่ track state
                            prevPublishedIds = publishedNow.map { it.id }.toSet()
                            return@collect
                        }
                        val newlyPublished = publishedNow.filter { it.id !in prevPublishedIds }
                        newlyPublished.forEach { exam ->
                            val deadlineText = if (exam.endTime > 0L) {
                                val fmt = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("th","TH"))
                                "\nหมดเวลา ${fmt.format(java.util.Date(exam.endTime))}"
                            } else ""
                            NotificationStore.add(appCtx, InAppNotification(
                                id    = "exam_pub_${exam.id}",
                                type  = InAppNotification.TYPE_EXAM_PUBLISHED,
                                title = "เริ่มการสอบแล้ว!",
                                body  = "\"${exam.title}\" — กดเข้าไปทำข้อสอบเดี๋ยวนี้$deadlineText"
                            ))
                        }
                        prevPublishedIds = publishedNow.map { it.id }.toSet()
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }
    }

    // ── ข้อมูลห้องเรียน ───────────────────────────────────────────────────
    private val _room = MutableStateFlow<ClassRoom?>(null)
    val room: StateFlow<ClassRoom?> = _room.asStateFlow()

    private val _roomError = MutableStateFlow<String?>(null)
    val roomError: StateFlow<String?> = _roomError.asStateFlow()

    fun loadRoom() {
        val id = _roomId.value
        if (id.isBlank()) { _roomError.value = "ไม่มี Room ID"; return }
        viewModelScope.launch {
            val result = repo.getRoom(id)
            if (result.isSuccess) {
                _room.value = result.getOrNull()
                _roomError.value = null
            } else {
                _roomError.value = friendlyError(result.exceptionOrNull()?.message)
            }
        }
    }

    // ── Auth state ────────────────────────────────────────────────────────

    /** true = ครู (Firebase Auth login แล้ว) — reactive กับ auth state */
    val isTeacherMode: StateFlow<Boolean> = manager.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, manager.auth.currentUser != null)

    // ── Realtime flows ────────────────────────────────────────────────────

    // helper: combine teacherUid + roomId → emit pair ทุกครั้งที่เปลี่ยน
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> roomFlow(fn: (uid: String, id: String) -> Flow<T>, default: T): Flow<T> =
        combine(_teacherUid, _roomId) { uid, id -> uid to id }
            .flatMapLatest { (uid, id) ->
                if (uid.isBlank() || id.isBlank()) flowOf(default) else fn(uid, id)
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val events: StateFlow<List<ScheduleEvent>> = roomFlow({ _, id -> repo.getEventsFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val homework: StateFlow<List<HomeworkItem>> = roomFlow({ _, id -> repo.getHomeworkFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val attendance: StateFlow<List<AttendanceRecord>> = roomFlow({ _, id -> repo.getAttendanceFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val grades: StateFlow<List<GradeRecord>> = roomFlow({ _, id -> repo.getGradesFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val files: StateFlow<List<ClassFile>> = roomFlow({ _, id -> repo.getFilesFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val arenaEntries: StateFlow<List<ArenaEntry>> = roomFlow({ _, id -> repo.getArenaFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val students: StateFlow<List<StudentProfile>> = roomFlow({ _, id -> repo.getStudentsFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** โปรไฟล์นักเรียนของเครื่องนี้ (ถ้าลงทะเบียนแล้ว) */
    val myStudentProfile: StateFlow<StudentProfile?> = students
        .map { list -> list.find { it.deviceId == deviceId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * true = มีสิทธิ์ครู: server owner (isTeacherMode) หรือ
     *        นักเรียนที่ได้รับ approve ให้เป็นครู (co-teacher)
     */
    val isEffectiveTeacher: StateFlow<Boolean> = combine(isTeacherMode, myStudentProfile) { isTeacher, profile ->
        isTeacher || (profile?.isApproved == true && profile.role == StudentProfile.ROLE_TEACHER)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** สมาชิกที่รอการอนุมัติ */
    val pendingStudents: StateFlow<List<StudentProfile>> = students
        .map { list -> list.filter { it.status == StudentProfile.STATUS_PENDING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** สมาชิกที่อนุมัติแล้ว */
    val approvedStudents: StateFlow<List<StudentProfile>> = students
        .map { list -> list.filter { it.status == StudentProfile.STATUS_APPROVED } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Firebase Storage พร้อมใช้งานหรือไม่ */
    val hasStorage: Boolean get() = manager.storage() != null

    // ══════════════════════════════════════════════════════════════════════
    // Student Multi-Room — นักเรียนเข้าได้หลายห้อง (เก็บ Set<roomId> ใน prefs)
    // ══════════════════════════════════════════════════════════════════════

    private val _studentRoomIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet("student_room_ids", null)?.toSet() ?: emptySet()
    )
    val studentRoomIds: StateFlow<Set<String>> = _studentRoomIds.asStateFlow()

    private val _studentRoomsInfo = MutableStateFlow<List<StudentRoomEntry>>(emptyList())
    val studentRoomsInfo: StateFlow<List<StudentRoomEntry>> = _studentRoomsInfo.asStateFlow()

    /** เพิ่มห้องเรียนเข้า list ของนักเรียน */
    fun addStudentRoom(id: String) {
        val trimmed = id.trim()
        if (trimmed.isBlank()) return
        val new = _studentRoomIds.value + trimmed
        _studentRoomIds.value = new
        prefs.edit().putStringSet("student_room_ids", new).apply()
    }

    private fun removeStudentRoomEntry(id: String) {
        val new = _studentRoomIds.value - id
        _studentRoomIds.value = new
        prefs.edit().putStringSet("student_room_ids", new).apply()
    }

    /** โหลดข้อมูลห้อง + status ของนักเรียนเองในแต่ละห้อง (เรียกเมื่อเปิด room list) */
    fun loadStudentRooms() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = _studentRoomIds.value
            if (ids.isEmpty() || !manager.isConnected.value) {
                _studentRoomsInfo.value = emptyList()
                return@launch
            }
            val entries = ids.mapNotNull { id ->
                val room    = repo.getRoom(id).getOrNull() ?: return@mapNotNull null
                val profile = repo.getStudentOnce(id, deviceId)
                StudentRoomEntry(room = room, myProfile = profile)
            }
            _studentRoomsInfo.value = entries
        }
    }

    // ── รายการห้องทั้งหมดของครู (real-time) ──────────────────────────────
    @OptIn(ExperimentalCoroutinesApi::class)
    val teacherRooms: StateFlow<List<ClassRoom>> = _teacherUid
        .flatMapLatest { uid ->
            if (uid.isBlank() || !manager.isTeacher) flowOf(emptyList())
            else { repo.teacherUid = uid; repo.getRoomsFlow() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    // ── รวมทุกห้อง (สำหรับหน้าแรก) ────────────────────────────────────────
    // ครู → ทุกห้องของตัวเอง / นักเรียน → ทุกห้องที่เข้าร่วม
    @OptIn(ExperimentalCoroutinesApi::class)
    private val allRoomIds: StateFlow<List<String>> =
        combine(isTeacherMode, teacherRooms, _studentRoomIds) { isTeacher, tRooms, sRooms ->
            if (isTeacher) tRooms.map { it.id } else sRooms.toList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** ตารางงานรวมทุกห้อง — เรียงตามเวลา */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allEvents: StateFlow<List<ScheduleEvent>> =
        combine(_teacherUid, allRoomIds) { uid, ids -> uid to ids }
            .flatMapLatest { (uid, ids) ->
                if (uid.isBlank() || ids.isEmpty()) flowOf(emptyList())
                else {
                    repo.teacherUid = uid
                    combine(ids.map { repo.getEventsFlow(it) }) { arr -> arr.toList().flatten() }
                }
            }
            .map { list -> list.sortedBy { it.dateTime } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    /** การบ้านรวมทุกห้อง พร้อม roomId ของแต่ละชิ้น — เรียงตามกำหนดส่ง */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val allHomeworkWithRoom: StateFlow<List<Pair<HomeworkItem, String>>> =
        combine(_teacherUid, allRoomIds) { uid, ids -> uid to ids }
            .flatMapLatest { (uid, ids) ->
                if (uid.isBlank() || ids.isEmpty()) flowOf(emptyList())
                else {
                    repo.teacherUid = uid
                    combine(ids.map { rid ->
                        repo.getHomeworkFlow(rid).map { list -> list.map { it to rid } }
                    }) { arr -> arr.toList().flatten() }
                }
            }
            .map { list -> list.sortedBy { it.first.dueDate } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    val allHomework: StateFlow<List<HomeworkItem>> = allHomeworkWithRoom
        .map { list -> list.map { it.first } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    /** map: homeworkId → roomId (ใช้ตอนนักเรียนส่งงานจากหน้ารวม) */
    val homeworkRoomMap: StateFlow<Map<String, String>> = allHomeworkWithRoom
        .map { list -> list.associate { it.first.id to it.second } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyMap())

    /** ครูเลือกห้องจากรายการ */
    fun selectRoom(id: String) {
        saveRoomId(id)
        loadRoom()
    }

    /** ครูออกจากห้องที่เลือก → กลับไปหน้ารายการห้อง */
    fun clearSelectedRoom() {
        prefs.edit().remove("room_id").apply()
        _roomId.value   = ""
        _room.value     = null
        _roomError.value = null
    }

    // ── Connection state ───────────────────────────────────────────────────

    /** Firebase เชื่อมต่อตลอด (hardcoded google-services.json) */
    val isFirebaseConnected: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    // ── Error handling ─────────────────────────────────────────────────────
    private val _teacherError = MutableStateFlow<String?>(null)
    val teacherError: StateFlow<String?> = _teacherError.asStateFlow()
    fun clearTeacherError() { _teacherError.value = null }

    private fun friendlyError(raw: String?): String {
        if (raw == null) return "เกิดข้อผิดพลาด"
        return when {
            raw.contains("PERMISSION_DENIED") ->
                "ไม่มีสิทธิ์: กรุณาตั้ง Firestore Security Rules ให้ allow read/write"
            raw.contains("NOT_FOUND") ->
                "ไม่พบห้องเรียนนี้ใน Firebase"
            raw.contains("UNAVAILABLE") || raw.contains("Unable to resolve host") ->
                "ไม่มีอินเทอร์เน็ต"
            else -> raw
        }
    }

    // ── นักเรียน connect จาก QR ───────────────────────────────────────────
    /**
     * เรียกเมื่อสแกน QR สำเร็จ — ข้อมูลใน QR:
     * "PCR|{teacherUid}|{roomId}"
     */
    fun connectFromQr(teacherUid: String, roomId: String) {
        manager.saveStudentRoom(teacherUid, roomId)
        _teacherUid.value = teacherUid
        repo.teacherUid   = teacherUid
        addStudentRoom(roomId)
        saveRoomId(roomId)
    }

    /** ล้าง student room data */
    fun clearStudentConfig() {
        manager.clearStudentRoom()
        _teacherUid.value = manager.auth.currentUser?.uid ?: ""
    }

    // ── Teacher: ห้องเรียน ────────────────────────────────────────────────

    /**
     * สร้างห้องใหม่ — ไม่เลือกห้องอัตโนมัติ (ครูยังอยู่ในโหมด list)
     * แก้ปัญหา: ห้องที่ 2+ สร้างไม่ได้เพราะ list mode หายไป
     */
    fun createRoom(roomId: String, name: String, teacherName: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repo.createOrUpdateRoom(roomId, ClassRoom(
                id = roomId, name = name, teacherName = teacherName, description = description
            ))
            if (result.isFailure) {
                _teacherError.value = friendlyError(result.exceptionOrNull()?.message)
            }
            // ไม่เรียก saveRoomId → ครูยังอยู่ในโหมดรายการห้อง (FAB + ยังคงแสดง)
        }
    }

    /** แก้ไขข้อมูลห้องที่เลือกอยู่ (ไม่เปลี่ยนโหมด) */
    fun createOrUpdateRoom(roomId: String, name: String, teacherName: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repo.createOrUpdateRoom(roomId, ClassRoom(
                id = roomId, name = name, teacherName = teacherName, description = description
            ))
            if (result.isFailure) {
                _teacherError.value = friendlyError(result.exceptionOrNull()?.message)
            } else {
                loadRoom()
            }
        }
    }

    /**
     * ลบห้องเรียน — เฉพาะ server owner (isTeacherMode) เท่านั้น
     * หลังลบแล้ว → กลับไปโหมด list
     */
    fun deleteRoom() {
        val id = _roomId.value; if (id.isBlank()) return
        if (!manager.isTeacher) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.deleteRoom(id)
            if (r.isSuccess) clearSelectedRoom()
            else _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    /** อนุมัติสมาชิก + กำหนด role */
    fun approveStudent(deviceId: String, role: String = StudentProfile.ROLE_STUDENT) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.approveStudent(id, deviceId, role)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    /**
     * ย้ายตัวตนจากเครื่องเก่าไปเครื่องใหม่ (เครื่องเก่าพัง)
     * oldDeviceId = สมาชิกที่อนุมัติแล้ว / newDeviceId = เครื่องใหม่ที่รออนุมัติ
     */
    fun transferStudentDevice(oldDeviceId: String, newDeviceId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.transferStudentDevice(id, oldDeviceId, newDeviceId)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    /** ลบสมาชิกออกจากห้อง (ครูทำ) */
    fun removeStudent(deviceId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.removeStudent(id, deviceId)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    /**
     * Auto-register นักเรียนในห้องปัจจุบัน (เรียกเมื่อมีชื่อแล้วแต่ยังไม่มี profile ใน Firestore)
     * ไม่ทำอะไรถ้า myStudentProfile != null (มีอยู่แล้ว — ป้องกัน status reset)
     */
    fun autoRegisterStudent() {
        val name = _studentName.value; if (name.isBlank()) return
        val id   = _roomId.value;     if (id.isBlank()) return
        if (myStudentProfile.value != null) return   // มีอยู่แล้ว — ไม่ register ซ้ำ
        viewModelScope.launch(Dispatchers.IO) {
            repo.registerStudent(id, StudentProfile(
                deviceId = deviceId,
                name     = name,
                joinedAt = System.currentTimeMillis()
            ))
        }
    }

    /**
     * นักเรียน / co-teacher ออกจากห้องด้วยตัวเอง
     * — ลบ profile ตัวเองใน Firestore แล้วล้าง local room ID
     */
    fun leaveRoom() {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.removeStudent(id, deviceId)  // ลบ Firestore doc ของตัวเอง
            removeStudentRoomEntry(id)         // ลบออกจาก local room list
            clearSelectedRoom()               // กลับไปหน้า list
        }
    }

    // ── Teacher: ตารางงาน ─────────────────────────────────────────────────
    fun addEvent(event: ScheduleEvent) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val r = repo.addEvent(id, event)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun deleteEvent(eventId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repo.deleteEvent(id, eventId) }
    }

    // ── Teacher: การบ้าน ──────────────────────────────────────────────────
    fun addHomework(hw: HomeworkItem) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val r = repo.addHomework(id, hw)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun deleteHomework(hwId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repo.deleteHomework(id, hwId) }
    }

    // ── Teacher: เช็คชื่อ ─────────────────────────────────────────────────
    fun setAttendance(record: AttendanceRecord) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val r = repo.setAttendance(id, record)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun deleteAttendance(date: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repo.deleteAttendance(id, date) }
    }

    // ── Teacher: คะแนน ────────────────────────────────────────────────────
    fun addGrade(grade: GradeRecord) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val r = repo.addGrade(id, grade)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun deleteGrade(gradeId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repo.deleteGrade(id, gradeId) }
    }

    // ── Teacher: ไฟล์ ─────────────────────────────────────────────────────
    fun addFile(file: ClassFile) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val r = repo.addFile(id, file)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun deleteFile(fileId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repo.deleteFile(id, fileId) }
    }

    // ── Teacher: Arena ────────────────────────────────────────────────────
    fun addArenaEntry(entry: ArenaEntry) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val r = repo.addArenaEntry(id, entry)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun deleteArenaEntry(entryId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repo.deleteArenaEntry(id, entryId) }
    }

    fun clearArena() {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val r = repo.clearArena(id)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    // ── Notification helpers ───────────────────────────────────────────────
    private val ctx get() = getApplication<Application>()

    fun scheduleEventReminder(event: ScheduleEvent) {
        NotificationHelper.scheduleReminder(
            context          = ctx,
            eventEpochMillis = event.dateTime,
            beforeMinutes    = event.notifyBeforeMinutes,
            title            = "📅 ${event.title}",
            body             = "อีก ${event.notifyBeforeMinutes} นาที: ${event.description.ifBlank { event.title }}",
            tagId            = "event_${event.id}"
        )
    }

    fun scheduleHomeworkReminder(hw: HomeworkItem) {
        NotificationHelper.scheduleReminder(
            context          = ctx,
            eventEpochMillis = hw.dueDate,
            beforeMinutes    = hw.notifyBeforeMinutes,
            title            = "📝 กำหนดส่ง: ${hw.title}",
            body             = "เหลืออีก ${hw.notifyBeforeMinutes} นาที ถึงกำหนดส่งการบ้าน",
            tagId            = "hw_${hw.id}"
        )
    }

    fun scheduleAttendanceReminder() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        NotificationHelper.scheduleReminder(
            context          = ctx,
            eventEpochMillis = cal.timeInMillis + 30 * 60_000L,
            beforeMinutes    = 30,
            title            = "🏫 เช็คชื่อวันนี้",
            body             = "อย่าลืมตรวจสอบการเข้าเรียนของคุณ",
            tagId            = "attendance_daily"
        )
    }

    fun markHomeworkDone(hwId: String, done: Boolean) {
        prefs.edit().putBoolean("hw_done_$hwId", done).apply()
    }

    fun isHomeworkDone(hwId: String): Boolean =
        prefs.getBoolean("hw_done_$hwId", false)

    // ── วันนี้ในรูปแบบ yyyy-MM-dd ─────────────────────────────────────────
    fun todayString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    // ══════════════════════════════════════════════════════════════════════
    // ชื่อนักเรียน (เก็บใน prefs)
    // ══════════════════════════════════════════════════════════════════════
    private val _studentName = MutableStateFlow(prefs.getString("student_name", "") ?: "")
    val studentName: StateFlow<String> = _studentName.asStateFlow()

    fun saveStudentName(name: String, avatarBase64: String = "") {
        val trimmed = name.trim()
        prefs.edit().putString("student_name", trimmed).apply()
        _studentName.value = trimmed
        // ลงทะเบียนนักเรียนใน Firestore (ห้องที่เชื่อมต่ออยู่)
        val roomId = _roomId.value
        if (roomId.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repo.registerStudent(roomId, StudentProfile(
                    deviceId     = deviceId,
                    name         = trimmed,
                    avatarBase64 = avatarBase64,
                    joinedAt     = System.currentTimeMillis()
                ))
            }
        }
    }

    // ── Device ID (UUID — 1 เครื่อง = 1 identity นักเรียน) ────────────────
    val deviceId: String = prefs.getString("device_id", null)
        ?: UUID.randomUUID().toString().also { uuid ->
            prefs.edit().putString("device_id", uuid).apply()
        }

    // ══════════════════════════════════════════════════════════════════════
    // Homework Submissions — real-time (ทุก submission ในห้อง)
    // ══════════════════════════════════════════════════════════════════════
    @OptIn(ExperimentalCoroutinesApi::class)
    val allSubmissions: StateFlow<List<HomeworkSubmission>> = roomFlow({ _, id -> repo.getAllSubmissionsFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    /** นักเรียนส่งงาน */
    fun submitHomework(hwId: String, hwTitle: String, answer: String) {
        val id   = _roomId.value; if (id.isBlank()) return
        val name = _studentName.value; if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.submitHomework(id,
                HomeworkSubmission(hwId = hwId, hwTitle = hwTitle, studentName = name,
                    deviceId = deviceId, answer = answer)
            )
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    /** ครูตรวจงาน */
    fun gradeSubmission(submissionId: String, grade: String, feedback: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.gradeSubmission(id, submissionId, grade, feedback)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Online Exams — real-time
    // ══════════════════════════════════════════════════════════════════════

    @OptIn(ExperimentalCoroutinesApi::class)
    val exams: StateFlow<List<com.petcompanion.data.firebase.OnlineExam>> = roomFlow({ _, id -> repo.getExamsFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    private val _selectedExamId = MutableStateFlow<String?>(null)
    fun selectExam(examId: String?) { _selectedExamId.value = examId }

    @OptIn(ExperimentalCoroutinesApi::class)
    val examQuestions: StateFlow<List<com.petcompanion.data.firebase.ExamQuestion>> =
        combine(_selectedExamId, _roomId) { eid, rid -> eid to rid }
            .flatMapLatest { (eid, rid) ->
                if (eid == null || rid.isBlank()) flowOf(emptyList())
                else repo.getQuestionsFlow(rid, eid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val examSubmissions: StateFlow<List<com.petcompanion.data.firebase.ExamSubmission>> =
        combine(_selectedExamId, _roomId) { eid, rid -> eid to rid }
            .flatMapLatest { (eid, rid) ->
                if (eid == null || rid.isBlank()) flowOf(emptyList())
                else repo.getExamSubmissionsFlow(rid, eid)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    /** ครูสร้างข้อสอบ — คืน examId ผ่าน callback (สำหรับ link กับ scheduleEventId) */
    fun createExam(exam: com.petcompanion.data.firebase.OnlineExam, onCreated: (String) -> Unit = {}) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.createExam(id, exam)
            if (r.isSuccess) onCreated(r.getOrDefault(""))
            else _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun updateExam(exam: com.petcompanion.data.firebase.OnlineExam) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.updateExam(id, exam)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun publishExam(exam: com.petcompanion.data.firebase.OnlineExam, published: Boolean) =
        updateExam(exam.copy(isPublished = published))

    fun deleteExam(examId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        if (_selectedExamId.value == examId) _selectedExamId.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.deleteExam(id, examId)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun saveQuestion(examId: String, q: com.petcompanion.data.firebase.ExamQuestion) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.saveQuestion(id, examId, q)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    fun deleteQuestion(examId: String, questionId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { repo.deleteQuestion(id, examId, questionId) }
    }

    /**
     * นำเข้าคำถามจากคลังข้อสอบ (Room DB) ขึ้น Firestore
     *
     * แปลง QuestionEntity → ExamQuestion:
     *  - options[correctIndex] → correctAnswer (เก็บข้อความ ไม่ใช่ index)
     *  - ทุกข้อ type = "choice" (ระบบ Quiz ใช้ตัวเลือกหลายข้อ)
     *  - ใช้ 1.0 คะแนน/ข้อ เป็น default
     */
    fun importQuestionsFromCourse(
        examId: String,
        questions: List<com.petcompanion.domain.model.QuestionEntity>
    ) {
        val rid = _roomId.value; if (rid.isBlank() || questions.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            questions.forEachIndexed { index, q ->
                val examQ = ExamQuestion(
                    id            = UUID.randomUUID().toString(),
                    text          = q.text,
                    type          = ExamQuestion.TYPE_CHOICE,
                    choices       = q.options,
                    correctAnswer = q.options.getOrElse(q.correctIndex) { "" },
                    points        = 1.0,
                    order         = index
                )
                repo.saveQuestion(rid, examId, examQ)
            }
        }
    }

    /**
     * นักเรียนส่งคำตอบข้อสอบ
     * - ถ้า autoGrade → คำนวณคะแนนทันทีจาก questions ที่โหลดไว้แล้ว
     * - บันทึกลง grades เพื่อให้ Leaderboard อัปเดต
     */
    fun submitExam(
        examId: String,
        answers: Map<String, String>,
        exam: com.petcompanion.data.firebase.OnlineExam
    ) {
        val id   = _roomId.value; if (id.isBlank()) return
        val name = _studentName.value; if (name.isBlank()) return
        val questions = examQuestions.value

        viewModelScope.launch(Dispatchers.IO) {
            val maxScore = questions.sumOf { it.points }
            val score = if (exam.autoGrade) {
                questions.sumOf { q ->
                    val studentAns = answers[q.id]?.trim() ?: ""
                    val correct    = q.correctAnswer.trim()
                    if (studentAns.equals(correct, ignoreCase = true)) q.points else 0.0
                }
            } else 0.0

            val submission = com.petcompanion.data.firebase.ExamSubmission(
                examId      = examId,
                studentName = name,
                deviceId    = deviceId,
                answers     = answers,
                score       = score,
                maxScore    = maxScore,
                isGraded    = exam.autoGrade
            )
            val r = repo.submitExam(id, examId, submission)
            if (r.isFailure) {
                _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
                return@launch
            }
            // บันทึกลง grades → Leaderboard อัปเดตอัตโนมัติ
            if (exam.autoGrade && maxScore > 0.0) {
                repo.addGrade(id, GradeRecord(
                    studentName = name,
                    subject     = "[สอบ] ${exam.title}",
                    score       = score,
                    maxScore    = maxScore,
                    date        = todayString(),
                    note        = "ข้อสอบออนไลน์"
                ))
            }
        }
    }

    /** โหลด my submission สำหรับข้อสอบที่เลือก (one-shot) */
    private val _myExamSubmission =
        MutableStateFlow<com.petcompanion.data.firebase.ExamSubmission?>(null)
    val myExamSubmission: StateFlow<com.petcompanion.data.firebase.ExamSubmission?> =
        _myExamSubmission.asStateFlow()

    fun loadMyExamSubmission(examId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _myExamSubmission.value = repo.getMyExamSubmission(id, examId, deviceId)
        }
    }

    fun clearMyExamSubmission() { _myExamSubmission.value = null }

    // ── Upload progress ────────────────────────────────────────────────────
    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    /**
     * อัปโหลดไฟล์ (ถ้ามี uri) แล้วส่งงาน
     * จำกัดขนาดไฟล์ 2 MB
     */
    fun uploadAndSubmitHomework(
        hwId: String,
        hwTitle: String,
        answer: String,
        uri: android.net.Uri?,
        context: android.content.Context
    ) {
        val roomId = _roomId.value; if (roomId.isBlank()) return
        val name   = _studentName.value; if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            var fileUrl       = ""
            var fileName      = ""
            var fileSizeBytes = 0L

            if (uri != null) {
                val size = getFileSize(context, uri)
                if (size > 2 * 1024 * 1024) {
                    _teacherError.value = "ไฟล์ต้องไม่เกิน 2 MB (ขนาด: ${size / 1024} KB)"
                    return@launch
                }
                fileSizeBytes = size
                fileName      = getFileName(context, uri) ?: "attachment"
                _uploadProgress.value = 0f
                val stream = context.contentResolver.openInputStream(uri)
                    ?: run {
                        _teacherError.value = "เปิดไฟล์ไม่ได้"
                        _uploadProgress.value = null
                        return@launch
                    }
                val result = repo.uploadHomeworkFile(roomId, hwId, deviceId, stream, fileName) { prog ->
                    _uploadProgress.value = prog
                }
                _uploadProgress.value = null
                if (result.isFailure) {
                    _teacherError.value = friendlyError(result.exceptionOrNull()?.message)
                    return@launch
                }
                fileUrl = result.getOrDefault("")
            }

            val r = repo.submitHomework(roomId, HomeworkSubmission(
                hwId          = hwId,
                hwTitle       = hwTitle,
                studentName   = name,
                deviceId      = deviceId,
                answer        = answer,
                fileUrl       = fileUrl,
                fileName      = fileName,
                fileSizeBytes = fileSizeBytes
            ))
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    private fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun getFileSize(context: android.content.Context, uri: android.net.Uri): Long {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (idx >= 0) return cursor.getLong(idx)
                }
            }
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        } catch (e: Exception) { 0L }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Attendance Sessions — real-time
    // ══════════════════════════════════════════════════════════════════════
    @OptIn(ExperimentalCoroutinesApi::class)
    val attSessions: StateFlow<List<AttendanceSession>> = roomFlow({ _, id -> repo.getAttSessionsFlow(id) }, emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    /** ครูสร้างเซสชันใหม่ (เปิดทันที) */
    fun createAttSession(label: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.createAttSession(id, AttendanceSession(label = label, date = todayString()))
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    /** ครูเปิด/ปิดเซสชัน */
    fun toggleAttSession(sessionId: String, isOpen: Boolean) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.toggleAttSession(id, sessionId, isOpen)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }

    /** ลบเซสชัน */
    fun deleteAttSession(sessionId: String) {
        val id = _roomId.value; if (id.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { repo.deleteAttSession(id, sessionId) }
    }

    // ── เซสชันที่เครื่องนี้เช็คชื่อไปแล้ว (Firestore-backed — คงอยู่แม้ restart app) ──
    private val _myCheckedSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val myCheckedSessionIds: StateFlow<Set<String>> = _myCheckedSessionIds.asStateFlow()

    /**
     * โหลด checked state จาก Firestore สำหรับทุก session ที่มีในห้อง
     * เรียกเมื่อเปิดหน้า AttendanceScreen หรือเมื่อ attSessions เปลี่ยน
     */
    fun refreshMyCheckIns() {
        val roomId   = _roomId.value; if (roomId.isBlank()) return
        val sessions = attSessions.value; if (sessions.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val checked = sessions.mapNotNull { session ->
                if (repo.isCheckedIn(roomId, session.id, deviceId)) session.id else null
            }.toSet()
            _myCheckedSessionIds.value = checked
        }
    }

    /** นักเรียนเช็คชื่อ (ใช้ deviceId เป็น document key กัน check-in แทนกัน) */
    fun checkIn(sessionId: String) {
        val id   = _roomId.value; if (id.isBlank()) return
        val name = _studentName.value; if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val r = repo.checkIn(id, sessionId, name, deviceId)
            if (r.isSuccess) {
                // optimistic update → UI ตอบสนองทันทีโดยไม่ต้องรอ refresh
                _myCheckedSessionIds.value = _myCheckedSessionIds.value + sessionId
            } else {
                _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
            }
        }
    }

    // ── Check-ins ของ session ที่ครูเลือกดู (real-time) ──────────────────
    private val _selectedSessionId = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedSessionCheckIns: StateFlow<List<AttendanceCheckIn>> = _selectedSessionId
        .flatMapLatest { sid ->
            val rid = _roomId.value
            if (sid == null || rid.isBlank()) flowOf(emptyList())
            else repo.getCheckInsFlow(rid, sid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())

    fun selectAttSession(sessionId: String?) { _selectedSessionId.value = sessionId }

    /**
     * ครูให้/หักคะแนนทั้งกลุ่มจากเซสชันเช็คชื่อ
     * presentScore = คะแนนที่ให้คนที่มา
     * absentScore  = คะแนนที่หักคนที่ขาด (ส่วนใหญ่เป็น 0 หรือ negative ก็ coerce เป็น 0)
     * subject      = ชื่อวิชา/หมวด
     */
    fun addAttendanceScore(
        sessionId: String,
        presentScore: Double,
        absentScore: Double,
        subject: String
    ) {
        val roomId = _roomId.value; if (roomId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val allStudents = approvedStudents.value  // เฉพาะนักเรียนที่อนุมัติแล้ว
            if (allStudents.isEmpty()) {
                _teacherError.value = "ยังไม่มีนักเรียนที่ได้รับการอนุมัติในห้องนี้"
                return@launch
            }
            val checkIns         = repo.getCheckInsOnce(roomId, sessionId)
            // ✅ match by deviceId (accurate) — fallback name-match สำหรับ check-in เก่าที่ไม่มี deviceId
            val checkedDeviceIds = checkIns.map { it.deviceId }.filter { it.isNotBlank() }.toSet()
            val checkedNames     = checkIns.map { it.studentName.trim().lowercase() }.toSet()
            val today            = todayString()
            val gradesToAdd      = allStudents.map { student ->
                val isPresent = if (checkedDeviceIds.isNotEmpty())
                    student.deviceId in checkedDeviceIds
                else
                    student.name.trim().lowercase() in checkedNames
                GradeRecord(
                    studentName = student.name,
                    subject     = subject.ifBlank { "เช็คชื่อ" },
                    score       = if (isPresent) presentScore else absentScore.coerceAtLeast(0.0),
                    maxScore    = presentScore.coerceAtLeast(1.0),
                    date        = today,
                    note        = if (isPresent) "เข้าชั้นเรียน ✓" else "ขาดเรียน"
                )
            }
            val r = repo.bulkAddGrades(roomId, gradesToAdd)
            if (r.isFailure) _teacherError.value = friendlyError(r.exceptionOrNull()?.message)
        }
    }
}
