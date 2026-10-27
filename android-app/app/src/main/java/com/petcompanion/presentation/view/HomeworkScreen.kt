package com.petcompanion.presentation.view

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.data.firebase.HomeworkItem
import com.petcompanion.data.firebase.HomeworkSubmission
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.theme.QTheme
import com.petcompanion.presentation.viewmodel.ClassroomViewModel
import java.text.SimpleDateFormat
import java.util.*

private val HwPurple = Color(0xFF7B1FA2)

@Composable
fun HomeworkScreen(
    viewModel: ClassroomViewModel,
    onBackClick: () -> Unit,
    combinedMode: Boolean = false   // true = รวมทุกห้อง (เข้าจากหน้าแรก, read-only)
) {
    val singleHomework   by viewModel.homework.collectAsState()
    val combinedHomework by viewModel.allHomework.collectAsState()
    val homework = if (combinedMode) combinedHomework else singleHomework
    val allSubmissions  by viewModel.allSubmissions.collectAsState()
    val homeworkRoomMap by viewModel.homeworkRoomMap.collectAsState()
    val isTeacher = if (combinedMode) false else viewModel.isEffectiveTeacher.collectAsState().value
    val teacherError    by viewModel.teacherError.collectAsState()
    val studentName     by viewModel.studentName.collectAsState()
    val context         = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val fmt = SimpleDateFormat("d MMM yyyy HH:mm", Locale("th", "TH"))
    val now = System.currentTimeMillis()
    val pending = homework.filter { it.dueDate >= now }
    val overdue = homework.filter { it.dueDate < now }
    // นักเรียนเห็นเฉพาะ submission ของตัวเอง; ครูเห็นทั้งหมด
    val visibleSubmissions = if (isTeacher) allSubmissions
                             else allSubmissions.filter { it.studentName == studentName || it.deviceId == viewModel.deviceId }

    var showAddDialog        by remember { mutableStateOf(false) }
    var showNameDialog       by remember { mutableStateOf(false) }
    // Teacher: which hw to view submissions
    var viewSubmissionsHw    by remember { mutableStateOf<HomeworkItem?>(null) }
    // Teacher: which submission to grade
    var gradeTarget          by remember { mutableStateOf<HomeworkSubmission?>(null) }
    // Student: which hw to submit
    var submitTarget         by remember { mutableStateOf<HomeworkItem?>(null) }

    val uploadProgress  by viewModel.uploadProgress.collectAsState()
    var isSubmitting    by remember { mutableStateOf(false) }

    LaunchedEffect(teacherError) {
        if (teacherError != null) isSubmitting = false   // reset on error
        teacherError?.let { viewModel.clearTeacherError(); snackbarHostState.showSnackbar(it) }
    }
    // auto-close submit dialog when upload finishes
    LaunchedEffect(uploadProgress) {
        if (isSubmitting && uploadProgress == null) {
            isSubmitting = false
            submitTarget = null
        }
    }

    Scaffold(
        containerColor = QTheme.colors.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isTeacher) {
                FloatingActionButton(
                    onClick        = { showAddDialog = true },
                    containerColor = HwPurple,
                    contentColor   = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "เพิ่มการบ้าน") }
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
                    Text(stringResource(R.string.homework_title),
                        color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (isTeacher) "${visibleSubmissions.count { it.status == HomeworkSubmission.STATUS_PENDING }} งานรอตรวจ"
                        else stringResource(R.string.homework_subtitle),
                        color = QTheme.colors.textSecondary, fontSize = 12.sp
                    )
                }
                if (isTeacher) {
                    Surface(shape = RoundedCornerShape(6.dp), color = HwPurple.copy(0.15f)) {
                        Text("ครู", color = HwPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold,
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
            if (homework.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.AssignmentTurnedIn, null,
                                tint = QTheme.colors.textSecondary, modifier = Modifier.size(64.dp))
                            Text(stringResource(R.string.homework_no_assignments),
                                color = QTheme.colors.textSecondary, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                // ── Student: ถ้ายังไม่ได้ตั้งชื่อ ─────────────────────────
                if (!isTeacher && studentName.isBlank()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = HwPurple.copy(0.1f))) {
                            Row(modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, tint = HwPurple, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("กรอกชื่อของคุณก่อน", color = HwPurple,
                                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("จะสามารถส่งงานได้หลังจากตั้งชื่อ",
                                        color = QTheme.colors.textSecondary, fontSize = 11.sp)
                                }
                                Button(onClick = { showNameDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = HwPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)) {
                                    Text("ตั้งชื่อ", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (pending.isNotEmpty()) {
                    item {
                        Text("งานที่ต้องส่ง (${pending.size})",
                            color = HwPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    items(pending) { hw ->
                        val hwSubs = visibleSubmissions.filter { it.hwId == hw.id }
                        HomeworkCard(
                            hw         = hw,
                            fmt        = fmt,
                            isOverdue  = false,
                            isTeacher  = isTeacher,
                            submissions = hwSubs,
                            studentName = studentName,
                            context    = context,
                            onDelete   = { viewModel.deleteHomework(hw.id) },
                            onViewSubs = { viewSubmissionsHw = hw },
                            onSubmit   = {
                                // โหมดรวม → ตั้งห้องของการบ้านชิ้นนี้ก่อนส่ง
                                if (combinedMode) homeworkRoomMap[hw.id]?.let { viewModel.saveRoomId(it) }
                                if (studentName.isBlank()) showNameDialog = true
                                else submitTarget = hw
                            },
                            onReminder = { viewModel.scheduleHomeworkReminder(hw) }
                        )
                    }
                }
                if (overdue.isNotEmpty()) {
                    item {
                        Text("⚠️ เลยกำหนดแล้ว (${overdue.size})",
                            color = Color(0xFFEF5350), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    items(overdue) { hw ->
                        val hwSubs = visibleSubmissions.filter { it.hwId == hw.id }
                        HomeworkCard(
                            hw         = hw,
                            fmt        = fmt,
                            isOverdue  = true,
                            isTeacher  = isTeacher,
                            submissions = hwSubs,
                            studentName = studentName,
                            context    = context,
                            onDelete   = { viewModel.deleteHomework(hw.id) },
                            onViewSubs = { viewSubmissionsHw = hw },
                            onSubmit   = {
                                if (combinedMode) homeworkRoomMap[hw.id]?.let { viewModel.saveRoomId(it) }
                                if (studentName.isBlank()) showNameDialog = true
                                else submitTarget = hw
                            },
                            onReminder = { viewModel.scheduleHomeworkReminder(hw) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────

    // ตั้งชื่อนักเรียน
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

    // เพิ่มการบ้าน (ครู)
    if (showAddDialog) {
        AddHomeworkDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { hw -> viewModel.addHomework(hw); showAddDialog = false }
        )
    }

    // นักเรียนส่งงาน
    submitTarget?.let { hw ->
        SubmitHomeworkDialog(
            hw             = hw,
            uploadProgress = uploadProgress,
            onDismiss      = { if (uploadProgress == null) submitTarget = null },
            onConfirm      = { answer, uri ->
                if (uri == null) {
                    // ส่งแค่ข้อความ — ปิด dialog ทันที
                    viewModel.uploadAndSubmitHomework(hw.id, hw.title, answer, null, context)
                    submitTarget = null
                } else {
                    // มีไฟล์ — คง dialog ไว้แสดง progress จนอัปโหลดเสร็จ
                    isSubmitting = true
                    viewModel.uploadAndSubmitHomework(hw.id, hw.title, answer, uri, context)
                }
            }
        )
    }

    // ครูดูการส่งงาน + ตรวจ
    viewSubmissionsHw?.let { hw ->
        val hwSubs = visibleSubmissions.filter { it.hwId == hw.id }
        SubmissionsViewDialog(
            hw          = hw,
            submissions = hwSubs,
            onDismiss   = { viewSubmissionsHw = null },
            onGrade     = { sub -> gradeTarget = sub }
        )
    }

    // ครูให้คะแนน
    gradeTarget?.let { sub ->
        GradeDialog(
            submission = sub,
            onDismiss  = { gradeTarget = null },
            onConfirm  = { grade, feedback ->
                viewModel.gradeSubmission(sub.id, grade, feedback)
                gradeTarget = null
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HomeworkCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeworkCard(
    hw: HomeworkItem,
    fmt: SimpleDateFormat,
    isOverdue: Boolean,
    isTeacher: Boolean,
    submissions: List<HomeworkSubmission>,
    studentName: String,
    context: android.content.Context,
    onDelete: () -> Unit,
    onViewSubs: () -> Unit,
    onSubmit: () -> Unit,
    onReminder: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reminderSet       by remember(hw.id) { mutableStateOf(false) }

    // สถานะของนักเรียนคนนี้
    val mySubmission = submissions.find { it.studentName == studentName }
    val pendingCount = submissions.count { it.status == HomeworkSubmission.STATUS_PENDING }
    val gradedCount  = submissions.count { it.status == HomeworkSubmission.STATUS_GRADED }

    val accentColor = when {
        isTeacher && pendingCount > 0 -> Color(0xFFFF9800)
        mySubmission != null && mySubmission.status == HomeworkSubmission.STATUS_GRADED -> Color(0xFF4CAF50)
        mySubmission != null -> HwPurple
        isOverdue -> Color(0xFFEF5350)
        else -> HwPurple
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(hw.title, color = QTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (hw.description.isNotBlank())
                        Text(hw.description, color = QTheme.colors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, null,
                            tint = accentColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${stringResource(R.string.homework_due_label)} ${fmt.format(Date(hw.dueDate))}",
                            color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
                // Badge: ครู = จำนวน submission | นักเรียน = สถานะงานตัวเอง
                if (isTeacher) {
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(shape = RoundedCornerShape(8.dp),
                            color = if (pendingCount > 0) Color(0xFFFF9800).copy(0.15f) else Color(0xFF4CAF50).copy(0.12f)) {
                            Text(
                                if (pendingCount > 0) "รอตรวจ $pendingCount" else "ตรวจแล้ว $gradedCount",
                                color = if (pendingCount > 0) Color(0xFFFF9800) else Color(0xFF4CAF50),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (submissions.isNotEmpty()) {
                            Text("${submissions.size} คนส่ง", color = QTheme.colors.textSecondary,
                                fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                } else if (mySubmission != null) {
                    Surface(shape = RoundedCornerShape(8.dp), color = accentColor.copy(0.15f)) {
                        Text(
                            if (mySubmission.status == HomeworkSubmission.STATUS_GRADED) "ตรวจแล้ว ✓" else "ส่งแล้ว ✓",
                            color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (isTeacher) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, null,
                            tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── คะแนนที่ครูให้ (นักเรียน) ─────────────────────────────────
            if (!isTeacher && mySubmission?.status == HomeworkSubmission.STATUS_GRADED) {
                Spacer(Modifier.height(8.dp))
                Card(shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(0.08f))) {
                    Column(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Grade, null,
                                tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("คะแนน: ${mySubmission.grade}", color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        if (mySubmission.feedback.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text("ความเห็นครู: ${mySubmission.feedback}",
                                color = QTheme.colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isTeacher) {
                    // ครู: ดูการส่งงาน
                    Button(
                        onClick  = onViewSubs,
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = HwPurple)
                    ) {
                        Icon(Icons.Default.Assignment, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ดูการส่งงาน (${submissions.size})", fontSize = 11.sp)
                    }
                } else {
                    // นักเรียน: ส่งงาน
                    if (mySubmission == null) {
                        Button(
                            onClick  = onSubmit,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape    = RoundedCornerShape(8.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = HwPurple)
                        ) {
                            Icon(Icons.Default.Upload, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ส่งงาน", fontSize = 11.sp)
                        }
                    } else {
                        // แก้ไขคำตอบ
                        OutlinedButton(
                            onClick  = onSubmit,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape    = RoundedCornerShape(8.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = HwPurple),
                            border   = androidx.compose.foundation.BorderStroke(1.dp, HwPurple.copy(0.5f))
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("แก้ไขคำตอบ", fontSize = 11.sp)
                        }
                    }
                    // Reminder (ถ้ายังไม่เลยกำหนดและยังไม่ได้ส่ง)
                    if (!isOverdue && mySubmission == null) {
                        OutlinedButton(
                            onClick  = { onReminder(); reminderSet = true },
                            modifier = Modifier.height(36.dp),
                            shape    = RoundedCornerShape(8.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (reminderSet) Color(0xFF4CAF50) else HwPurple),
                            border   = androidx.compose.foundation.BorderStroke(
                                1.dp, if (reminderSet) Color(0xFF4CAF50) else HwPurple.copy(0.4f))
                        ) {
                            Icon(
                                if (reminderSet) Icons.Default.NotificationsActive else Icons.Default.NotificationAdd,
                                null, modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
                // Open file
                if (hw.fileUrl.isNotBlank()) {
                    IconButton(
                        onClick  = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hw.fileUrl))) },
                        modifier = Modifier.size(36.dp)
                    ) { Icon(Icons.Default.FileOpen, null, tint = Color(0xFFF57C00)) }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = QTheme.colors.card,
            title  = { Text("ลบการบ้าน?", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text   = { Text("ต้องการลบ \"${hw.title}\"?\n(การส่งงานของนักเรียนทั้งหมดจะยังคงอยู่)", color = QTheme.colors.textSecondary) },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) { Text("ลบ") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("ยกเลิก") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SubmitHomeworkDialog — นักเรียนส่งคำตอบ + แนบไฟล์ ≤ 2 MB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SubmitHomeworkDialog(
    hw: HomeworkItem,
    uploadProgress: Float?,
    onDismiss: () -> Unit,
    onConfirm: (answer: String, uri: Uri?) -> Unit
) {
    val context     = LocalContext.current
    var answer      by remember { mutableStateOf("") }
    var pickedUri   by remember { mutableStateOf<Uri?>(null) }
    var pickedName  by remember { mutableStateOf("") }
    val isUploading = uploadProgress != null
    val canSubmit   = !isUploading && (answer.isNotBlank() || pickedUri != null)

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pickedUri  = uri
        pickedName = uri?.let { u ->
            context.contentResolver.query(u, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cur.getString(idx) else null
                } else null
            } ?: u.lastPathSegment
        } ?: ""
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        containerColor   = QTheme.colors.card,
        title = {
            Text("ส่งงาน: ${hw.title}", color = QTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hw.description.isNotBlank())
                    Text(hw.description, color = QTheme.colors.textSecondary, fontSize = 12.sp)

                OutlinedTextField(
                    value         = answer,
                    onValueChange = { if (!isUploading) answer = it },
                    label         = { Text("คำตอบ / รายละเอียด") },
                    placeholder   = { Text("พิมพ์คำตอบหรือรายละเอียดงานที่ส่ง") },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    maxLines      = 6,
                    enabled       = !isUploading,
                    shape         = RoundedCornerShape(10.dp),
                    colors        = classroomTfColors()
                )

                // ── แนบไฟล์ ───────────────────────────────────────────────
                Surface(
                    shape    = RoundedCornerShape(10.dp),
                    color    = QTheme.colors.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AttachFile, null,
                            tint = HwPurple, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("แนบไฟล์ (≤ 2 MB)", color = QTheme.colors.textSecondary, fontSize = 11.sp)
                            if (pickedName.isNotBlank())
                                Text(pickedName, color = QTheme.colors.textPrimary,
                                    fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick        = { filePicker.launch("*/*") },
                            enabled        = !isUploading,
                            shape          = RoundedCornerShape(8.dp),
                            modifier       = Modifier.height(34.dp),
                            colors         = ButtonDefaults.outlinedButtonColors(contentColor = HwPurple),
                            border         = androidx.compose.foundation.BorderStroke(1.dp, HwPurple.copy(0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Text(if (pickedUri == null) "เลือกไฟล์" else "เปลี่ยน", fontSize = 11.sp)
                        }
                    }
                }

                // ── Progress bar ──────────────────────────────────────────
                if (isUploading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress   = { uploadProgress ?: 0f },
                            modifier   = Modifier.fillMaxWidth(),
                            color      = HwPurple,
                            trackColor = HwPurple.copy(0.2f)
                        )
                        Text(
                            "กำลังอัปโหลด ${((uploadProgress ?: 0f) * 100).toInt()}%",
                            color    = QTheme.colors.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (canSubmit) onConfirm(answer.trim(), pickedUri) },
                enabled  = canSubmit,
                colors   = ButtonDefaults.buttonColors(containerColor = HwPurple)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(15.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(if (isUploading) "กำลังส่ง..." else "ส่งงาน")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) {
                Text("ยกเลิก",
                    color = if (isUploading) QTheme.colors.textSecondary.copy(alpha = 0.4f)
                            else QTheme.colors.textSecondary)
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SubmissionsViewDialog — ครูดูรายการส่งงาน
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SubmissionsViewDialog(
    hw: HomeworkItem,
    submissions: List<HomeworkSubmission>,
    onDismiss: () -> Unit,
    onGrade: (HomeworkSubmission) -> Unit
) {
    val context = LocalContext.current
    val fmt = SimpleDateFormat("d MMM HH:mm", Locale("th", "TH"))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = {
            Column {
                Text("การส่งงาน: ${hw.title}", color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${submissions.size} คนส่ง", color = QTheme.colors.textSecondary, fontSize = 12.sp)
            }
        },
        text = {
            if (submissions.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, null,
                            tint = QTheme.colors.textSecondary, modifier = Modifier.size(48.dp))
                        Text("ยังไม่มีนักเรียนส่งงาน",
                            color = QTheme.colors.textSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    submissions.forEach { sub ->
                        val isGraded = sub.status == HomeworkSubmission.STATUS_GRADED
                        Card(shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isGraded) Color(0xFF4CAF50).copy(0.07f)
                                else Color(0xFFFF9800).copy(0.07f))) {
                            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, null,
                                        tint = if (isGraded) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                        modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(sub.studentName, color = QTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Surface(shape = RoundedCornerShape(6.dp),
                                        color = if (isGraded) Color(0xFF4CAF50).copy(0.15f) else Color(0xFFFF9800).copy(0.15f)) {
                                        Text(if (isGraded) "✓ ตรวจแล้ว" else "รอตรวจ",
                                            color = if (isGraded) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Text(fmt.format(Date(sub.submittedAt)),
                                    color = QTheme.colors.textSecondary, fontSize = 10.sp)
                                Spacer(Modifier.height(6.dp))
                                Surface(shape = RoundedCornerShape(8.dp), color = QTheme.colors.background) {
                                    Text(sub.answer, color = QTheme.colors.textPrimary, fontSize = 12.sp,
                                        modifier = Modifier.padding(10.dp).fillMaxWidth())
                                }
                                // ปุ่มเปิดไฟล์แนบ (ถ้ามี)
                                if (sub.hasFile) {
                                    OutlinedButton(
                                        onClick  = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sub.fileUrl))) },
                                        modifier = Modifier.fillMaxWidth().height(34.dp),
                                        shape    = RoundedCornerShape(8.dp),
                                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF57C00)),
                                        border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF57C00).copy(0.5f))
                                    ) {
                                        Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(sub.fileName.ifBlank { "ดูไฟล์แนบ" }, fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                                if (isGraded) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("คะแนน: ${sub.grade}", color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    if (sub.feedback.isNotBlank())
                                        Text("ความเห็น: ${sub.feedback}",
                                            color = QTheme.colors.textSecondary, fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick  = { onGrade(sub) },
                                    modifier = Modifier.fillMaxWidth().height(34.dp),
                                    shape    = RoundedCornerShape(8.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = if (isGraded) Color(0xFF388E3C) else HwPurple)
                                ) {
                                    Icon(Icons.Default.Grade, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isGraded) "แก้คะแนน" else "ตรวจ / ให้คะแนน", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ปิด", color = QTheme.colors.textSecondary) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// GradeDialog — ครูให้คะแนน
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GradeDialog(
    submission: HomeworkSubmission,
    onDismiss: () -> Unit,
    onConfirm: (grade: String, feedback: String) -> Unit
) {
    val context  = LocalContext.current
    var grade    by remember { mutableStateOf(submission.grade) }
    var feedback by remember { mutableStateOf(submission.feedback) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = {
            Text("ให้คะแนน: ${submission.studentName}", color = QTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // แสดงคำตอบ
                Text("คำตอบของนักเรียน:", color = QTheme.colors.textSecondary, fontSize = 11.sp)
                Surface(shape = RoundedCornerShape(8.dp), color = QTheme.colors.background) {
                    Text(submission.answer, color = QTheme.colors.textPrimary, fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp).fillMaxWidth())
                }
                // ไฟล์แนบ (ถ้ามี)
                if (submission.hasFile) {
                    OutlinedButton(
                        onClick  = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(submission.fileUrl))) },
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF57C00)),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF57C00).copy(0.5f))
                    ) {
                        Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(submission.fileName.ifBlank { "ดูไฟล์แนบ" }, fontSize = 11.sp, maxLines = 1)
                    }
                }
                OutlinedTextField(
                    value         = grade,
                    onValueChange = { grade = it },
                    label         = { Text("คะแนน * (เช่น 90, A, ผ่าน)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    colors        = classroomTfColors()
                )
                OutlinedTextField(
                    value         = feedback,
                    onValueChange = { feedback = it },
                    label         = { Text("ความเห็น (ไม่บังคับ)") },
                    maxLines      = 3,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    colors        = classroomTfColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (grade.isNotBlank()) onConfirm(grade.trim(), feedback.trim()) },
                enabled  = grade.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("บันทึกคะแนน")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = QTheme.colors.textSecondary) }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// StudentNameDialog — ตั้งชื่อ + รูปโปรไฟล์ (base64, ไม่ใช้ Firebase Storage)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun StudentNameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, avatarBase64: String) -> Unit
) {
    val context      = LocalContext.current
    var name         by remember { mutableStateOf(current) }
    var avatarBase64 by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val orig = context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: return@rememberLauncherForActivityResult
        val scaled = android.graphics.Bitmap.createScaledBitmap(orig, 120, 120, true)
        val baos   = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 65, baos)
        avatarBase64 = "data:image/jpeg;base64," +
            android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.DEFAULT)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = {
            Text("ชื่อและรูปโปรไฟล์",
                color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // รูปโปรไฟล์ + ปุ่มกล้อง
                Box {
                    AvatarImage(
                        avatarBase64 = avatarBase64,
                        name         = name.ifBlank { "?" },
                        size         = 80.dp
                    )
                    Surface(
                        shape    = CircleShape,
                        color    = PrimaryBlue,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .offset(x = 4.dp, y = 4.dp)
                            .clickable { imagePicker.launch("image/*") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CameraAlt, null,
                                tint     = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Text(
                    "แตะไอคอนกล้องเพื่อเพิ่มรูปโปรไฟล์",
                    color     = QTheme.colors.textSecondary,
                    fontSize  = 11.sp,
                    textAlign = TextAlign.Center
                )
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("ชื่อ-นามสกุล หรือ ชื่อเล่น") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                    colors        = classroomTfColors()
                )
                Text(
                    "ชื่อนี้จะแสดงในการส่งงานและการเช็คชื่อ",
                    color     = QTheme.colors.textSecondary,
                    fontSize  = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (name.isNotBlank()) onConfirm(name.trim(), avatarBase64) },
                enabled  = name.isNotBlank(),
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
// AddHomeworkDialog (teacher)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AddHomeworkDialog(
    onDismiss: () -> Unit,
    onConfirm: (HomeworkItem) -> Unit
) {
    val context = LocalContext.current
    var title        by remember { mutableStateOf("") }
    var description  by remember { mutableStateOf("") }
    var dueMillis    by remember { mutableStateOf(System.currentTimeMillis() + 7 * 24 * 3_600_000L) }
    var fileUrl      by remember { mutableStateOf("") }
    var fileName     by remember { mutableStateOf("") }
    var notifyBefore by remember { mutableStateOf(60) }

    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale("th", "TH")) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale("th", "TH")) }

    val showDatePicker: () -> Unit = {
        val cal = Calendar.getInstance().apply { timeInMillis = dueMillis }
        DatePickerDialog(context, { _, y, m, d ->
            dueMillis = Calendar.getInstance().apply {
                timeInMillis = dueMillis; set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d)
            }.timeInMillis
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    val showTimePicker: () -> Unit = {
        val cal = Calendar.getInstance().apply { timeInMillis = dueMillis }
        TimePickerDialog(context, { _, h, min ->
            dueMillis = Calendar.getInstance().apply {
                timeInMillis = dueMillis; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0)
            }.timeInMillis
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = { Text("เพิ่มการบ้าน", color = QTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it },
                    label = { Text("ชื่องาน *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors())
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("รายละเอียด / โจทย์") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors())
                Text("กำหนดส่ง", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = showDatePicker, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(dateFmt.format(Date(dueMillis)), fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = showTimePicker, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(timeFmt.format(Date(dueMillis)), fontSize = 11.sp)
                    }
                }
                OutlinedTextField(value = fileName, onValueChange = { fileName = it },
                    label = { Text("ชื่อไฟล์ (ไม่บังคับ)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors())
                OutlinedTextField(value = fileUrl, onValueChange = { fileUrl = it },
                    label = { Text("URL ไฟล์แนบ (ไม่บังคับ)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors())
                Text("แจ้งเตือนล่วงหน้า", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(30 to "30 นาที", 60 to "1 ชม.", 120 to "2 ชม.", 1440 to "1 วัน")
                        .forEach { (m, label) ->
                            FilterChip(selected = notifyBefore == m, onClick = { notifyBefore = m },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HwPurple.copy(0.2f),
                                    selectedLabelColor     = HwPurple))
                        }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    if (title.isNotBlank())
                        onConfirm(HomeworkItem(
                            title = title.trim(), description = description.trim(),
                            dueDate = dueMillis, fileUrl = fileUrl.trim(),
                            fileName = fileName.trim(), notifyBeforeMinutes = notifyBefore
                        ))
                },
                enabled = title.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(containerColor = HwPurple)
            ) { Text("บันทึก") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = QTheme.colors.textSecondary) }
        }
    )
}
