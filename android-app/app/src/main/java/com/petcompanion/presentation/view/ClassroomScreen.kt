package com.petcompanion.presentation.view

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.petcompanion.R
import com.petcompanion.data.firebase.ClassRoom
import com.petcompanion.data.firebase.StudentProfile
import com.petcompanion.data.local.QrUtil
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.theme.QTheme
import com.petcompanion.presentation.viewmodel.ClassroomViewModel
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// QR Format: "PCR|{teacherUid}|{roomId}"
// ─────────────────────────────────────────────────────────────────────────────
private const val QR_PREFIX = "PCR"
private const val QR_SEP    = "|"

@Composable
fun ClassroomScreen(
    viewModel: ClassroomViewModel,
    onBackClick: () -> Unit,
    onScheduleClick: () -> Unit,
    onHomeworkClick: () -> Unit,
    onAttendanceClick: () -> Unit,
    onGradesClick: () -> Unit,
    onFilesClick: () -> Unit,
    onArenaClick: () -> Unit,
    onOnlineClick: () -> Unit,
    onGoToSettings: () -> Unit,
    onGoToTeacherLogin: () -> Unit = onGoToSettings,
    onGoToSubscription: () -> Unit = {},
    isTeacherSubscribed: Boolean = true   // ส่งมาจาก MainActivity
) {
    val isConnected       by viewModel.isFirebaseConnected.collectAsState()
    val isTeacher         by viewModel.isTeacherMode.collectAsState()
    val isEffTeacher      by viewModel.isEffectiveTeacher.collectAsState()
    val teacherRooms      by viewModel.teacherRooms.collectAsState()
    val room              by viewModel.room.collectAsState()
    val roomError         by viewModel.roomError.collectAsState()
    val roomId            by viewModel.roomId.collectAsState()
    val teacherError      by viewModel.teacherError.collectAsState()
    val pendingStudents    by viewModel.pendingStudents.collectAsState()
    val approvedStudents   by viewModel.approvedStudents.collectAsState()
    val myStudentProfile   by viewModel.myStudentProfile.collectAsState()
    val studentName        by viewModel.studentName.collectAsState()
    val studentRoomsInfo   by viewModel.studentRoomsInfo.collectAsState()
    val studentRoomIds     by viewModel.studentRoomIds.collectAsState()
    val teacherUid         by viewModel.teacherUid.collectAsState()
    val context            = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ── UI state ──────────────────────────────────────────────────────────
    var showJoinDialog        by remember { mutableStateOf(false) }
    var showCreateRoomDialog  by remember { mutableStateOf(false) }
    var showEditRoomDialog    by remember { mutableStateOf(false) }
    var showQrDialog          by remember { mutableStateOf(false) }
    var showRulesGuide        by remember { mutableStateOf(false) }
    var showDeleteRoomConfirm by remember { mutableStateOf(false) }
    var showRoomMenu          by remember { mutableStateOf(false) }
    var showLeaveConfirm          by remember { mutableStateOf(false) }
    var showNameDialogInClassroom by remember { mutableStateOf(false) }
    var inputRoomId               by remember { mutableStateOf("") }

    // ── List mode ──────────────────────────────────────────────────────────
    val teacherInListMode = isTeacher && roomId.isBlank()
    // นักเรียนเห็น room list ทันที ถ้าเคยสแกนห้องไว้แล้ว (ไม่ต้องรอ reconnect)
    val studentInListMode = !isTeacher && roomId.isBlank() &&
            (isConnected || studentRoomIds.isNotEmpty())

    // ── Smart back: ถ้าอยู่ใน dashboard → กลับไป list ────────────────────
    val handleBack: () -> Unit = {
        if (roomId.isNotBlank()) viewModel.clearSelectedRoom()
        else onBackClick()
    }

    // ── QR parse helper (ใช้ร่วมกันทั้ง camera scan และ gallery decode) ────
    val handleScannedContent: (String) -> Unit = { scanned ->
        val parts = scanned.split(QR_SEP)
        if (parts.size == 3 && parts[0] == QR_PREFIX) {
            // format ใหม่: "PCR|{teacherUid}|{roomId}"
            viewModel.connectFromQr(teacherUid = parts[1], roomId = parts[2])
        } else {
            // fallback: กรอก roomId ตรง (ไม่มี teacherUid ไม่รู้จะเชื่อมกับครูคนไหน)
            val id = scanned.trim()
            if (id.isNotBlank()) {
                viewModel.addStudentRoom(id)
                viewModel.saveRoomId(id)
            }
        }
        showJoinDialog = false
    }

    // ── Camera QR Scanner ─────────────────────────────────────────────────
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleScannedContent(it) }
    }

    // ── Gallery QR Decoder (สำหรับ Emulator / เลือกรูปจากแกลเลอรี่) ──────
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(stream)
                stream?.close()
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val source      = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                val binary      = BinaryBitmap(HybridBinarizer(source))
                val decoded     = MultiFormatReader().decode(binary)
                handleScannedContent(decoded.text)
            } catch (_: Exception) {
                // ถอดรหัสล้มเหลว — ไม่ทำอะไร
            }
        }
    }

    LaunchedEffect(roomId, isConnected) {
        if (roomId.isNotBlank() && isConnected) viewModel.loadRoom()
    }
    // โหลด student room list ทันทีที่เข้า list mode หรือ connect สำเร็จ
    LaunchedEffect(studentInListMode, isConnected) {
        if (studentInListMode && isConnected) viewModel.loadStudentRooms()
    }
    LaunchedEffect(teacherError) {
        teacherError?.let { viewModel.clearTeacherError(); snackbarHostState.showSnackbar(it) }
    }
    // นักเรียนสแกน QR แล้ว → ยังไม่มี profile ใน Firestore
    // - ถ้าไม่มีชื่อ → เปิด dialog ให้ตั้งชื่อ
    // - ถ้ามีชื่อแล้ว → auto-register (status=pending) รอครูอนุมัติ
    LaunchedEffect(roomId, isConnected, isTeacher, studentName, myStudentProfile) {
        if (!isTeacher && isConnected && roomId.isNotBlank() && myStudentProfile == null) {
            if (studentName.isBlank()) {
                showNameDialogInClassroom = true
            } else {
                viewModel.autoRegisterStudent()
            }
        }
    }

    Scaffold(
        containerColor = QTheme.colors.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        // ── FAB: ครู = สร้างห้อง / นักเรียน = สแกน QR เข้าร่วมห้องใหม่ ──
        floatingActionButton = {
            when {
                teacherInListMode -> FloatingActionButton(
                    onClick = {
                        if (isTeacherSubscribed) showCreateRoomDialog = true
                        else onGoToSubscription()
                    },
                    containerColor = if (isTeacherSubscribed) PrimaryBlue else Color(0xFFF4A500),
                    contentColor   = Color.White
                ) {
                    Icon(
                        if (isTeacherSubscribed) Icons.Default.Add else Icons.Default.Lock,
                        contentDescription = if (isTeacherSubscribed) "สร้างห้องใหม่" else "ต้อง Subscribe"
                    )
                }

                studentInListMode -> FloatingActionButton(
                    onClick        = { showJoinDialog = true },
                    containerColor = Color(0xFF4CAF50),
                    contentColor   = Color.White
                ) { Icon(Icons.Default.QrCodeScanner, contentDescription = "สแกน QR") }
            }
        },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = handleBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = QTheme.colors.textPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            teacherInListMode -> "ห้องเรียนของฉัน"
                            studentInListMode -> "ห้องเรียนของฉัน"
                            else              -> stringResource(R.string.mini_app_classroom)
                        },
                        color      = QTheme.colors.textPrimary,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        when {
                            teacherInListMode -> "เลือกห้องที่ต้องการเข้า"
                            studentInListMode -> "กด + เพื่อสแกน QR เข้าร่วมห้องใหม่"
                            else              -> "Online Classroom"
                        },
                        color    = QTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                when {
                    isTeacher -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = PrimaryBlue.copy(0.15f)) {
                            Text(
                                "ครู", color = PrimaryBlue, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (roomId.isNotBlank()) {
                            Spacer(Modifier.width(2.dp))
                            IconButton(onClick = { viewModel.clearSelectedRoom() }) {
                                Icon(Icons.Default.GridView, null, tint = PrimaryBlue)
                            }
                        }
                    }
                    isConnected -> {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF4CAF50).copy(0.15f)) {
                            Text(
                                "นักเรียน", color = Color(0xFF4CAF50), fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        // ปุ่มกลับไป room list (เมื่ออยู่ใน dashboard)
                        if (roomId.isNotBlank()) {
                            Spacer(Modifier.width(2.dp))
                            IconButton(onClick = { viewModel.clearSelectedRoom() }) {
                                Icon(Icons.Default.GridView, null, tint = Color(0xFF4CAF50))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->

        // ── Subscription banner สำหรับครูที่ยังไม่ subscribe ─────────────
        if (isTeacher && !isTeacherSubscribed) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding)
                    .padding(16.dp)
                    .background(Color(0xFFFFF8E1), RoundedCornerShape(14.dp))
            ) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFFF4A500), modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ต้องสมัคร Teacher Pro", color = Color(0xFF7C5200),
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("เพื่อสร้างห้องเรียนและจัดการนักเรียน",
                            color = Color(0xFF9E6B00), fontSize = 12.sp)
                    }
                    Button(
                        onClick = onGoToSubscription,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF4A500)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("สมัคร", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // ── Teacher room list ──────────────────────────────────────────────
        if (teacherInListMode) {
            TeacherRoomListContent(
                rooms        = teacherRooms,
                padding      = padding,
                onRoomClick  = { room -> viewModel.selectRoom(room.id) },
                onCreateRoom = { showCreateRoomDialog = true }
            )
        // ── Student room list ──────────────────────────────────────────────
        } else if (studentInListMode) {
            StudentRoomListContent(
                entries      = studentRoomsInfo,
                localRoomIds = studentRoomIds,
                isConnected  = isConnected,
                padding      = padding,
                onRoomClick  = { entry -> viewModel.selectRoom(entry.room.id) },
                onScanQr     = { showJoinDialog = true }
            )
        } else {
            // ── โหมด dashboard (ครูเลือกห้องแล้ว หรือ นักเรียน) ─────────
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isConnected) {
                    // มี context ห้องเรียน → กำลัง reconnect (แสดง spinner)
                    // ไม่มี context → ยังไม่ได้ connect จริงๆ
                    val hasRoomContext = roomId.isNotBlank() || studentRoomIds.isNotEmpty()
                    if (!isTeacher && hasRoomContext) {
                        ReconnectingCard(onScanQr = { showJoinDialog = true })
                    } else {
                        NotConnectedCard(onGoToTeacherLogin = onGoToTeacherLogin, onScanQr = { showJoinDialog = true })
                    }
                } else if (roomId.isNotBlank()) {
                    // ── Room Info Card (compact) ──────────────────────────
                    when {
                        room != null -> {
                            val r = room!!
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(16.dp),
                                colors   = CardDefaults.cardColors(
                                    containerColor = PrimaryBlue.copy(alpha = 0.10f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // ── หัว: ไอคอน ชื่อ และปุ่มจัดการ ──────────
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape    = RoundedCornerShape(10.dp),
                                            color    = PrimaryBlue.copy(0.18f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.School, null,
                                                    tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(r.name, color = PrimaryBlue,
                                                fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (r.teacherName.isNotBlank())
                                                Text("อ.${r.teacherName}",
                                                    color = QTheme.colors.textSecondary, fontSize = 11.sp)
                                        }
                                        // ปุ่ม ⋮ เปิดเมนู (เฉพาะ server owner)
                                        if (isTeacher) {
                                            Box {
                                                IconButton(onClick = { showRoomMenu = true },
                                                    modifier = Modifier.size(36.dp)) {
                                                    Icon(Icons.Default.MoreVert, null,
                                                        tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                                }
                                                DropdownMenu(
                                                    expanded         = showRoomMenu,
                                                    onDismissRequest = { showRoomMenu = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text         = { Text("แสดง QR Code") },
                                                        onClick      = { showQrDialog = true; showRoomMenu = false },
                                                        leadingIcon  = { Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(18.dp)) }
                                                    )
                                                    DropdownMenuItem(
                                                        text         = { Text("แก้ไขห้อง") },
                                                        onClick      = { showEditRoomDialog = true; showRoomMenu = false },
                                                        leadingIcon  = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) }
                                                    )
                                                    DropdownMenuItem(
                                                        text         = { Text("ตั้งค่า Rules") },
                                                        onClick      = { showRulesGuide = true; showRoomMenu = false },
                                                        leadingIcon  = { Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp)) }
                                                    )
                                                    HorizontalDivider()
                                                    DropdownMenuItem(
                                                        text    = { Text("ลบห้องเรียน", color = Color(0xFFEF5350)) },
                                                        onClick = { showDeleteRoomConfirm = true; showRoomMenu = false }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // ── Room ID + Rules ───────────────────────
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = PrimaryBlue.copy(0.12f)) {
                                            Text("ID: $roomId", color = PrimaryBlue,
                                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                        if (r.description.isNotBlank()) {
                                            Text("·", color = QTheme.colors.textSecondary, fontSize = 10.sp)
                                            Text(r.description, color = QTheme.colors.textSecondary,
                                                fontSize = 11.sp, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f))
                                        }
                                        if (isTeacher) {
                                            Spacer(Modifier.weight(1f))
                                            Text("ตั้งค่า Rules", color = PrimaryBlue,
                                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { showRulesGuide = true })
                                        }
                                    }
                                }
                            }
                        }
                        roomError != null -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFEF5350))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.classroom_error),
                                            color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(roomError!!, color = Color(0xFFB71C1C), fontSize = 11.sp)
                                    if (roomError!!.contains("Rules") || roomError!!.contains("PERMISSION")) {
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { showRulesGuide = true },
                                            shape   = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Help, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("วิธีตั้ง Security Rules", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
                            ) {
                                Row(
                                    modifier          = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(20.dp),
                                        strokeWidth = 2.dp, color = PrimaryBlue
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.classroom_loading),
                                        color = QTheme.colors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    // ── Pending members (ครู / co-teacher เท่านั้น) ──────
                    if (isEffTeacher && pendingStudents.isNotEmpty()) {
                        PendingMembersCard(
                            pending          = pendingStudents,
                            approved         = approvedStudents,
                            onApproveStudent = { dId -> viewModel.approveStudent(dId, StudentProfile.ROLE_STUDENT) },
                            onApproveTeacher = { dId -> viewModel.approveStudent(dId, StudentProfile.ROLE_TEACHER) },
                            onReject         = { dId -> viewModel.removeStudent(dId) },
                            onTransfer       = { oldDId, newDId -> viewModel.transferStudentDevice(oldDId, newDId) }
                        )
                    }

                    // ── Gate content: ครู vs นักเรียน (pending / approved) ─
                    // ใช้ local val เพื่อให้ Kotlin smart-cast ได้ (delegate ไม่ถูก cast อัตโนมัติ)
                    val profile = myStudentProfile
                    when {
                        // ─── ครู / co-teacher: เห็น dashboard เต็ม ──────────
                        isEffTeacher -> {
                            // Members section: ครูเห็นเสมอ แม้ยังไม่มีสมาชิก
                            ApprovedMembersSection(
                                members       = approvedStudents,
                                myDeviceId    = viewModel.deviceId,
                                isEffTeacher  = true,
                                isServerOwner = isTeacher,
                                onRemove      = { dId -> viewModel.removeStudent(dId) },
                                onLeave       = { showLeaveConfirm = true }
                            )
                            // ครู/co-teacher → ปุ่ม Arena = จัดการข้อสอบ
                            FeatureGrid(
                                isTeacher         = true,
                                onScheduleClick   = onScheduleClick,
                                onHomeworkClick   = onHomeworkClick,
                                onAttendanceClick = onAttendanceClick,
                                onGradesClick     = onGradesClick,
                                onFilesClick      = onFilesClick,
                                onArenaClick      = onArenaClick,
                                onOnlineClick     = onOnlineClick
                            )
                        }

                        // ─── นักเรียนที่ยังไม่ได้ตั้งชื่อ / ยังไม่ลงทะเบียน ─
                        profile == null -> {
                            RegisterCard(onRegister = { showNameDialogInClassroom = true })
                        }

                        // ─── นักเรียนที่รอการอนุมัติ (status=pending) ────────
                        profile.status == StudentProfile.STATUS_PENDING -> {
                            WaitingApprovalCard(name = studentName)
                        }

                        // ─── นักเรียนที่ได้รับอนุมัติ (status=approved) ──────
                        profile.isApproved -> {
                            if (approvedStudents.isNotEmpty()) {
                                ApprovedMembersSection(
                                    members       = approvedStudents,
                                    myDeviceId    = viewModel.deviceId,
                                    isEffTeacher  = false,
                                    isServerOwner = false,
                                    onRemove      = { _ -> },  // นักเรียนไม่สามารถนำคนอื่นออก
                                    onLeave       = { showLeaveConfirm = true }
                                )
                            }
                            // นักเรียน → ปุ่ม สนามสอบออนไลน์ แทน Arena
                            FeatureGrid(
                                isTeacher         = false,
                                onScheduleClick   = onScheduleClick,
                                onHomeworkClick   = onHomeworkClick,
                                onAttendanceClick = onAttendanceClick,
                                onGradesClick     = onGradesClick,
                                onFilesClick      = onFilesClick,
                                onArenaClick      = onArenaClick,
                                onOnlineClick     = onOnlineClick
                            )
                        }
                    }
                } else {
                    // นักเรียนที่ยังไม่ได้สแกน QR
                    NotConnectedCard(onGoToTeacherLogin = onGoToTeacherLogin, onScanQr = { showJoinDialog = true })
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ── Join Dialog (นักเรียน) ────────────────────────────────────────────
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor   = QTheme.colors.card,
            icon   = { Icon(Icons.Default.QrCodeScanner, null, tint = PrimaryBlue) },
            title  = {
                Text(
                    stringResource(R.string.qr_title),
                    color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.qr_scan_hint), color = QTheme.colors.textSecondary, fontSize = 13.sp)
                    // ── ปุ่มสแกนด้วยกล้อง ────────────────────────────────
                    OutlinedButton(
                        onClick = {
                            scanLauncher.launch(
                                ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("สแกน QR Code ห้องเรียน")
                                    setBeepEnabled(false)
                                    setOrientationLocked(false)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("สแกนด้วยกล้อง", color = PrimaryBlue)
                    }
                    // ── ปุ่มเลือกรูปจากแกลเลอรี่ (ใช้ได้บน Emulator) ─────
                    OutlinedButton(
                        onClick  = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(0.6f))
                    ) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(18.dp), tint = PrimaryBlue.copy(0.8f))
                        Spacer(Modifier.width(8.dp))
                        Text("เลือกรูป QR จากแกลเลอรี่", color = PrimaryBlue.copy(0.8f))
                    }
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Divider(modifier = Modifier.weight(1f), color = QTheme.colors.textSecondary.copy(0.3f))
                        Text("หรือพิมพ์", color = QTheme.colors.textSecondary, fontSize = 11.sp)
                        Divider(modifier = Modifier.weight(1f), color = QTheme.colors.textSecondary.copy(0.3f))
                    }
                    OutlinedTextField(
                        value           = inputRoomId,
                        onValueChange   = { inputRoomId = it.uppercase() },
                        label           = { Text(stringResource(R.string.qr_room_id_label)) },
                        placeholder     = { Text(stringResource(R.string.qr_placeholder)) },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (inputRoomId.isNotBlank()) {
                                // บันทึกเข้า room list เท่านั้น (ต้องสแกน QR เพื่อเชื่อมต่อ Firebase)
                                viewModel.addStudentRoom(inputRoomId)
                                showJoinDialog = false; inputRoomId = ""
                            }
                        }),
                        shape           = RoundedCornerShape(12.dp),
                        colors          = classroomTfColors(),
                        supportingText  = {
                            Text("* ต้องสแกน QR ของครูเพื่อเชื่อมต่อห้องเรียน",
                                color = QTheme.colors.textSecondary, fontSize = 10.sp)
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputRoomId.isNotBlank()) {
                            // บันทึกเข้า room list เท่านั้น (ต้องสแกน QR เพื่อเชื่อมต่อ Firebase)
                            viewModel.addStudentRoom(inputRoomId)
                            showJoinDialog = false; inputRoomId = ""
                        }
                    },
                    colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = inputRoomId.isNotBlank()
                ) { Text(stringResource(R.string.qr_join_btn)) }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false; inputRoomId = "" }) {
                    Text(stringResource(R.string.btn_cancel), color = QTheme.colors.textSecondary)
                }
            }
        )
    }

    // ── Create Room Dialog ────────────────────────────────────────────────
    if (showCreateRoomDialog) {
        RoomFormDialog(
            dialogTitle    = "สร้างห้องเรียนใหม่",
            initialRoomId  = "",
            initialName    = "",
            initialTeacher = "",
            initialDesc    = "",
            readOnlyRoomId = false,
            onDismiss      = { showCreateRoomDialog = false },
            onConfirm      = { rId, name, teacher, desc ->
                viewModel.createRoom(rId, name, teacher, desc)  // ไม่เลือกห้องอัตโนมัติ
                showCreateRoomDialog = false
            }
        )
    }

    // ── Edit Room Dialog ──────────────────────────────────────────────────
    if (showEditRoomDialog) {
        val r = room
        RoomFormDialog(
            dialogTitle    = "แก้ไขห้องเรียน",
            initialRoomId  = roomId,
            initialName    = r?.name ?: "",
            initialTeacher = r?.teacherName ?: "",
            initialDesc    = r?.description ?: "",
            readOnlyRoomId = true,
            onDismiss      = { showEditRoomDialog = false },
            onConfirm      = { rId, name, teacher, desc ->
                viewModel.createOrUpdateRoom(rId, name, teacher, desc)
                showEditRoomDialog = false
            }
        )
    }

    // ── QR Display Dialog ─────────────────────────────────────────────────
    if (showQrDialog && roomId.isNotBlank()) {
        QrDisplayDialog(
            roomId     = roomId,
            roomName   = room?.name ?: roomId,
            teacherUid = teacherUid,
            context    = context,
            onDismiss  = { showQrDialog = false }
        )
    }

    // ── Leave Room Confirm ────────────────────────────────────────────────
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor   = QTheme.colors.card,
            icon   = { Icon(Icons.Default.ExitToApp, null, tint = Color(0xFFFF9800)) },
            title  = { Text("ออกจากห้องเรียน?", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text   = { Text("คุณต้องการออกจากห้องนี้ใช่ไหม?\nสามารถกลับมาสแกน QR เพื่อเข้าร่วมใหม่ได้ภายหลัง",
                color = QTheme.colors.textSecondary) },
            confirmButton = {
                Button(
                    onClick = { viewModel.leaveRoom(); showLeaveConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Icon(Icons.Default.ExitToApp, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ออกจากห้อง")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("ยกเลิก", color = QTheme.colors.textSecondary)
                }
            }
        )
    }

    // ── Delete Room Confirm ───────────────────────────────────────────────
    if (showDeleteRoomConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteRoomConfirm = false },
            containerColor   = QTheme.colors.card,
            icon   = { Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFEF5350)) },
            title  = { Text("ลบห้องเรียน?", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text   = {
                Text(
                    "ลบ \"${room?.name ?: roomId}\"?\n\nข้อมูลในห้องทั้งหมด (การบ้าน, คะแนน, ฯลฯ) จะหายถาวร\nไม่สามารถกู้คืนได้",
                    color = QTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteRoom(); showDeleteRoomConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ลบถาวร")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRoomConfirm = false }) {
                    Text("ยกเลิก", color = QTheme.colors.textSecondary)
                }
            }
        )
    }

    // ── Firestore Rules Guide ─────────────────────────────────────────────
    if (showRulesGuide) {
        FirestoreRulesDialog(onDismiss = { showRulesGuide = false })
    }

    // ── ตั้งชื่อนักเรียน (เปิดอัตโนมัติหลังสแกน QR ถ้ายังไม่มีชื่อ) ────
    if (showNameDialogInClassroom) {
        StudentNameDialog(
            current   = studentName,
            onDismiss = { showNameDialogInClassroom = false },
            onConfirm = { name, avatar ->
                viewModel.saveStudentName(name, avatar)
                showNameDialogInClassroom = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StudentRoomListContent — รายการห้องที่นักเรียนเข้าร่วม
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StudentRoomListContent(
    entries: List<com.petcompanion.data.firebase.StudentRoomEntry>,
    localRoomIds: Set<String>,   // roomId ที่เก็บใน prefs (ใช้แสดงก่อน connect)
    isConnected: Boolean,
    padding: PaddingValues,
    onRoomClick: (com.petcompanion.data.firebase.StudentRoomEntry) -> Unit,
    onScanQr: () -> Unit
) {
    // รวม entries ที่โหลดได้ + placeholder สำหรับห้องที่ยังโหลดไม่เสร็จ
    val loadedIds   = entries.map { it.room.id }.toSet()
    val pendingIds  = localRoomIds - loadedIds  // ยังรอโหลด / ยังไม่ connect

    LazyColumn(
        modifier            = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding      = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── กรณีไม่มีห้องเลย ──────────────────────────────────────────────
        if (localRoomIds.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.School, null,
                            tint = QTheme.colors.textSecondary, modifier = Modifier.size(72.dp))
                        Text("ยังไม่ได้เข้าร่วมห้องเรียน",
                            color = QTheme.colors.textSecondary, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("กดปุ่ม + เพื่อสแกน QR จากครู",
                            color = QTheme.colors.textSecondary, fontSize = 13.sp)
                        Button(
                            onClick = onScanQr,
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape   = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("สแกน QR เข้าร่วมห้อง")
                        }
                    }
                }
            }
        } else {
            // ── Header ──────────────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.School, null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    Text("${localRoomIds.size} ห้องเรียน",
                        color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    // แสดง "กำลังเชื่อมต่อ..." ถ้ายังไม่ connect
                    if (!isConnected) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp,
                                color = QTheme.colors.textSecondary)
                            Text("กำลังเชื่อมต่อ...",
                                color = QTheme.colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            // ── ห้องที่โหลดข้อมูลได้แล้ว ────────────────────────────────────
            items(entries) { entry ->
                StudentRoomCard(
                    entry       = entry,
                    isConnected = isConnected,
                    onClick     = { onRoomClick(entry) }
                )
            }

            // ── Skeleton สำหรับห้องที่ยังโหลดไม่เสร็จ ───────────────────────
            items(pendingIds.toList()) { roomId ->
                StudentRoomSkeletonCard(roomId = roomId)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun StudentRoomSkeletonCard(roomId: String) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = QTheme.colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp),
                color    = QTheme.colors.textSecondary.copy(0.08f),
                modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = QTheme.colors.textSecondary.copy(0.4f))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(4.dp),
                    color    = QTheme.colors.textSecondary.copy(0.12f),
                    modifier = Modifier.fillMaxWidth(0.6f).height(14.dp)) {}
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp),
                        color    = PrimaryBlue.copy(0.08f)) {
                        Text(roomId, color = PrimaryBlue, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Text("กำลังโหลด...",
                        color = QTheme.colors.textSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun StudentRoomCard(
    entry: com.petcompanion.data.firebase.StudentRoomEntry,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val profile     = entry.myProfile
    val statusColor = when (profile?.status) {
        com.petcompanion.data.firebase.StudentProfile.STATUS_APPROVED -> Color(0xFF4CAF50)
        com.petcompanion.data.firebase.StudentProfile.STATUS_PENDING  -> Color(0xFFFF9800)
        else -> QTheme.colors.textSecondary
    }
    val statusLabel = when (profile?.status) {
        com.petcompanion.data.firebase.StudentProfile.STATUS_APPROVED -> "อนุมัติแล้ว ✓"
        com.petcompanion.data.firebase.StudentProfile.STATUS_PENDING  -> "รอการอนุมัติ…"
        else -> "ยังไม่ได้ลงทะเบียน"
    }

    Card(
        modifier  = Modifier.fillMaxWidth().clickable(
            enabled = isConnected,    // ❌ ป้องกันกดขณะ offline — ไม่งั้นจะหลุดออกจากห้อง
            onClick = onClick
        ),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isConnected) QTheme.colors.card
                             else QTheme.colors.card.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isConnected) 1.dp else 0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = statusColor.copy(alpha = if (isConnected) 0.12f else 0.06f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!isConnected) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = QTheme.colors.textSecondary.copy(0.4f)
                        )
                    } else {
                        Icon(Icons.Default.School, null,
                            tint = statusColor, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.room.name,
                    color = if (isConnected) QTheme.colors.textPrimary
                            else QTheme.colors.textSecondary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.room.teacherName.isNotBlank())
                    Text("อ.${entry.room.teacherName}",
                        color = QTheme.colors.textSecondary, fontSize = 12.sp)
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Surface(shape = RoundedCornerShape(4.dp), color = PrimaryBlue.copy(0.08f)) {
                        Text(entry.room.id, color = PrimaryBlue, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (isConnected) {
                        Surface(shape = RoundedCornerShape(4.dp), color = statusColor.copy(0.10f)) {
                            Text(statusLabel, color = statusColor, fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else {
                        Text("กำลังเชื่อมต่อ...",
                            color = QTheme.colors.textSecondary, fontSize = 10.sp)
                    }
                }
            }
            if (isConnected) {
                Icon(Icons.Default.ChevronRight, null,
                    tint = QTheme.colors.textSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TeacherRoomListContent — LazyColumn รายการห้องทั้งหมดของครู
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TeacherRoomListContent(
    rooms: List<ClassRoom>,
    padding: PaddingValues,
    onRoomClick: (ClassRoom) -> Unit,
    onCreateRoom: () -> Unit
) {
    LazyColumn(
        modifier            = Modifier
            .padding(padding)
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding      = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (rooms.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(top = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.School, null,
                            tint = QTheme.colors.textSecondary, modifier = Modifier.size(72.dp)
                        )
                        Text(
                            "ยังไม่มีห้องเรียน",
                            color = QTheme.colors.textSecondary, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "กดปุ่ม + เพื่อสร้างห้องเรียนใหม่",
                            color = QTheme.colors.textSecondary, fontSize = 13.sp
                        )
                        Button(
                            onClick = onCreateRoom,
                            colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape   = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("สร้างห้องเรียน")
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.MeetingRoom, null,
                        tint = PrimaryBlue, modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "${rooms.size} ห้องเรียน",
                        color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }

            items(rooms) { room ->
                RoomListCard(room = room, onClick = { onRoomClick(room) })
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RoomListCard — การ์ดรายการห้อง (ครูเลือก)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RoomListCard(room: ClassRoom, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon badge
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = PrimaryBlue.copy(alpha = 0.12f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.School, null,
                        tint     = PrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Room info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    room.name,
                    color      = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 15.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                if (room.teacherName.isNotBlank())
                    Text(
                        "อ.${room.teacherName}",
                        color    = QTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                Surface(
                    shape  = RoundedCornerShape(4.dp),
                    color  = PrimaryBlue.copy(0.08f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        room.id,
                        color      = PrimaryBlue,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (room.description.isNotBlank())
                    Text(
                        room.description,
                        color    = QTheme.colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
            }

            // Arrow
            Icon(
                Icons.Default.ChevronRight, null,
                tint = QTheme.colors.textSecondary, modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ApprovedMembersSection — รายชื่อสมาชิกที่อนุมัติแล้ว (ครู + นักเรียน)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ApprovedMembersSection(
    members: List<StudentProfile>,
    myDeviceId: String,
    isEffTeacher: Boolean,
    isServerOwner: Boolean,
    onRemove: (deviceId: String) -> Unit,
    onLeave: () -> Unit
) {
    val teachers = members.filter { it.role == StudentProfile.ROLE_TEACHER }
    val students = members.filter { it.role == StudentProfile.ROLE_STUDENT }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ── Header — กดเพื่อขยาย/พับ ────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Group, null,
                    tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                Text("สมาชิกห้องเรียน",
                    color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = PrimaryBlue.copy(0.12f)) {
                    Text("${members.size} คน",
                        color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "พับ" else "ขยาย",
                    tint     = PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }

            // ── Collapsed: แสดง avatar preview ──────────────────────────────
            if (!expanded) {
                if (members.isEmpty()) {
                    Text("ยังไม่มีสมาชิกที่อนุมัติแล้ว",
                        color = QTheme.colors.textSecondary, fontSize = 12.sp)
                } else {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((-8).dp)   // overlap avatars
                    ) {
                        members.take(8).forEach { m ->
                            AvatarImage(avatarBase64 = m.avatarBase64,
                                name = m.name, size = 28.dp,
                                modifier = Modifier)
                        }
                        if (members.size > 8) {
                            Surface(
                                shape    = CircleShape,
                                color    = PrimaryBlue.copy(0.15f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("+${members.size - 8}",
                                        color    = PrimaryBlue,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("แตะเพื่อดูรายชื่อ",
                            color = QTheme.colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }

            // ── Expanded: รายชื่อเต็ม ────────────────────────────────────────
            if (expanded) {
                // ครู
                if (teachers.isNotEmpty()) {
                    Text("ครู (${teachers.size})",
                        color = QTheme.colors.textSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold)
                    teachers.forEach { member ->
                        val isSelf    = member.deviceId == myDeviceId
                        val canRemove = !isSelf && isServerOwner
                        val canLeave  = isSelf && !isServerOwner
                        MemberRow(member = member, isSelf = isSelf,
                            canRemove = canRemove, canLeave = canLeave,
                            roleColor = PrimaryBlue, roleLabel = "ครู",
                            onRemove = { onRemove(member.deviceId) }, onLeave = onLeave)
                    }
                }
                // นักเรียน
                if (students.isNotEmpty()) {
                    if (teachers.isNotEmpty())
                        HorizontalDivider(color = QTheme.colors.textSecondary.copy(0.15f))
                    Text("นักเรียน (${students.size})",
                        color = QTheme.colors.textSecondary, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold)
                    students.forEach { member ->
                        val isSelf    = member.deviceId == myDeviceId
                        val canRemove = !isSelf && isEffTeacher
                        val canLeave  = isSelf
                        MemberRow(member = member, isSelf = isSelf,
                            canRemove = canRemove, canLeave = canLeave,
                            roleColor = Color(0xFF4CAF50), roleLabel = "นักเรียน",
                            onRemove = { onRemove(member.deviceId) }, onLeave = onLeave)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: StudentProfile,
    isSelf: Boolean,
    canRemove: Boolean,
    canLeave: Boolean,
    roleColor: Color,
    roleLabel: String,
    onRemove: () -> Unit,
    onLeave: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AvatarImage(avatarBase64 = member.avatarBase64, name = member.name, size = 32.dp)

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.name, color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (isSelf)
                    Surface(shape = RoundedCornerShape(4.dp), color = QTheme.colors.textSecondary.copy(0.12f)) {
                        Text("คุณ", color = QTheme.colors.textSecondary, fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
            }
            // Device fingerprint — ล็อคตัวตนตามเครื่อง (ป้องกันสลับชื่อให้เพื่อน)
            Text(
                "ID: …${member.deviceId.takeLast(6)}",
                color    = QTheme.colors.textSecondary.copy(0.55f),
                fontSize = 9.sp
            )
        }

        // Role badge
        Surface(shape = RoundedCornerShape(6.dp), color = roleColor.copy(0.12f)) {
            Text(roleLabel, color = roleColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
        }

        // Action button
        when {
            canLeave -> {
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = Color(0xFFFF9800).copy(0.12f),
                    modifier = Modifier.clickable { showConfirm = true }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.ExitToApp, null,
                            tint = Color(0xFFFF9800), modifier = Modifier.size(12.dp))
                        Text("ออก", color = Color(0xFFFF9800), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            canRemove -> {
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = Color(0xFFEF5350).copy(0.10f),
                    modifier = Modifier.clickable { showConfirm = true }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.PersonRemove, null,
                            tint = Color(0xFFEF5350), modifier = Modifier.size(12.dp))
                        Text("นำออก", color = Color(0xFFEF5350), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showConfirm) {
        val isLeaving = canLeave
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor   = QTheme.colors.card,
            title = {
                Text(if (isLeaving) "ออกจากห้องเรียน?" else "นำ ${member.name} ออก?",
                    color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Text(
                    if (isLeaving) "คุณต้องการออกจากห้องนี้ใช่ไหม?\nสามารถเข้าร่วมใหม่ได้ภายหลัง"
                    else "นำ ${member.name} ออกจากห้องเรียน?",
                    color = QTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isLeaving) onLeave() else onRemove()
                        showConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLeaving) Color(0xFFFF9800) else Color(0xFFEF5350))
                ) {
                    Text(if (isLeaving) "ออกจากห้อง" else "นำออก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("ยกเลิก", color = QTheme.colors.textSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PendingMembersCard — อนุมัติสมาชิกรอเข้าห้อง
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PendingMembersCard(
    pending: List<StudentProfile>,
    approved: List<StudentProfile>,
    onApproveStudent: (deviceId: String) -> Unit,
    onApproveTeacher: (deviceId: String) -> Unit,
    onReject: (deviceId: String) -> Unit,
    onTransfer: (oldDeviceId: String, newDeviceId: String) -> Unit
) {
    // state สำหรับ dialog ย้ายเครื่อง — เก็บ pending student ที่กำลังจะย้าย
    var transferTarget by remember { mutableStateOf<StudentProfile?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(0.08f))
    ) {
        Column(modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFFF9800).copy(0.2f)) {
                    Text("${pending.size}",
                        color = Color(0xFFFF9800), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Text("สมาชิกรอการอนุมัติ",
                    color = Color(0xFFFF9800), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            pending.forEach { student ->
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = QTheme.colors.card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        // ── แถวบน: avatar + ชื่อ + ปุ่มหลัก ────────────────
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AvatarImage(avatarBase64 = student.avatarBase64,
                                name = student.name, size = 36.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(student.name, color = QTheme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("ID: …${student.deviceId.takeLast(6)}",
                                    color = QTheme.colors.textSecondary.copy(0.55f), fontSize = 9.sp)
                            }
                            // อนุมัติเป็นนักเรียน
                            Surface(shape = RoundedCornerShape(8.dp),
                                color    = Color(0xFF4CAF50).copy(0.15f),
                                modifier = Modifier.clickable { onApproveStudent(student.deviceId) }
                            ) {
                                Text("นักเรียน", color = Color(0xFF4CAF50), fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                            // อนุมัติเป็นครู
                            Surface(shape = RoundedCornerShape(8.dp),
                                color    = PrimaryBlue.copy(0.15f),
                                modifier = Modifier.clickable { onApproveTeacher(student.deviceId) }
                            ) {
                                Text("ครู", color = PrimaryBlue, fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                            // ปฏิเสธ
                            Surface(shape = RoundedCornerShape(8.dp),
                                color    = Color(0xFFEF5350).copy(0.12f),
                                modifier = Modifier.clickable { onReject(student.deviceId) }
                            ) {
                                Icon(Icons.Default.Close, null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.padding(5.dp).size(14.dp))
                            }
                        }

                        // ── แถวล่าง: ปุ่มย้ายเครื่อง (แสดงเฉพาะเมื่อมีสมาชิกเดิม) ──
                        if (approved.isNotEmpty()) {
                            Surface(
                                shape    = RoundedCornerShape(8.dp),
                                color    = Color(0xFF9C27B0).copy(0.10f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { transferTarget = student }
                            ) {
                                Row(
                                    modifier              = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.PhoneAndroid, null,
                                        tint     = Color(0xFF9C27B0),
                                        modifier = Modifier.size(14.dp))
                                    Text("แทนที่เครื่องเก่าที่พัง",
                                        color      = Color(0xFF9C27B0),
                                        fontSize   = 11.sp,
                                        fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.weight(1f))
                                    Text("เลือกสมาชิก →",
                                        color    = Color(0xFF9C27B0).copy(0.7f),
                                        fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialog เลือกสมาชิกที่จะแทนที่ ────────────────────────────────────────
    transferTarget?.let { newStudent ->
        TransferDeviceDialog(
            newStudent = newStudent,
            candidates = approved,
            onConfirm  = { oldStudent ->
                onTransfer(oldStudent.deviceId, newStudent.deviceId)
                transferTarget = null
            },
            onDismiss  = { transferTarget = null }
        )
    }
}

@Composable
private fun TransferDeviceDialog(
    newStudent: StudentProfile,
    candidates: List<StudentProfile>,
    onConfirm: (oldStudent: StudentProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<StudentProfile?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        icon  = { Icon(Icons.Default.PhoneAndroid, null, tint = Color(0xFF9C27B0)) },
        title = {
            Text("แทนที่เครื่องเก่าที่พัง",
                color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ข้อมูลเครื่องใหม่
                Surface(shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF9C27B0).copy(0.08f)) {
                    Row(modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AvatarImage(avatarBase64 = newStudent.avatarBase64,
                            name = newStudent.name, size = 32.dp)
                        Column {
                            Text("เครื่องใหม่: ${newStudent.name}",
                                color = QTheme.colors.textPrimary, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                            Text("ID: …${newStudent.deviceId.takeLast(6)}",
                                color = QTheme.colors.textSecondary.copy(0.6f), fontSize = 9.sp)
                        }
                    }
                }

                Text("เลือกสมาชิกเดิมที่จะย้ายตัวตนมาให้เครื่องใหม่:",
                    color = QTheme.colors.textSecondary, fontSize = 12.sp)

                // รายชื่อสมาชิกที่อนุมัติแล้ว
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    candidates.forEach { candidate ->
                        val isSelected = selected?.deviceId == candidate.deviceId
                        val roleColor  = if (candidate.role == StudentProfile.ROLE_TEACHER)
                            PrimaryBlue else Color(0xFF4CAF50)
                        Surface(
                            shape    = RoundedCornerShape(10.dp),
                            color    = if (isSelected) Color(0xFF9C27B0).copy(0.12f)
                                       else QTheme.colors.background,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = candidate }
                        ) {
                            Row(modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AvatarImage(avatarBase64 = candidate.avatarBase64,
                                    name = candidate.name, size = 36.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(candidate.name, color = QTheme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("ID: …${candidate.deviceId.takeLast(6)}",
                                        color = QTheme.colors.textSecondary.copy(0.55f), fontSize = 9.sp)
                                }
                                Surface(shape = RoundedCornerShape(4.dp),
                                    color = roleColor.copy(0.12f)) {
                                    Text(
                                        if (candidate.role == StudentProfile.ROLE_TEACHER) "ครู" else "นักเรียน",
                                        color = roleColor, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                }
                                if (isSelected)
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint = Color(0xFF9C27B0), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (selected != null) {
                    Surface(shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEF5350).copy(0.08f)) {
                        Text(
                            "⚠️  ชื่อ/บทบาทของ ${selected!!.name} จะถูกย้ายมาให้เครื่องใหม่\n" +
                            "เครื่องเก่า (…${selected!!.deviceId.takeLast(6)}) จะถูกลบออกจากห้อง",
                            color    = Color(0xFFEF5350),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { selected?.let { onConfirm(it) } },
                enabled  = selected != null,
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
            ) {
                Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("ย้ายตัวตน")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = QTheme.colors.textSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// QR Display Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun QrDisplayDialog(
    roomId: String,
    roomName: String,
    teacherUid: String,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    // format ใหม่: "PCR|{teacherUid}|{roomId}"
    val qrContent = if (teacherUid.isNotBlank())
        "$QR_PREFIX$QR_SEP$teacherUid$QR_SEP$roomId"
    else roomId

    val qrBitmap = remember(qrContent) { QrUtil.generateQrBitmap(qrContent, size = 512) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.QrCode2, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Text("QR ห้องเรียน", color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter            = remember(qrBitmap) { BitmapPainter(qrBitmap.asImageBitmap()) },
                    contentDescription = "QR Code ห้อง $roomId",
                    modifier           = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp))
                )
                Text(roomName, color = PrimaryBlue, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Surface(shape = RoundedCornerShape(8.dp), color = PrimaryBlue.copy(0.1f)) {
                    Text(
                        "Room ID: $roomId",
                        color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                Text(
                    "ให้นักเรียนสแกน QR นี้เพื่อเข้าร่วมห้องเรียนโดยอัตโนมัติ",
                    color = QTheme.colors.textSecondary, fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "เข้าร่วมห้องเรียน: $roomName\nRoom ID: $roomId")
                                }, "แชร์ Room ID"
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("แชร์ Room ID", color = PrimaryBlue)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ปิด", color = QTheme.colors.textSecondary) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Firestore Rules Guide Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FirestoreRulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        icon  = { Icon(Icons.Default.Security, null, tint = PrimaryBlue) },
        title = {
            Text("ตั้งค่า Firestore Security Rules",
                color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "ต้องตั้ง Security Rules ก่อน มิฉะนั้นการสร้างห้อง / บันทึกข้อมูลจะล้มเหลว",
                    color = QTheme.colors.textSecondary, fontSize = 13.sp
                )

                // ── ขั้นตอน ─────────────────────────────────────────────────
                StepItem("1", "เปิด Firebase Console → เลือก project ของคุณ")
                StepItem("2", "ไปที่  Firestore Database → แท็บ Rules")
                StepItem("3", "ลบ rules เดิมทั้งหมด แล้ว วาง rules ด้านล่างแทน")
                StepItem("4", "กดปุ่ม  Publish")

                // ── Rules ที่ถูกต้อง ─────────────────────────────────────────
                Text("Rules ที่ต้องใช้",
                    color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF1A2A1A)) {
                    Text(
                        text = "rules_version = '2';\n" +
                               "service cloud.firestore {\n" +
                               "  match /databases/{database}/documents {\n" +
                               "    match /rooms/{document=**} {\n" +
                               "      allow read, write: if true;\n" +
                               "    }\n" +
                               "  }\n" +
                               "}",
                        color      = Color(0xFF80CBC4),
                        fontSize   = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier   = Modifier.padding(12.dp)
                    )
                }

                // ── ปัญหาที่พบบ่อย ───────────────────────────────────────────
                Card(
                    shape  = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF5350).copy(0.08f))
                ) {
                    Column(modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.ErrorOutline, null,
                                tint = Color(0xFFEF5350), modifier = Modifier.size(14.dp))
                            Text("Rules ที่ทำให้สร้างห้องไม่ได้",
                                color = Color(0xFFEF5350), fontSize = 11.sp,
                                fontWeight = FontWeight.Bold)
                        }
                        // Rules ผิด
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF2A1A1A)) {
                            Text(
                                text = "allow read: if true;\nallow write: if false;  ← ❌ ห้าม write",
                                color      = Color(0xFFEF9A9A),
                                fontSize   = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier   = Modifier.padding(8.dp)
                            )
                        }
                        Text(
                            "Rules แบบนี้บล็อก write ทั้งหมด — สร้างห้อง / บันทึกคะแนน / ส่งงาน ล้มเหลวทุกอย่าง",
                            color = Color(0xFFEF5350), fontSize = 10.sp
                        )
                    }
                }

                Text(
                    "⚠️ Rules นี้เหมาะสำหรับห้องเรียนส่วนตัว ไม่ควรใช้กับข้อมูลสาธารณะ",
                    color = QTheme.colors.textSecondary, fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("เข้าใจแล้ว", color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun StepItem(number: String, text: String) {
    Row(
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(50), color = PrimaryBlue) {
            Text(
                number, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(text, color = QTheme.colors.textPrimary, fontSize = 12.sp,
            modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NotConnectedCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NotConnectedCard(onGoToTeacherLogin: () -> Unit, onScanQr: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(
            modifier            = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.School, null,
                tint = QTheme.colors.textSecondary, modifier = Modifier.size(48.dp))
            Text(
                "เข้าร่วมห้องเรียน",
                color = QTheme.colors.textPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )

            // นักเรียน
            Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
                Text("นักเรียน", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick  = onScanQr,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("สแกน QR Code ของครู")
                }
            }

            HorizontalDivider(color = QTheme.colors.textSecondary.copy(0.2f))

            // ครู
            Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Alignment.CenterHorizontally) {
                Text("ครู", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick  = onGoToTeacherLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("เข้าสู่ระบบครู")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ReconnectingCard — แสดงขณะนักเรียนมี context ห้องแต่ยังเชื่อมต่อไม่สำเร็จ
// หลัง 8 วินาทีถ้ายังไม่เชื่อมต่อ → แสดงปุ่มสแกน QR ใหม่
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ReconnectingCard(onScanQr: () -> Unit) {
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5_000)
        timedOut = true
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (timedOut) {
                    Surface(shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFF9800).copy(0.12f),
                        modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.WifiOff, null,
                                tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp))
                        }
                    }
                } else {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color       = PrimaryBlue
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (timedOut) "เชื่อมต่อไม่สำเร็จ" else "กำลังเชื่อมต่อ...",
                        color      = if (timedOut) Color(0xFFFF9800) else QTheme.colors.textPrimary,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (timedOut) "กรุณาสแกน QR ของห้องเรียนใหม่อีกครั้ง"
                        else          "กรุณารอสักครู่ ระบบกำลังเข้าสู่ห้องเรียน",
                        color    = QTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            if (timedOut) {
                Button(
                    onClick = onScanQr,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("สแกน QR ใหม่", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RoomFormDialog (สร้าง / แก้ไข)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RoomFormDialog(
    dialogTitle: String,
    initialRoomId: String,
    initialName: String,
    initialTeacher: String,
    initialDesc: String,
    readOnlyRoomId: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (roomId: String, name: String, teacher: String, desc: String) -> Unit
) {
    var roomId  by remember { mutableStateOf(initialRoomId.uppercase()) }
    var name    by remember { mutableStateOf(initialName) }
    var teacher by remember { mutableStateOf(initialTeacher) }
    var desc    by remember { mutableStateOf(initialDesc) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = {
            Text(dialogTitle, color = QTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value         = roomId,
                    onValueChange = { if (!readOnlyRoomId) roomId = it.uppercase() },
                    label         = { Text("Room ID *  เช่น MATH101") },
                    singleLine    = true, readOnly = readOnlyRoomId,
                    modifier      = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp), colors = classroomTfColors()
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("ชื่อห้องเรียน *") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp), colors = classroomTfColors()
                )
                OutlinedTextField(
                    value = teacher, onValueChange = { teacher = it },
                    label = { Text("ชื่ออาจารย์") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp), colors = classroomTfColors()
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("คำอธิบาย (ไม่บังคับ)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3, shape = RoundedCornerShape(10.dp), colors = classroomTfColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(roomId.trim(), name.trim(), teacher.trim(), desc.trim()) },
                enabled  = roomId.isNotBlank() && name.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text("บันทึก") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = QTheme.colors.textSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ClassFeatureCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ClassFeatureCard(
    icon: ImageVector, label: String, color: Color,
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(90.dp).clickable(onClick = onClick),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FeatureGrid — ตารางฟีเจอร์ห้องเรียน (ใช้ร่วมกันทั้งครูและนักเรียนที่อนุมัติแล้ว)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FeatureGrid(
    isTeacher: Boolean,
    onScheduleClick: () -> Unit,
    onHomeworkClick: () -> Unit,
    onAttendanceClick: () -> Unit,
    onGradesClick: () -> Unit,
    onFilesClick: () -> Unit,
    onArenaClick: () -> Unit,
    onOnlineClick: () -> Unit
) {
    Text(
        "ฟีเจอร์ห้องเรียน",
        color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold
    )
    // ครู → เห็นปุ่ม "สนามข้อสอบ" (จัดการ)
    // นักเรียน → เห็นปุ่ม "สอบออนไลน์" (ทำข้อสอบ)
    val lastIcon   = if (isTeacher) Icons.Default.SportsEsports else Icons.Default.Quiz
    val lastLabel  = if (isTeacher) stringResource(R.string.classroom_feature_arena) else "สอบออนไลน์"
    val lastAction = if (isTeacher) onArenaClick else onOnlineClick
    val features = listOf(
        Triple(Icons.Default.CalendarMonth, stringResource(R.string.classroom_feature_schedule),   onScheduleClick),
        Triple(Icons.Default.Assignment,    stringResource(R.string.classroom_feature_homework),   onHomeworkClick),
        Triple(Icons.Default.HowToReg,      stringResource(R.string.classroom_feature_attendance), onAttendanceClick),
        Triple(Icons.Default.BarChart,      stringResource(R.string.classroom_feature_grades),     onGradesClick),
        Triple(Icons.Default.FolderOpen,    stringResource(R.string.classroom_feature_files),      onFilesClick),
        Triple(lastIcon,                    lastLabel,                                              lastAction),
    )
    val featureColors = listOf(
        Color(0xFF1976D2), Color(0xFF7B1FA2), Color(0xFF388E3C),
        Color(0xFFF57C00), Color(0xFF0097A7), Color(0xFFE64A19)
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        features.chunked(2).forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { colIdx, (icon, label, onClick) ->
                    ClassFeatureCard(
                        icon     = icon,
                        label    = label,
                        color    = featureColors[rowIdx * 2 + colIdx],
                        onClick  = onClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WaitingApprovalCard — นักเรียนที่ลงทะเบียนแล้ว รอครูอนุมัติ
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun WaitingApprovalCard(name: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(0.08f))
    ) {
        Column(
            modifier            = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                color       = Color(0xFFFF9800),
                modifier    = Modifier.size(52.dp),
                strokeWidth = 3.dp
            )
            Text(
                "รอครูอนุมัติ",
                color      = Color(0xFFFF9800),
                fontSize   = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (name.isNotBlank()) {
                Text(
                    "สวัสดี $name! 👋",
                    color      = QTheme.colors.textPrimary,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "คำขอเข้าร่วมของคุณถูกส่งไปยังครูแล้ว\nกรุณารอให้ครูอนุมัติก่อนเข้าใช้งานห้องเรียน",
                color     = QTheme.colors.textSecondary,
                fontSize  = 13.sp,
                textAlign = TextAlign.Center
            )
            // ── ป้าย status ───────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFF9800).copy(0.18f)
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.HourglassEmpty, null,
                        tint     = Color(0xFFFF9800),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Pending",
                        color      = Color(0xFFFF9800),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RegisterCard — นักเรียนที่ยังไม่ได้ตั้งชื่อ / ยังไม่ลงทะเบียน
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RegisterCard(onRegister: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(
            modifier            = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = PrimaryBlue.copy(0.12f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PersonAdd, null,
                        tint     = PrimaryBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Text(
                "ระบุชื่อเพื่อเข้าร่วม",
                color      = QTheme.colors.textPrimary,
                fontSize   = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "กรุณาตั้งชื่อและรูปโปรไฟล์ของคุณ\nครูจะต้องอนุมัติก่อนจึงจะเข้าใช้งานได้",
                color     = QTheme.colors.textSecondary,
                fontSize  = 13.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick  = onRegister,
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape    = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ตั้งชื่อและเข้าร่วม", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// classroomTfColors — shared ทุก classroom screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun classroomTfColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = PrimaryBlue,
    unfocusedBorderColor = QTheme.colors.textSecondary.copy(0.4f),
    focusedTextColor     = QTheme.colors.textPrimary,
    unfocusedTextColor   = QTheme.colors.textPrimary
)

// ─────────────────────────────────────────────────────────────────────────────
// AvatarImage — วงกลมแสดงรูปโปรไฟล์นักเรียน (base64) หรือตัวอักษรตัวแรก
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun AvatarImage(
    avatarBase64: String,
    name: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatarBase64) {
        if (avatarBase64.isBlank()) null
        else runCatching {
            val data  = avatarBase64.substringAfter(",")
            val bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier         = modifier
            .size(size)
            .clip(CircleShape)
            .background(PrimaryBlue.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap             = bitmap,
                contentDescription = name,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop
            )
        } else {
            Text(
                text       = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color      = PrimaryBlue,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = (size.value * 0.42f).sp
            )
        }
    }
}
