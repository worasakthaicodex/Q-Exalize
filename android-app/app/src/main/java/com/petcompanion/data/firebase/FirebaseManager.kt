package com.petcompanion.data.firebase

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase ใช้ google-services.json (hardcoded project ของเรา)
 * ครู: login ด้วย email/password → ได้ teacherUid → ข้อมูลอยู่ใต้ teachers/{uid}/
 * นักเรียน: ไม่ต้อง login — query Firestore แบบ anonymous ผ่าน teacherUid ที่ได้จาก QR
 */
class FirebaseManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS = "firebase_prefs"

        @Volatile private var INSTANCE: FirebaseManager? = null

        fun getInstance(context: Context): FirebaseManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    // ── Auth state ─────────────────────────────────────────────────────────
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    val isTeacher: Boolean get() = auth.currentUser != null
    val teacherUid: String? get() = auth.currentUser?.uid

    // ── compat กับโค้ดเดิม ─────────────────────────────────────────────────
    val isConnected: StateFlow<Boolean> get() = MutableStateFlow(true).asStateFlow()
    // isTeacherMode: reactive — อิงจาก currentUser StateFlow ผ่าน ViewModel

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    // ── Teacher: Login ─────────────────────────────────────────────────────
    suspend fun teacherLogin(email: String, password: String): Result<FirebaseUser> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        _currentUser.value = result.user
        result.user!!
    }

    // ── Teacher: Register ──────────────────────────────────────────────────
    suspend fun teacherRegister(email: String, password: String, displayName: String): Result<FirebaseUser> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user!!
        val profileUpdate = com.google.firebase.auth.userProfileChangeRequest { setDisplayName(displayName) }
        user.updateProfile(profileUpdate).await()
        // สร้าง teacher document ใน Firestore
        firestore.collection("teachers").document(user.uid)
            .set(mapOf("name" to displayName, "email" to email, "createdAt" to System.currentTimeMillis()))
            .await()
        _currentUser.value = user
        user
    }

    // ── Teacher: Logout ────────────────────────────────────────────────────
    fun teacherLogout() {
        auth.signOut()
        _currentUser.value = null
    }

    // ── Student: บันทึก room ที่ scan QR มา ───────────────────────────────
    fun saveStudentRoom(teacherUid: String, roomId: String) {
        prefs.edit()
            .putString("s_teacher_uid", teacherUid)
            .putString("s_room_id", roomId)
            .apply()
    }

    fun getSavedStudentTeacherUid(): String? = prefs.getString("s_teacher_uid", null)
    fun getSavedStudentRoomId(): String? = prefs.getString("s_room_id", null)

    fun clearStudentRoom() {
        prefs.edit().remove("s_teacher_uid").remove("s_room_id").apply()
    }

    // ── Firestore paths ────────────────────────────────────────────────────
    // ครู: teachers/{teacherUid}/rooms/{roomId}/...
    fun teacherRoomsRef(uid: String = teacherUid!!) =
        firestore.collection("teachers").document(uid).collection("rooms")

    fun roomRef(teacherUid: String, roomId: String) =
        firestore.collection("teachers").document(teacherUid).collection("rooms").document(roomId)

    // ── compat: ส่วนเดิมที่ใช้ connectAsStudent (ยังใช้ได้ แต่ไม่ต้องทำอะไรแล้ว) ──
    @Deprecated("ไม่ต้องใช้แล้ว — Firebase ใช้ google-services.json ตายตัว")
    fun connectAsStudent(config: FirebaseConfig): Result<Unit> = Result.success(Unit)

    @Deprecated("ไม่ต้องใช้แล้ว")
    fun saveAndConnect(config: FirebaseConfig): Result<Unit> = Result.success(Unit)

    @Deprecated("ไม่ต้องใช้แล้ว")
    val savedConfig: FirebaseConfig? get() = null

    @Deprecated("ไม่ต้องใช้แล้ว")
    val activeConfig: FirebaseConfig? get() = null

    @Deprecated("ไม่ต้องใช้แล้ว")
    fun disconnect() { auth.signOut(); _currentUser.value = null }

    @Deprecated("ใช้ auth/firestore/storage โดยตรง")
    fun firestore(): FirebaseFirestore = firestore

    @Deprecated("ใช้ auth โดยตรง")
    fun auth(): FirebaseAuth = auth

    @Deprecated("ใช้ storage โดยตรง")
    fun storage(): FirebaseStorage = storage
}
