package com.petcompanion.presentation.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.data.firebase.GradeRecord
import com.petcompanion.data.firebase.StudentProfile
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.theme.QTheme
import com.petcompanion.presentation.viewmodel.ClassroomViewModel
import kotlin.math.roundToInt

@Composable
fun GradeManagerScreen(
    viewModel: ClassroomViewModel,
    onBackClick: () -> Unit
) {
    val allGrades    by viewModel.grades.collectAsState()
    val students     by viewModel.approvedStudents.collectAsState()
    val isTeacher    by viewModel.isEffectiveTeacher.collectAsState()
    val studentName  by viewModel.studentName.collectAsState()
    val teacherError by viewModel.teacherError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(teacherError) {
        teacherError?.let { viewModel.clearTeacherError(); snackbarHostState.showSnackbar(it) }
    }

    // นักเรียนเห็นเฉพาะคะแนนของตัวเอง; ครูเห็นทั้งหมด
    val grades = if (isTeacher) allGrades
                 else allGrades.filter { it.studentName == studentName }

    val avg     = if (grades.isEmpty()) 0.0 else grades.map { (it.score / it.maxScore) * 100 }.average()
    val highest = grades.maxOfOrNull { (it.score / it.maxScore) * 100 } ?: 0.0
    val lowest  = grades.minOfOrNull { (it.score / it.maxScore) * 100 } ?: 0.0

    Scaffold(
        containerColor = QTheme.colors.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isTeacher) {
                FloatingActionButton(
                    onClick        = { showAddDialog = true },
                    containerColor = Color(0xFFF57C00),
                    contentColor   = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "เพิ่มคะแนน") }
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
                    Text(stringResource(R.string.grades_title),
                        color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.grades_subtitle),
                        color = QTheme.colors.textSecondary, fontSize = 12.sp)
                }
                if (isTeacher) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFF57C00).copy(0.15f)) {
                        Text("ครู", color = Color(0xFFF57C00), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
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
            // ── สรุปคะแนน ──────────────────────────────────────────────────
            if (grades.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.grades_summary),
                        color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradeStatChip(stringResource(R.string.grades_average),
                            "${avg.roundToInt()}%",     PrimaryBlue,         Modifier.weight(1f))
                        GradeStatChip(stringResource(R.string.grades_highest),
                            "${highest.roundToInt()}%", Color(0xFF4CAF50),  Modifier.weight(1f))
                        GradeStatChip(stringResource(R.string.grades_lowest),
                            "${lowest.roundToInt()}%",  Color(0xFFEF5350),  Modifier.weight(1f))
                    }
                }
            }

            item {
                Text("รายการคะแนน", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            if (grades.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BarChart, null,
                                tint = QTheme.colors.textSecondary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.grades_no_records),
                                color = QTheme.colors.textSecondary, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(grades) { grade ->
                    GradeCard(grade, isTeacher, onDelete = { viewModel.deleteGrade(grade.id) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddGradeDialog(
            defaultDate = viewModel.todayString(),
            students    = students,
            onDismiss   = { showAddDialog = false },
            onConfirm   = { grade ->
                viewModel.addGrade(grade)
                showAddDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GradeCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GradeCard(
    grade: GradeRecord,
    isTeacher: Boolean,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val percentage = if (grade.maxScore > 0) (grade.score / grade.maxScore * 100).roundToInt() else 0
    val barColor = when {
        percentage >= 80 -> Color(0xFF4CAF50)
        percentage >= 60 -> Color(0xFFFFC107)
        else             -> Color(0xFFEF5350)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (grade.studentName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null,
                                tint     = PrimaryBlue.copy(0.7f),
                                modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(grade.studentName, color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                    Text(grade.subject, color = QTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (grade.date.isNotBlank())
                        Text(grade.date, color = QTheme.colors.textSecondary, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val scoreStr = grade.score.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
                    val maxStr   = grade.maxScore.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
                    Text("$scoreStr/$maxStr", color = barColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("$percentage%", color = barColor, fontSize = 12.sp)
                }
                if (isTeacher) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, null,
                            tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress  = { (percentage / 100f).coerceIn(0f, 1f) },
                modifier  = Modifier.fillMaxWidth().height(6.dp),
                color     = barColor,
                trackColor = barColor.copy(alpha = 0.15f)
            )
            if (grade.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(grade.note, color = QTheme.colors.textSecondary, fontSize = 11.sp)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = QTheme.colors.card,
            title  = { Text("ลบคะแนน?", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text   = { Text("ลบคะแนนวิชา \"${grade.subject}\"?", color = QTheme.colors.textSecondary) },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) { Text("ลบ") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("ยกเลิก") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AddGradeDialog  (teacher only)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AddGradeDialog(
    defaultDate: String,
    students: List<StudentProfile>,
    onDismiss: () -> Unit,
    onConfirm: (GradeRecord) -> Unit
) {
    var studentName  by remember { mutableStateOf("") }
    var subject      by remember { mutableStateOf("") }
    var score        by remember { mutableStateOf("") }
    var maxScore     by remember { mutableStateOf("100") }
    var date         by remember { mutableStateOf(defaultDate) }
    var note         by remember { mutableStateOf("") }
    var showStudentPicker by remember { mutableStateOf(false) }

    val isValid = subject.isNotBlank() &&
        score.toDoubleOrNull() != null &&
        (maxScore.toDoubleOrNull() ?: 0.0) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = { Text("เพิ่มคะแนน",
            color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── เลือกนักเรียน ──────────────────────────────────────────
                if (students.isNotEmpty()) {
                    Text("นักเรียน", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                    Surface(
                        shape    = RoundedCornerShape(10.dp),
                        color    = QTheme.colors.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { showStudentPicker = !showStudentPicker }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, null,
                                tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                studentName.ifBlank { "เลือกนักเรียน (ไม่บังคับ)" },
                                color    = if (studentName.isBlank()) QTheme.colors.textSecondary
                                           else QTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f), fontSize = 14.sp
                            )
                            Icon(
                                if (showStudentPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, tint = QTheme.colors.textSecondary
                            )
                        }
                    }
                    if (showStudentPicker) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // ตัวเลือกว่าง (ไม่ระบุนักเรียน)
                            Surface(
                                shape    = RoundedCornerShape(8.dp),
                                color    = if (studentName.isBlank()) PrimaryBlue.copy(0.12f)
                                           else QTheme.colors.background,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        studentName = ""
                                        showStudentPicker = false
                                    }
                            ) {
                                Text("— ไม่ระบุนักเรียน —",
                                    color    = QTheme.colors.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                            students.forEach { s ->
                                Surface(
                                    shape    = RoundedCornerShape(8.dp),
                                    color    = if (studentName == s.name) PrimaryBlue.copy(0.12f)
                                               else QTheme.colors.background,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            studentName = s.name
                                            showStudentPicker = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarImage(
                                            avatarBase64 = s.avatarBase64,
                                            name         = s.name,
                                            size         = 28.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(s.name,
                                            color    = QTheme.colors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (studentName == s.name) FontWeight.Bold
                                                         else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(value = studentName, onValueChange = { studentName = it },
                        label = { Text("ชื่อนักเรียน (ไม่บังคับ)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                        colors = classroomTfColors())
                }
                OutlinedTextField(value = subject, onValueChange = { subject = it },
                    label = { Text("วิชา *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = score, onValueChange = { score = it },
                        label = { Text("คะแนนที่ได้ *") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = classroomTfColors())
                    OutlinedTextField(value = maxScore, onValueChange = { maxScore = it },
                        label = { Text("คะแนนเต็ม") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = classroomTfColors())
                }
                OutlinedTextField(value = date, onValueChange = { date = it },
                    label = { Text("วันที่ (yyyy-MM-dd)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors())
                OutlinedTextField(value = note, onValueChange = { note = it },
                    label = { Text("หมายเหตุ (ไม่บังคับ)") }, maxLines = 2,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors())
                // Preview percentage
                val pct = if (score.toDoubleOrNull() != null && (maxScore.toDoubleOrNull() ?: 0.0) > 0) {
                    ((score.toDouble() / maxScore.toDouble()) * 100).roundToInt()
                } else null
                if (pct != null) {
                    val barColor = when { pct >= 80 -> Color(0xFF4CAF50); pct >= 60 -> Color(0xFFFFC107); else -> Color(0xFFEF5350) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress   = { (pct / 100f).coerceIn(0f, 1f) },
                            modifier   = Modifier.weight(1f).height(6.dp),
                            color      = barColor,
                            trackColor = barColor.copy(0.15f)
                        )
                        Text("$pct%", color = barColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    onConfirm(GradeRecord(
                        studentName = studentName.trim(),
                        subject     = subject.trim(),
                        score       = score.toDoubleOrNull() ?: 0.0,
                        maxScore    = maxScore.toDoubleOrNull() ?: 100.0,
                        date        = date.trim(),
                        note        = note.trim()
                    ))
                },
                enabled = isValid,
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
            ) { Text("บันทึก") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = QTheme.colors.textSecondary) }
        }
    )
}

@Composable
private fun GradeStatChip(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(label, color = color, fontSize = 10.sp)
        }
    }
}
