package com.petcompanion.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.data.firebase.AttendanceCheckIn
import com.petcompanion.data.firebase.AttendanceSession
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.theme.QTheme
import com.petcompanion.presentation.viewmodel.ClassroomViewModel
import java.text.SimpleDateFormat
import java.util.*

private val AttGreen = Color(0xFF388E3C)

@Composable
fun AttendanceScreen(
    viewModel: ClassroomViewModel,
    onBackClick: () -> Unit
) {
    val sessions             by viewModel.attSessions.collectAsState()
    val checkIns             by viewModel.selectedSessionCheckIns.collectAsState()
    val isTeacher            by viewModel.isEffectiveTeacher.collectAsState()
    val teacherError         by viewModel.teacherError.collectAsState()
    val studentName          by viewModel.studentName.collectAsState()
    // ✅ Firestore-backed — คงอยู่แม้ปิด-เปิด app ใหม่ (แทน local Set)
    val myCheckedSessionIds  by viewModel.myCheckedSessionIds.collectAsState()
    val snackbarHostState    = remember { SnackbarHostState() }

    var showCreateDialog  by remember { mutableStateOf(false) }
    var showNameDialog    by remember { mutableStateOf(false) }
    var scoreTargetId     by remember { mutableStateOf<String?>(null) }
    // ครูเลือก session เพื่อดู check-ins
    var expandedSessionId by remember { mutableStateOf<String?>(null) }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale("th", "TH")) }
    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale("th", "TH")) }

    val openSessions   = sessions.filter { it.isOpen }
    val closedSessions = sessions.filter { !it.isOpen }

    // เมื่อ expandedSessionId เปลี่ยน → โหลด check-ins
    LaunchedEffect(expandedSessionId) {
        viewModel.selectAttSession(expandedSessionId)
    }
    LaunchedEffect(teacherError) {
        teacherError?.let { viewModel.clearTeacherError(); snackbarHostState.showSnackbar(it) }
    }
    // ✅ โหลด checked-state จาก Firestore เมื่อเปิดหน้าหรือเมื่อ sessions เปลี่ยน
    // (ทำให้ "เช็คแล้ว ✓" ยังคงอยู่แม้ปิด-เปิด app ใหม่)
    LaunchedEffect(sessions) {
        if (!isTeacher) viewModel.refreshMyCheckIns()
    }

    Scaffold(
        containerColor = QTheme.colors.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isTeacher) {
                FloatingActionButton(
                    onClick        = { showCreateDialog = true },
                    containerColor = AttGreen,
                    contentColor   = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "เปิดเซสชันเช็คชื่อ") }
            }
        },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, null, tint = QTheme.colors.textPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.attendance_title),
                        color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (isTeacher) "${openSessions.size} เซสชันเปิดอยู่"
                        else if (openSessions.isNotEmpty()) "มีเซสชันเช็คชื่อเปิดอยู่!"
                        else stringResource(R.string.attendance_subtitle),
                        color = if (!isTeacher && openSessions.isNotEmpty()) AttGreen
                               else QTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                if (isTeacher) {
                    Surface(shape = RoundedCornerShape(6.dp), color = AttGreen.copy(0.15f)) {
                        Text("ครู", color = AttGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                } else if (studentName.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(6.dp), color = PrimaryBlue.copy(0.1f)) {
                        Text(studentName, color = PrimaryBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), maxLines = 1)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ══════════════════════════════════════════════════════════════
            // STUDENT VIEW
            // ══════════════════════════════════════════════════════════════
            if (!isTeacher) {
                // ── ตั้งชื่อก่อน ──────────────────────────────────────────
                if (studentName.isBlank()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = AttGreen.copy(0.1f))) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = AttGreen, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("กรอกชื่อของคุณก่อน", color = AttGreen,
                                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("จะสามารถเช็คชื่อได้หลังจากตั้งชื่อ",
                                        color = QTheme.colors.textSecondary, fontSize = 11.sp)
                                }
                                Button(onClick = { showNameDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = AttGreen),
                                    shape = RoundedCornerShape(8.dp), modifier = Modifier.height(34.dp)) {
                                    Text("ตั้งชื่อ", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // ── เซสชันที่เปิดอยู่ ─────────────────────────────────────
                if (openSessions.isNotEmpty()) {
                    item {
                        Text("เซสชันเช็คชื่อที่เปิดอยู่",
                            color = AttGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    items(openSessions) { session ->
                        val alreadyChecked = session.id in myCheckedSessionIds
                        StudentCheckInCard(
                            session        = session,
                            alreadyChecked = alreadyChecked,
                            studentName    = studentName,
                            timeFmt        = timeFmt,
                            onCheckIn      = {
                                if (studentName.isBlank()) {
                                    showNameDialog = true
                                } else {
                                    viewModel.checkIn(session.id)
                                    // optimistic update อยู่ใน ViewModel แล้ว
                                }
                            }
                        )
                    }
                } else {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)) {
                            Column(modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.EventBusy, null,
                                    tint = QTheme.colors.textSecondary, modifier = Modifier.size(48.dp))
                                Text("ไม่มีเซสชันเช็คชื่อที่เปิดอยู่",
                                    color = QTheme.colors.textSecondary, fontWeight = FontWeight.SemiBold)
                                Text("รอครูเปิดเซสชันก่อนจึงจะเช็คชื่อได้",
                                    color = QTheme.colors.textSecondary, fontSize = 12.sp,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                // ── เซสชันที่ปิดแล้ว (ประวัติ) ───────────────────────────
                if (closedSessions.isNotEmpty()) {
                    item {
                        Text("ประวัติเซสชัน", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    items(closedSessions) { session ->
                        val checked = session.id in myCheckedSessionIds
                        ClosedSessionRowStudent(session = session, checked = checked, dateFmt = dateFmt, timeFmt = timeFmt)
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════
            // TEACHER VIEW
            // ══════════════════════════════════════════════════════════════
            else {
                if (sessions.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.HowToReg, null,
                                    tint = QTheme.colors.textSecondary, modifier = Modifier.size(72.dp))
                                Text("ยังไม่มีเซสชันเช็คชื่อ",
                                    color = QTheme.colors.textSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("กด + เพื่อเปิดเซสชันใหม่",
                                    color = QTheme.colors.textSecondary, fontSize = 12.sp)
                                Button(onClick = { showCreateDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = AttGreen),
                                    shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("เปิดเซสชันเช็คชื่อ")
                                }
                            }
                        }
                    }
                } else {
                    // ── เซสชันที่เปิดอยู่ ─────────────────────────────────
                    if (openSessions.isNotEmpty()) {
                        item {
                            Text("เซสชันที่เปิดอยู่ (${openSessions.size})",
                                color = AttGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        items(openSessions) { session ->
                            TeacherSessionCard(
                                session     = session,
                                isExpanded  = expandedSessionId == session.id,
                                checkIns    = if (expandedSessionId == session.id) checkIns else emptyList(),
                                timeFmt     = timeFmt,
                                onToggle    = { viewModel.toggleAttSession(session.id, false) },
                                onExpand    = {
                                    expandedSessionId = if (expandedSessionId == session.id) null else session.id
                                },
                                onDelete    = { viewModel.deleteAttSession(session.id) },
                                onScore     = { scoreTargetId = session.id }
                            )
                        }
                    }

                    // ── เซสชันที่ปิดแล้ว ──────────────────────────────────
                    if (closedSessions.isNotEmpty()) {
                        item {
                            Text("⬜ เซสชันที่ปิดแล้ว (${closedSessions.size})",
                                color = QTheme.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        items(closedSessions) { session ->
                            TeacherSessionCard(
                                session     = session,
                                isExpanded  = expandedSessionId == session.id,
                                checkIns    = if (expandedSessionId == session.id) checkIns else emptyList(),
                                timeFmt     = timeFmt,
                                onToggle    = { viewModel.toggleAttSession(session.id, true) },
                                onExpand    = {
                                    expandedSessionId = if (expandedSessionId == session.id) null else session.id
                                },
                                onDelete    = { viewModel.deleteAttSession(session.id) },
                                onScore     = { scoreTargetId = session.id }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────
    if (showCreateDialog) {
        CreateSessionDialog(
            defaultDate = viewModel.todayString(),
            onDismiss   = { showCreateDialog = false },
            onConfirm   = { label ->
                viewModel.createAttSession(label)
                showCreateDialog = false
            }
        )
    }
    if (showNameDialog) {
        StudentNameDialog(
            current   = studentName,
            onDismiss = { showNameDialog = false },
            onConfirm = { name, avatar ->
                viewModel.saveStudentName(name, avatar)
                showNameDialog = false
            }
        )
    }

    scoreTargetId?.let { sid ->
        AttScoreDialog(
            onDismiss = { scoreTargetId = null },
            onConfirm = { presentScore, absentScore, subject ->
                viewModel.addAttendanceScore(sid, presentScore, absentScore, subject)
                scoreTargetId = null
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// StudentCheckInCard — นักเรียนเห็นเซสชันที่เปิด
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StudentCheckInCard(
    session: AttendanceSession,
    alreadyChecked: Boolean,
    studentName: String,
    timeFmt: SimpleDateFormat,
    onCheckIn: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (alreadyChecked) AttGreen.copy(0.08f) else AttGreen.copy(0.14f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulse indicator
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AttGreen))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.label.ifBlank { "เซสชันเช็คชื่อ" }, color = AttGreen,
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("${session.date}  |  เปิดเมื่อ ${timeFmt.format(Date(session.openedAt))}",
                        color = QTheme.colors.textSecondary, fontSize = 11.sp)
                }
                Surface(shape = RoundedCornerShape(20.dp),
                    color = if (alreadyChecked) AttGreen.copy(0.2f) else AttGreen.copy(0.15f)) {
                    Text(if (alreadyChecked) "เช็คแล้ว ✓" else "กำลังเปิด",
                        color = AttGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            if (alreadyChecked) {
                // แสดงว่าเช็คแล้ว
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AttGreen, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("เช็คชื่อสำเร็จแล้ว", color = AttGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick  = onCheckIn,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AttGreen)
                ) {
                    Icon(Icons.Default.HowToReg, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("เช็คชื่อ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (studentName.isBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("* กรุณาตั้งชื่อก่อนเช็คชื่อ",
                        color = Color(0xFFFF9800), fontSize = 11.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ClosedSessionRowStudent — ประวัติสำหรับนักเรียน
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ClosedSessionRowStudent(
    session: AttendanceSession,
    checked: Boolean,
    dateFmt: SimpleDateFormat,
    timeFmt: SimpleDateFormat
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape)
                .background(if (checked) AttGreen else QTheme.colors.textSecondary.copy(0.4f)))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.label.ifBlank { "เซสชัน" }, color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(session.date, color = QTheme.colors.textSecondary, fontSize = 11.sp)
            }
            Surface(shape = RoundedCornerShape(6.dp),
                color = if (checked) AttGreen.copy(0.12f) else Color(0xFFEF5350).copy(0.1f)) {
                Text(if (checked) "เข้าร่วม ✓" else "ไม่ได้เช็ค",
                    color = if (checked) AttGreen else Color(0xFFEF5350),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TeacherSessionCard — ครูจัดการเซสชัน
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TeacherSessionCard(
    session: AttendanceSession,
    isExpanded: Boolean,
    checkIns: List<AttendanceCheckIn>,
    timeFmt: SimpleDateFormat,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onDelete: () -> Unit,
    onScore: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showToggleConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (session.isOpen) AttGreen.copy(0.08f) else QTheme.colors.card)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Header ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape)
                    .background(if (session.isOpen) AttGreen else QTheme.colors.textSecondary.copy(0.4f)))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.label.ifBlank { "เซสชันเช็คชื่อ" },
                        color = if (session.isOpen) AttGreen else QTheme.colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text(session.date, color = QTheme.colors.textSecondary, fontSize = 11.sp)
                }
                // Check-in count badge
                Surface(shape = RoundedCornerShape(20.dp), color = AttGreen.copy(0.15f)) {
                    Text("${if (isExpanded) checkIns.size else "?"} คน",
                        color = AttGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                // Expand check-ins
                IconButton(onClick = onExpand, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = QTheme.colors.textSecondary
                    )
                }
                // Delete
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFEF5350),
                        modifier = Modifier.size(18.dp))
                }
            }

            // ── Time info ─────────────────────────────────────────────────
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (session.openedAt > 0)
                    Text("เปิด: ${timeFmt.format(Date(session.openedAt))}",
                        color = AttGreen, fontSize = 11.sp)
                if (!session.isOpen && session.closedAt > 0)
                    Text("ปิด: ${timeFmt.format(Date(session.closedAt))}",
                        color = Color(0xFFEF5350), fontSize = 11.sp)
            }

            Spacer(Modifier.height(10.dp))
            // ── Toggle open/close button ──────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick  = { showToggleConfirm = true },
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = if (session.isOpen) Color(0xFFEF5350) else AttGreen)
                ) {
                    Icon(
                        if (session.isOpen) Icons.Default.LockOpen else Icons.Default.Lock,
                        null, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (session.isOpen) "ปิดเซสชัน" else "เปิดอีกครั้ง", fontSize = 11.sp)
                }
                // ให้คะแนนตามการเช็คชื่อ
                OutlinedButton(
                    onClick  = onScore,
                    modifier = Modifier.height(38.dp),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF57C00)),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF57C00).copy(0.6f))
                ) {
                    Icon(Icons.Default.Grade, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ให้คะแนน", fontSize = 11.sp)
                }
            }

            // ── Check-in list (expand) ────────────────────────────────────
            if (isExpanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = QTheme.colors.textSecondary.copy(0.2f))
                Spacer(Modifier.height(8.dp))
                if (checkIns.isEmpty()) {
                    Text("ยังไม่มีนักเรียนเช็คชื่อ", color = QTheme.colors.textSecondary,
                        fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                } else {
                    Text("นักเรียนที่เช็คชื่อ (${checkIns.size} คน):",
                        color = AttGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    checkIns.forEachIndexed { idx, ci ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${idx + 1}.", color = QTheme.colors.textSecondary, fontSize = 12.sp,
                                modifier = Modifier.width(24.dp))
                            Icon(Icons.Default.Person, null, tint = AttGreen,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(ci.studentName, color = QTheme.colors.textPrimary,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(timeFmt.format(Date(ci.checkedAt)),
                                color = QTheme.colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // ── Confirm toggle ────────────────────────────────────────────────────
    if (showToggleConfirm) {
        AlertDialog(
            onDismissRequest = { showToggleConfirm = false },
            containerColor   = QTheme.colors.card,
            title = {
                Text(if (session.isOpen) "ปิดเซสชัน?" else "เปิดเซสชันอีกครั้ง?",
                    color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    if (session.isOpen) "นักเรียนจะไม่สามารถเช็คชื่อได้อีก\nหลังจากปิดเซสชันนี้"
                    else "นักเรียนจะเช็คชื่อได้อีกครั้ง",
                    color = QTheme.colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { onToggle(); showToggleConfirm = false },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = if (session.isOpen) Color(0xFFEF5350) else AttGreen)
                ) { Text(if (session.isOpen) "ปิดเซสชัน" else "เปิดอีกครั้ง") }
            },
            dismissButton = {
                TextButton(onClick = { showToggleConfirm = false }) {
                    Text("ยกเลิก", color = QTheme.colors.textSecondary)
                }
            }
        )
    }

    // ── Confirm delete ────────────────────────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = QTheme.colors.card,
            title = { Text("ลบเซสชัน?", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text  = {
                Text("ลบ \"${session.label.ifBlank { "เซสชันนี้" }}\"?\nข้อมูลการเช็คชื่อจะหายถาวร",
                    color = QTheme.colors.textSecondary)
            },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) { Text("ลบ") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("ยกเลิก") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AttScoreDialog — ครูให้/หักคะแนนทั้งกลุ่มตามเซสชันเช็คชื่อ
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AttScoreDialog(
    onDismiss: () -> Unit,
    onConfirm: (presentScore: Double, absentScore: Double, subject: String) -> Unit
) {
    var presentText by remember { mutableStateOf("1") }
    var absentText  by remember { mutableStateOf("0") }
    var subject     by remember { mutableStateOf("เช็คชื่อ") }

    val presentVal = presentText.toDoubleOrNull()
    val absentVal  = absentText.toDoubleOrNull()
    val isValid    = presentVal != null && absentVal != null && subject.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        icon  = { Icon(Icons.Default.Grade, null, tint = Color(0xFFF57C00)) },
        title = {
            Text("ให้คะแนนตามการเช็คชื่อ",
                color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape  = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF57C00).copy(0.08f))
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null,
                            tint = Color(0xFFF57C00), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ระบบจะเพิ่มคะแนนให้นักเรียนทุกคนที่ลงทะเบียน\nโดยอัตโนมัติจากรายชื่อเช็คชื่อ",
                            color = Color(0xFFF57C00), fontSize = 11.sp
                        )
                    }
                }
                OutlinedTextField(
                    value         = subject,
                    onValueChange = { subject = it },
                    label         = { Text("วิชา / หมวด") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    colors        = classroomTfColors()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value           = presentText,
                        onValueChange   = { presentText = it },
                        label           = { Text("คะแนนคนที่มา") },
                        singleLine      = true,
                        modifier        = Modifier.weight(1f),
                        shape           = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors          = classroomTfColors()
                    )
                    OutlinedTextField(
                        value           = absentText,
                        onValueChange   = { absentText = it },
                        label           = { Text("คะแนนคนขาด") },
                        singleLine      = true,
                        modifier        = Modifier.weight(1f),
                        shape           = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors          = classroomTfColors()
                    )
                }
                Text(
                    "คะแนนคนขาด: 0 = ไม่ได้คะแนน, ค่าลบ = หักคะแนน",
                    color    = QTheme.colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    if (isValid)
                        onConfirm(presentVal!!, absentVal!!, subject.trim())
                },
                enabled = isValid,
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("บันทึกคะแนน")
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
// CreateSessionDialog — ครูสร้างเซสชัน
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CreateSessionDialog(
    defaultDate: String,
    onDismiss: () -> Unit,
    onConfirm: (label: String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        icon   = { Icon(Icons.Default.HowToReg, null, tint = AttGreen) },
        title  = { Text("เปิดเซสชันเช็คชื่อ", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
        text   = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("วันที่: $defaultDate", color = QTheme.colors.textSecondary, fontSize = 13.sp)
                OutlinedTextField(
                    value         = label,
                    onValueChange = { label = it },
                    label         = { Text("ชื่อเซสชัน (เช่น คาบ 1, เช้า, Quiz)") },
                    placeholder   = { Text("คาบ 1") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    colors        = classroomTfColors()
                )
                Card(shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = AttGreen.copy(0.08f))) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = AttGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("เมื่อสร้างแล้ว นักเรียนจะเห็นปุ่มเช็คชื่อทันที\nกดปิดเซสชันเมื่อต้องการหยุดรับ",
                            color = AttGreen, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(label.trim().ifBlank { "เซสชัน $defaultDate" }) },
                colors   = ButtonDefaults.buttonColors(containerColor = AttGreen)
            ) {
                Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("เปิดเซสชัน")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = QTheme.colors.textSecondary) }
        }
    )
}
