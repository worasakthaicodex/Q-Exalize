package com.petcompanion.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.data.firebase.OnlineExam
import com.petcompanion.presentation.theme.QTheme
import com.petcompanion.presentation.viewmodel.ClassroomViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private val OnlinePurple = Color(0xFF6A1B9A)

// ─────────────────────────────────────────────────────────────────────────────
// OnlineScreen — สนามสอบออนไลน์ (ดึงข้อสอบจากห้องเรียนที่เข้าอยู่)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun OnlineScreen(
    viewModel: ClassroomViewModel,
    onBackClick: () -> Unit,
    onGoToClassroom: () -> Unit = onBackClick  // default = กลับไปหน้าก่อนหน้า
) {
    val exams           by viewModel.exams.collectAsState()
    val questions       by viewModel.examQuestions.collectAsState()
    val mySubmission    by viewModel.myExamSubmission.collectAsState()
    val isConnected     by viewModel.isFirebaseConnected.collectAsState()
    val roomId          by viewModel.roomId.collectAsState()
    val studentRoomIds  by viewModel.studentRoomIds.collectAsState()
    val studentRoomsInfo by viewModel.studentRoomsInfo.collectAsState()

    // Auto-เลือกห้องถ้ายังไม่ได้เลือก
    LaunchedEffect(isConnected, roomId, studentRoomIds) {
        if (isConnected && roomId.isBlank() && studentRoomIds.isNotEmpty()) {
            if (studentRoomIds.size == 1) {
                // ห้องเดียว → เลือกเลย ไม่ต้องให้เลือก
                viewModel.selectRoom(studentRoomIds.first())
            } else {
                // หลายห้อง → โหลดข้อมูลห้องเพื่อแสดง picker
                viewModel.loadStudentRooms()
            }
        }
    }

    // state machine: null = หน้าหลัก, non-null = กำลังทำข้อสอบ
    var takingExam by remember { mutableStateOf<OnlineExam?>(null) }

    val dateFmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("th", "TH")) }

    // ── กำลังทำข้อสอบ → full-screen ──────────────────────────────────────
    val exam = takingExam
    if (exam != null) {
        ExamTakingScreen(
            exam         = exam,
            questions    = questions,
            mySubmission = mySubmission,
            showAnswers  = exam.showAnswers,
            onSubmit     = { answers ->
                viewModel.submitExam(exam.id, answers, exam)
                viewModel.loadMyExamSubmission(exam.id)
            },
            onBack = {
                takingExam = null
                viewModel.selectExam(null)
                viewModel.clearMyExamSubmission()
            }
        )
        return
    }

    // ── หน้าหลัก ──────────────────────────────────────────────────────────
    Scaffold(
        containerColor = QTheme.colors.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, null, tint = QTheme.colors.textPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "สนามสอบออนไลน์",
                        color = QTheme.colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        if (isConnected && roomId.isNotBlank()) "ห้อง: $roomId"
                        else "ยังไม่ได้เข้าห้องเรียน",
                        color = if (isConnected && roomId.isNotBlank()) OnlinePurple
                                else QTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                // badge จำนวนข้อสอบ active
                val activeCount = exams.count { it.isActive }
                if (activeCount > 0) {
                    Surface(shape = CircleShape, color = OnlinePurple) {
                        Text(
                            "$activeCount",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->

        // ── ยังไม่ได้เชื่อมต่อ Firebase เลย ─────────────────────────────────
        if (!isConnected) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.CloudOff, null,
                        tint = QTheme.colors.textSecondary, modifier = Modifier.size(64.dp))
                    Text("ยังไม่ได้สแกน QR ห้องเรียน",
                        color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("สแกน QR จากอาจารย์เพื่อเข้าร่วมห้องเรียนก่อน",
                        color = QTheme.colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Button(onClick = onGoToClassroom,
                        colors = ButtonDefaults.buttonColors(containerColor = OnlinePurple)) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ไปสแกน QR")
                    }
                }
            }
            return@Scaffold
        }

        // ── เชื่อมต่อแล้วแต่ยังไม่ได้เลือกห้อง ──────────────────────────────
        if (roomId.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // กำลัง auto-select ห้องเดียว (รอ LaunchedEffect ทำงาน)
                    studentRoomIds.size == 1 -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = OnlinePurple)
                            Text("กำลังโหลดข้อสอบ...",
                                color = QTheme.colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                    // ยังไม่ได้สแกน QR ห้องไหนเลย
                    studentRoomIds.isEmpty() -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.MeetingRoom, null,
                                tint = QTheme.colors.textSecondary, modifier = Modifier.size(64.dp))
                            Text("ยังไม่ได้เข้าห้องเรียน",
                                color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Button(onClick = onGoToClassroom,
                                colors = ButtonDefaults.buttonColors(containerColor = OnlinePurple)) {
                                Icon(Icons.Default.Login, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("ไปห้องเรียน")
                            }
                        }
                    }
                    // หลายห้อง → แสดง picker
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Text("เลือกห้องที่จะดูข้อสอบ",
                                    color = QTheme.colors.textPrimary,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("คุณเข้าอยู่ ${studentRoomIds.size} ห้อง — เลือกห้องที่ต้องการ",
                                    color = QTheme.colors.textSecondary, fontSize = 12.sp)
                            }
                            if (studentRoomsInfo.isEmpty()) {
                                // ยังโหลดชื่อห้องไม่เสร็จ → แสดงเฉพาะ ID
                                items(studentRoomIds.toList()) { rid ->
                                    RoomPickerCard(
                                        roomName    = rid,
                                        teacherName = "",
                                        onClick     = { viewModel.selectRoom(rid) }
                                    )
                                }
                            } else {
                                items(studentRoomsInfo) { entry ->
                                    RoomPickerCard(
                                        roomName    = entry.room.name.ifBlank { entry.room.id },
                                        teacherName = entry.room.teacherName,
                                        onClick     = { viewModel.selectRoom(entry.room.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            return@Scaffold
        }

        // ── มีห้องเรียน → แสดงข้อสอบ ─────────────────────────────────────
        val activeExams   = exams.filter { it.isActive }
        val upcomingExams = exams.filter { it.isUpcoming }
        val expiredExams  = exams.filter { it.isExpired && it.isPublished }

        val hasAnyVisible = activeExams.isNotEmpty() || upcomingExams.isNotEmpty() || expiredExams.isNotEmpty()
        if (!hasAnyVisible) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = OnlinePurple.copy(0.1f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Assignment, null,
                                tint = OnlinePurple, modifier = Modifier.size(36.dp))
                        }
                    }
                    Text("ยังไม่มีข้อสอบ",
                        color = QTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("อาจารย์ยังไม่ได้เปิดข้อสอบ\nรอสักครู่หรือกลับมาใหม่ภายหลัง",
                        color = QTheme.colors.textSecondary, fontSize = 13.sp,
                        textAlign = TextAlign.Center, lineHeight = 20.sp)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── ข้อสอบกำลังเปิดอยู่ ──────────────────────────────────────
            if (activeExams.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.RadioButtonChecked, null,
                            tint = Color(0xFFEF5350), modifier = Modifier.size(14.dp))
                        Text("กำลังสอบอยู่ (${activeExams.size})",
                            color = OnlinePurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                items(activeExams) { ex ->
                    ActiveOnlineExamCard(
                        exam   = ex,
                        dateFmt = dateFmt,
                        onEnter = {
                            viewModel.selectExam(ex.id)
                            viewModel.loadMyExamSubmission(ex.id)
                            takingExam = ex
                        }
                    )
                }
            }

            // ── ข้อสอบที่ยังไม่ถึงเวลาเริ่ม ──────────────────────────────
            if (upcomingExams.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AccessTime, null,
                            tint = Color(0xFFFF9800), modifier = Modifier.size(14.dp))
                        Text("รอเวลาเริ่ม (${upcomingExams.size})",
                            color = Color(0xFFFF9800), fontSize = 13.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
                items(upcomingExams) { ex ->
                    UpcomingOnlineExamCard(exam = ex, dateFmt = dateFmt)
                }
            }

            // ── ข้อสอบที่หมดเวลาแล้ว ─────────────────────────────────────
            if (expiredExams.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Lock, null,
                            tint = QTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
                        Text("หมดเวลาแล้ว",
                            color = QTheme.colors.textSecondary, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
                items(expiredExams) { ex ->
                    ExpiredOnlineExamCard(exam = ex, dateFmt = dateFmt)
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RoomPickerCard — การ์ดเลือกห้องเรียน (ใช้เมื่อนักเรียนอยู่หลายห้อง)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RoomPickerCard(
    roomName: String,
    teacherName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = OnlinePurple.copy(0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.School, null,
                        tint = OnlinePurple, modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(roomName,
                    color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (teacherName.isNotBlank()) {
                    Text("อาจารย์ $teacherName",
                        color = QTheme.colors.textSecondary, fontSize = 12.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = QTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ActiveOnlineExamCard — ข้อสอบที่กำลังเปิดอยู่ (มี countdown + ปุ่มเข้า)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ActiveOnlineExamCard(
    exam: OnlineExam,
    dateFmt: SimpleDateFormat,
    onEnter: () -> Unit
) {
    // countdown timer
    var millisLeft by remember(exam.id) { mutableLongStateOf(exam.millisRemaining()) }
    LaunchedEffect(exam.id, exam.endTime) {
        while (millisLeft > 0) { delay(1000L); millisLeft = exam.millisRemaining() }
    }
    val isUrgent = exam.endTime > 0L && millisLeft < 300_000L  // < 5 นาที

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEnter),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = OnlinePurple.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // LIVE badge
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEF5350)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(6.dp).clip(CircleShape)
                                .background(Color.White)
                        )
                        Text("LIVE", color = Color.White, fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text(
                    exam.title,
                    color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (exam.description.isNotBlank()) {
                Text(exam.description, color = QTheme.colors.textSecondary,
                    fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            // countdown + deadline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (exam.endTime > 0L) {
                    val h = millisLeft / 3_600_000L
                    val m = (millisLeft % 3_600_000L) / 60_000L
                    val s = (millisLeft % 60_000L) / 1_000L
                    val timerStr = if (h > 0) "%d:%02d:%02d".format(h,m,s) else "%d:%02d".format(m,s)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Timer, null,
                            tint = if (isUrgent) Color(0xFFEF5350) else OnlinePurple,
                            modifier = Modifier.size(14.dp))
                        Text("เหลือ $timerStr",
                            color = if (isUrgent) Color(0xFFEF5350) else OnlinePurple,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("ไม่จำกัดเวลา", color = QTheme.colors.textSecondary, fontSize = 11.sp)
                }
                if (exam.endTime > 0L) {
                    Text("ถึง ${dateFmt.format(Date(exam.endTime))}",
                        color = QTheme.colors.textSecondary, fontSize = 10.sp)
                }
            }

            // ปุ่มเข้าสอบ
            Button(
                onClick  = onEnter,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (isUrgent) Color(0xFFEF5350) else OnlinePurple
                )
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isUrgent) "เร่งด่วน! เข้าทำสอบเดี๋ยวนี้"
                    else "เข้าทำข้อสอบ",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UpcomingOnlineExamCard — ข้อสอบที่ publish แล้วแต่ยังไม่ถึงเวลาเริ่ม
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun UpcomingOnlineExamCard(exam: OnlineExam, dateFmt: SimpleDateFormat) {
    val UpcomingOrange = Color(0xFFFF9800)

    // countdown ถึงเวลาเริ่ม (อัพเดตทุก 1 วินาที)
    var msLeft by remember(exam.id) { mutableLongStateOf(exam.millisUntilStart()) }
    LaunchedEffect(exam.id, exam.startTime) {
        while (msLeft > 0) { delay(1000L); msLeft = exam.millisUntilStart() }
    }

    fun formatCountdown(ms: Long): String {
        val totalSec = ms / 1000
        val days    = totalSec / 86400
        val hours   = (totalSec % 86400) / 3600
        val minutes = (totalSec % 3600) / 60
        val secs    = totalSec % 60
        return when {
            days > 0    -> "อีก $days วัน ${hours} ชม."
            hours > 0   -> "อีก $hours ชม. ${minutes} นาที"
            minutes > 0 -> "อีก $minutes นาที ${secs} วินาที"
            else        -> "กำลังจะเริ่ม..."
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = UpcomingOrange.copy(alpha = 0.07f)
        ),
        border   = androidx.compose.foundation.BorderStroke(1.dp, UpcomingOrange.copy(0.25f))
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ไอคอนนาฬิกา
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = UpcomingOrange.copy(0.13f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AccessTime, null,
                        tint = UpcomingOrange, modifier = Modifier.size(24.dp))
                }
            }

            Column(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // ชื่อข้อสอบ
                Text(exam.title,
                    color      = QTheme.colors.textPrimary,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis)
                // Countdown
                Text(formatCountdown(msLeft),
                    color      = UpcomingOrange,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold)
                // เวลาเริ่ม
                if (exam.startTime > 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CalendarToday, null,
                            tint = QTheme.colors.textSecondary, modifier = Modifier.size(11.dp))
                        Text("เริ่ม ${dateFmt.format(Date(exam.startTime))}",
                            color    = QTheme.colors.textSecondary,
                            fontSize = 11.sp)
                        if (exam.endTime > 0L) {
                            Text("·", color = QTheme.colors.textSecondary, fontSize = 11.sp)
                            Text("ถึง ${dateFmt.format(Date(exam.endTime))}",
                                color    = QTheme.colors.textSecondary,
                                fontSize = 11.sp)
                        }
                    }
                }
            }

            // Badge "รอเวลา"
            Surface(shape = RoundedCornerShape(8.dp),
                color = UpcomingOrange.copy(0.15f)) {
                Text("รอเวลา",
                    color      = UpcomingOrange,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExpiredOnlineExamCard — ข้อสอบที่หมดเวลาแล้ว (ดูไม่ได้ / ล็อค)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ExpiredOnlineExamCard(exam: OnlineExam, dateFmt: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = QTheme.colors.textSecondary.copy(0.08f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, null,
                        tint = QTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(exam.title, color = QTheme.colors.textSecondary,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (exam.endTime > 0L)
                    Text("หมดเวลา ${dateFmt.format(Date(exam.endTime))}",
                        color = QTheme.colors.textSecondary.copy(0.7f), fontSize = 11.sp)
            }
            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEF5350).copy(0.1f)) {
                Text("หมดเวลา", color = Color(0xFFEF5350), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }
        }
    }
}
