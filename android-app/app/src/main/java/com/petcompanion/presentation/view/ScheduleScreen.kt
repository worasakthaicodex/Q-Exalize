package com.petcompanion.presentation.view

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.data.firebase.OnlineExam
import com.petcompanion.data.firebase.ScheduleEvent
import com.petcompanion.domain.model.CourseEntity
import com.petcompanion.domain.model.QuestionEntity
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.theme.QTheme
import com.petcompanion.presentation.viewmodel.ClassroomViewModel
import com.petcompanion.presentation.viewmodel.QuizViewModel
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScheduleScreen(
    viewModel: ClassroomViewModel,
    quizViewModel: QuizViewModel,
    onBackClick: () -> Unit,
    combinedMode: Boolean = false   // true = รวมทุกห้อง (เข้าจากหน้าแรก, read-only)
) {
    val singleEvents   by viewModel.events.collectAsState()
    val combinedEvents by viewModel.allEvents.collectAsState()
    val events = if (combinedMode) combinedEvents else singleEvents
    val isTeacher = if (combinedMode) false else viewModel.isTeacherMode.collectAsState().value
    val teacherError by viewModel.teacherError.collectAsState()
    val allCourses   by quizViewModel.allCourses.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentMonth       by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDay        by remember { mutableStateOf<Int?>(null) }
    var showAddEventDialog by remember { mutableStateOf(false) }

    LaunchedEffect(teacherError) {
        teacherError?.let { viewModel.clearTeacherError(); snackbarHostState.showSnackbar(it) }
    }

    val monthFmt = SimpleDateFormat("MMMM yyyy", Locale("th", "TH"))
    val dayFmt   = SimpleDateFormat("d MMM yyyy HH:mm", Locale("th", "TH"))

    val eventsInMonth = remember(events, currentMonth) {
        events.filter { ev ->
            val cal = Calendar.getInstance().apply { timeInMillis = ev.dateTime }
            cal.get(Calendar.YEAR)  == currentMonth.get(Calendar.YEAR) &&
            cal.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)
        }
    }

    val eventDayMap = remember(eventsInMonth) {
        eventsInMonth.groupBy { ev ->
            Calendar.getInstance().apply { timeInMillis = ev.dateTime }.get(Calendar.DAY_OF_MONTH)
        }
    }

    val selectedEvents = remember(selectedDay, eventDayMap) {
        selectedDay?.let { eventDayMap[it] } ?: emptyList()
    }

    val upcomingEvents = remember(events) {
        val now = System.currentTimeMillis()
        events.filter { it.dateTime >= now }.take(10)
    }

    Scaffold(
        containerColor = QTheme.colors.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isTeacher) {
                FloatingActionButton(
                    onClick        = { showAddEventDialog = true },
                    containerColor = PrimaryBlue,
                    contentColor   = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "เพิ่มกิจกรรม") }
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
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, null, tint = QTheme.colors.textPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.schedule_title),
                        color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.schedule_subtitle),
                        color = QTheme.colors.textSecondary, fontSize = 12.sp)
                }
                if (isTeacher) {
                    Surface(shape = RoundedCornerShape(6.dp), color = PrimaryBlue.copy(0.15f)) {
                        Text("ครู", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding      = PaddingValues(vertical = 8.dp)
        ) {
            // ── Month Navigator ──────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        selectedDay = null
                    }) { Icon(Icons.Default.ChevronLeft, null, tint = PrimaryBlue) }

                    Text(
                        monthFmt.format(currentMonth.time).replaceFirstChar { it.uppercase() },
                        color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )

                    IconButton(onClick = {
                        currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        selectedDay = null
                    }) { Icon(Icons.Default.ChevronRight, null, tint = PrimaryBlue) }
                }
            }

            // ── Calendar Grid ────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val dayNames = listOf("อา", "จ", "อ", "พ", "พฤ", "ศ", "ส")
                        Row(modifier = Modifier.fillMaxWidth()) {
                            dayNames.forEach { d ->
                                Text(d, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    color = QTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        val cal = currentMonth.clone() as Calendar
                        cal.set(Calendar.DAY_OF_MONTH, 1)
                        val firstDow    = cal.get(Calendar.DAY_OF_WEEK) - 1
                        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val today       = Calendar.getInstance()
                        val isCurrentMonth = today.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR) &&
                                             today.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)

                        val rows = ((firstDow + daysInMonth) + 6) / 7

                        (0 until rows).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                (0 until 7).forEach { col ->
                                    val day        = row * 7 + col - firstDow + 1
                                    val valid      = day in 1..daysInMonth
                                    val hasEvent   = valid && eventDayMap.containsKey(day)
                                    val isToday    = valid && isCurrentMonth && day == today.get(Calendar.DAY_OF_MONTH)
                                    val isSelected = valid && day == selectedDay

                                    Box(
                                        modifier = Modifier
                                            .weight(1f).aspectRatio(1f).padding(2.dp)
                                            .clip(CircleShape)
                                            .background(when {
                                                isSelected -> PrimaryBlue
                                                isToday    -> PrimaryBlue.copy(alpha = 0.2f)
                                                else       -> Color.Transparent
                                            })
                                            .then(if (valid) Modifier.clickable {
                                                selectedDay = if (selectedDay == day) null else day
                                            } else Modifier),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (valid) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    "$day",
                                                    color = when {
                                                        isSelected -> Color.White
                                                        isToday    -> PrimaryBlue
                                                        else       -> QTheme.colors.textPrimary
                                                    },
                                                    fontSize   = 13.sp,
                                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (hasEvent) {
                                                    Spacer(Modifier.height(1.dp))
                                                    Box(modifier = Modifier.size(4.dp).clip(CircleShape)
                                                        .background(if (isSelected) Color.White else PrimaryBlue))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Events ของวันที่เลือก ─────────────────────────────────────
            if (selectedDay != null && selectedEvents.isNotEmpty()) {
                item {
                    Text("${stringResource(R.string.schedule_today_events)} — วันที่ $selectedDay",
                        color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                items(selectedEvents) { ev ->
                    EventCard(ev, dayFmt, viewModel, isTeacher)
                }
            }

            // ── Upcoming ──────────────────────────────────────────────────
            item {
                Text(stringResource(R.string.schedule_upcoming),
                    color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (upcomingEvents.isEmpty()) {
                item {
                    Text(stringResource(R.string.schedule_no_events),
                        color = QTheme.colors.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
            } else {
                items(upcomingEvents) { ev -> EventCard(ev, dayFmt, viewModel, isTeacher) }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // ── Add Event Dialog ──────────────────────────────────────────────────
    if (showAddEventDialog) {
        AddEventDialog(
            courses     = allCourses,
            quizViewModel = quizViewModel,
            onDismiss   = { showAddEventDialog = false },
            onConfirm   = { event, examConfig ->
                viewModel.addEvent(event)
                // ถ้าเลือก "สร้างข้อสอบออนไลน์" → สร้าง exam พร้อมกัน
                examConfig?.let { cfg ->
                    viewModel.createExam(
                        OnlineExam(
                            title          = event.title,
                            description    = event.description,
                            endTime        = cfg.endTime,
                            autoGrade      = cfg.autoGrade,
                            showAnswers    = cfg.showAnswers,
                            linkedCourseId = cfg.selectedCourse?.id ?: 0,
                            isPublished    = false
                        )
                    ) { examId ->
                        // นำเข้าคำถามจากคลังทันที ถ้าเลือก course ไว้
                        if (cfg.questionsToImport.isNotEmpty()) {
                            viewModel.importQuestionsFromCourse(examId, cfg.questionsToImport)
                        }
                    }
                }
                showAddEventDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EventCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EventCard(
    event: ScheduleEvent,
    fmt: SimpleDateFormat,
    viewModel: ClassroomViewModel,
    isTeacher: Boolean
) {
    var reminderSet      by remember(event.id) { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val typeColor = when (event.type) {
        ScheduleEvent.TYPE_EXAM     -> Color(0xFFEF5350)
        ScheduleEvent.TYPE_HOMEWORK -> Color(0xFFFF9800)
        ScheduleEvent.TYPE_HOLIDAY  -> Color(0xFF4CAF50)
        else                        -> PrimaryBlue
    }
    val typeLabel = when (event.type) {
        ScheduleEvent.TYPE_EXAM     -> "สอบ"
        ScheduleEvent.TYPE_HOMEWORK -> "งานส่ง"
        ScheduleEvent.TYPE_HOLIDAY  -> "วันหยุด"
        else                        -> "กิจกรรม"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.width(4.dp).height(50.dp)
                .clip(RoundedCornerShape(2.dp)).background(typeColor))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(shape = RoundedCornerShape(6.dp), color = typeColor.copy(0.15f)) {
                    Text(typeLabel, color = typeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(event.title, color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (event.description.isNotBlank())
                    Text(event.description, color = QTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, null, tint = QTheme.colors.textSecondary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    val timeText = if (event.endTime > 0L)
                        "${fmt.format(Date(event.dateTime))} – ${fmt.format(Date(event.endTime))}"
                    else fmt.format(Date(event.dateTime))
                    Text(timeText, color = QTheme.colors.textSecondary, fontSize = 11.sp)
                }
            }
            // Reminder button
            if (event.type != ScheduleEvent.TYPE_HOLIDAY) {
                IconButton(
                    onClick  = { viewModel.scheduleEventReminder(event); reminderSet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (reminderSet) Icons.Default.NotificationsActive else Icons.Default.NotificationAdd,
                        contentDescription = null,
                        tint     = if (reminderSet) Color(0xFF4CAF50) else QTheme.colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // Delete button (teacher only)
            if (isTeacher) {
                IconButton(
                    onClick  = { showDeleteConfirm = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = QTheme.colors.card,
            title  = { Text("ลบกิจกรรม?", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text   = { Text("ต้องการลบ \"${event.title}\" ออกจากตาราง?", color = QTheme.colors.textSecondary) },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteEvent(event.id); showDeleteConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) { Text("ลบ") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("ยกเลิก") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExamConfig — ข้อมูลสำหรับสร้างข้อสอบออนไลน์พ่วงกับกิจกรรม
// ─────────────────────────────────────────────────────────────────────────────
data class ExamConfig(
    val endTime: Long,
    val autoGrade: Boolean,
    val showAnswers: Boolean,
    val selectedCourse: CourseEntity? = null,               // คลังที่เลือก (ถ้ามี)
    val questionsToImport: List<QuestionEntity> = emptyList() // คำถามที่จะนำเข้า Firestore
)

// ─────────────────────────────────────────────────────────────────────────────
// AddEventDialog  (teacher only)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AddEventDialog(
    courses: List<CourseEntity>,
    quizViewModel: QuizViewModel,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleEvent, ExamConfig?) -> Unit
) {
    val context = LocalContext.current

    var title          by remember { mutableStateOf("") }
    var description    by remember { mutableStateOf("") }
    var selectedMillis by remember { mutableStateOf(System.currentTimeMillis() + 3_600_000L) }
    var eventEndMillis by remember { mutableStateOf(System.currentTimeMillis() + 2 * 3_600_000L) }
    var hasEndTime     by remember { mutableStateOf(false) }
    var type           by remember { mutableStateOf(ScheduleEvent.TYPE_EVENT) }
    var notifyBefore   by remember { mutableStateOf(30) }
    var showTypeMenu   by remember { mutableStateOf(false) }

    // ── Exam config (แสดงเมื่อ type = สอบ) ────────────────────────────────
    var createOnlineExam   by remember { mutableStateOf(false) }
    var examEndMillis      by remember { mutableStateOf(System.currentTimeMillis() + 2 * 3_600_000L) }
    var autoGrade          by remember { mutableStateOf(true) }
    var showAnswers        by remember { mutableStateOf(false) }
    // ── Quiz bank picker ───────────────────────────────────────────────────
    var selectedCourse     by remember { mutableStateOf<CourseEntity?>(null) }
    var importedQuestions  by remember { mutableStateOf<List<QuestionEntity>>(emptyList()) }
    var showCoursePicker   by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCourse?.id) {
        val courseId = selectedCourse?.id
        if (courseId != null) {
            importedQuestions = quizViewModel.getQuestionsByCourseId(courseId).firstOrNull() ?: emptyList()
        } else {
            importedQuestions = emptyList()
        }
    }

    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale("th", "TH")) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale("th", "TH")) }

    fun makeDatePicker(millis: Long, onSet: (Long) -> Unit): () -> Unit = {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        DatePickerDialog(context, { _, y, m, d ->
            onSet(Calendar.getInstance().apply {
                timeInMillis = millis; set(y, m, d)
            }.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun makeTimePicker(millis: Long, onSet: (Long) -> Unit): () -> Unit = {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        TimePickerDialog(context, { _, h, min ->
            onSet(Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0)
            }.timeInMillis)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    val typeOptions = listOf(
        ScheduleEvent.TYPE_EVENT    to ("กิจกรรม"  to Icons.Default.Event),
        ScheduleEvent.TYPE_EXAM     to ("สอบ"      to Icons.Default.Assignment),
        ScheduleEvent.TYPE_HOMEWORK to ("งานส่ง"   to Icons.Default.Task),
        ScheduleEvent.TYPE_HOLIDAY  to ("วันหยุด"  to Icons.Default.BeachAccess)
    )
    val typeColor = when (type) {
        ScheduleEvent.TYPE_EXAM     -> Color(0xFFEF5350)
        ScheduleEvent.TYPE_HOMEWORK -> Color(0xFFFF9800)
        ScheduleEvent.TYPE_HOLIDAY  -> Color(0xFF4CAF50)
        else                        -> PrimaryBlue
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = { Text("เพิ่มกิจกรรม / กำหนดการ",
            color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── ชื่อ ───────────────────────────────────────────────────
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("ชื่อกิจกรรม *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors()
                )
                // ── รายละเอียด ─────────────────────────────────────────────
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("รายละเอียด (ไม่บังคับ)") }, maxLines = 2,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors()
                )

                // ── ประเภท (Dropdown ปุ่มเดียว) ────────────────────────────
                Text("ประเภท", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                Box(modifier = Modifier.fillMaxWidth()) {
                    val (selectedLabel, selectedIcon) = typeOptions.first { it.first == type }.second
                    OutlinedButton(
                        onClick = { showTypeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, typeColor.copy(0.6f))
                    ) {
                        Icon(selectedIcon, null, tint = typeColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(selectedLabel, color = typeColor, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null, tint = typeColor)
                    }
                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        typeOptions.forEach { (t, pair) ->
                            val (label, icon) = pair
                            val c = when (t) {
                                ScheduleEvent.TYPE_EXAM     -> Color(0xFFEF5350)
                                ScheduleEvent.TYPE_HOMEWORK -> Color(0xFFFF9800)
                                ScheduleEvent.TYPE_HOLIDAY  -> Color(0xFF4CAF50)
                                else                        -> PrimaryBlue
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(label,
                                        color = if (type == t) c else QTheme.colors.textPrimary,
                                        fontWeight = if (type == t) FontWeight.Bold else FontWeight.Normal)
                                },
                                onClick = { type = t; showTypeMenu = false; createOnlineExam = false },
                                leadingIcon = {
                                    Icon(if (type == t) Icons.Default.Check else icon, null,
                                        tint = c, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }

                // ── วันและเวลาเริ่ม ─────────────────────────────────────────
                Text("เวลาเริ่ม", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = makeDatePicker(selectedMillis) { selectedMillis = it },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(dateFmt.format(Date(selectedMillis)), fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = makeTimePicker(selectedMillis) { selectedMillis = it },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(timeFmt.format(Date(selectedMillis)), fontSize = 11.sp)
                    }
                }

                // ── วันและเวลาสิ้นสุด ──────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("กำหนดเวลาสิ้นสุด", color = QTheme.colors.textSecondary,
                        fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = hasEndTime,
                        onCheckedChange = { hasEndTime = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                    )
                }
                if (hasEndTime) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = makeDatePicker(eventEndMillis) { eventEndMillis = it },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.EventBusy, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(dateFmt.format(Date(eventEndMillis)), fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = makeTimePicker(eventEndMillis) { eventEndMillis = it },
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(timeFmt.format(Date(eventEndMillis)), fontSize = 11.sp)
                        }
                    }
                }

                // ── แจ้งเตือนล่วงหน้า ──────────────────────────────────────
                Text("แจ้งเตือนล่วงหน้า", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(15 to "15 น.", 30 to "30 น.", 60 to "1 ชม.", 1440 to "1 วัน")
                        .forEach { (m, label) ->
                            FilterChip(
                                selected = notifyBefore == m, onClick = { notifyBefore = m },
                                label = { Text(label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue.copy(0.2f),
                                    selectedLabelColor     = PrimaryBlue
                                )
                            )
                        }
                }

                // ── Online Exam section (แสดงเมื่อ type = สอบ) ─────────────
                if (type == ScheduleEvent.TYPE_EXAM) {
                    HorizontalDivider(color = Color(0xFF6A1B9A).copy(0.3f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Assignment, null,
                            tint = Color(0xFF6A1B9A), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "สร้างข้อสอบออนไลน์",
                                color = Color(0xFF6A1B9A), fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "นักเรียนทำข้อสอบผ่านแอปได้เลย",
                                color = QTheme.colors.textSecondary, fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked         = createOnlineExam,
                            onCheckedChange = { createOnlineExam = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor  = Color.White,
                                checkedTrackColor  = Color(0xFF6A1B9A)
                            )
                        )
                    }
                    if (createOnlineExam) {
                        Text("กำหนดหมดเวลาสอบ", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick  = makeDatePicker(examEndMillis) { examEndMillis = it },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(10.dp),
                                border   = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color(0xFF6A1B9A).copy(0.5f))
                            ) {
                                Icon(Icons.Default.CalendarMonth, null,
                                    modifier = Modifier.size(14.dp), tint = Color(0xFF6A1B9A))
                                Spacer(Modifier.width(4.dp))
                                Text(dateFmt.format(Date(examEndMillis)), fontSize = 11.sp,
                                    color = Color(0xFF6A1B9A))
                            }
                            OutlinedButton(
                                onClick  = makeTimePicker(examEndMillis) { examEndMillis = it },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(10.dp),
                                border   = androidx.compose.foundation.BorderStroke(
                                    1.dp, Color(0xFF6A1B9A).copy(0.5f))
                            ) {
                                Icon(Icons.Default.AccessTime, null,
                                    modifier = Modifier.size(14.dp), tint = Color(0xFF6A1B9A))
                                Spacer(Modifier.width(4.dp))
                                Text(timeFmt.format(Date(examEndMillis)), fontSize = 11.sp,
                                    color = Color(0xFF6A1B9A))
                            }
                        }
                        // Auto-grade toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ให้คะแนนอัตโนมัติ",
                                    color = QTheme.colors.textPrimary, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Text("คิดคะแนนทันทีเมื่อนักเรียนส่ง",
                                    color = QTheme.colors.textSecondary, fontSize = 10.sp)
                            }
                            Switch(
                                checked         = autoGrade,
                                onCheckedChange = { autoGrade = it; if (!it) showAnswers = false },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF4CAF50)
                                )
                            )
                        }
                        // Show answers toggle (เปิดได้ก็ต่อเมื่อ autoGrade = true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("อนุญาตให้ดูเฉลย",
                                    color = if (autoGrade) QTheme.colors.textPrimary
                                            else QTheme.colors.textSecondary,
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (autoGrade) "นักเรียนเห็นเฉลยหลังส่ง"
                                    else "ต้องเปิด auto-grade ก่อน",
                                    color = QTheme.colors.textSecondary, fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked         = showAnswers,
                                onCheckedChange = { if (autoGrade) showAnswers = it },
                                enabled         = autoGrade,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor  = Color.White,
                                    checkedTrackColor  = Color(0xFF6A1B9A)
                                )
                            )
                        }
                        // ── Quiz Bank Picker ──────────────────────────────
                        HorizontalDivider(color = Color(0xFF6A1B9A).copy(0.15f))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.LibraryBooks, null,
                                    tint = Color(0xFF6A1B9A), modifier = Modifier.size(14.dp))
                                Text(
                                    "เลือกชุดข้อสอบจากคลัง",
                                    color = Color(0xFF6A1B9A), fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                "ระบบจะนำเข้าคำถามทั้งหมดจากชุดนี้อัตโนมัติ",
                                color = QTheme.colors.textSecondary, fontSize = 10.sp
                            )
                            if (courses.isEmpty()) {
                                Surface(shape = RoundedCornerShape(8.dp),
                                    color = QTheme.colors.textSecondary.copy(0.08f)) {
                                    Text(
                                        "ยังไม่มีชุดข้อสอบในคลัง — ไปสร้างในหน้า Quiz ก่อน",
                                        color = QTheme.colors.textSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                // ปุ่มเลือก course
                                OutlinedButton(
                                    onClick  = { showCoursePicker = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape    = RoundedCornerShape(10.dp),
                                    border   = androidx.compose.foundation.BorderStroke(
                                        1.dp, Color(0xFF6A1B9A).copy(0.5f))
                                ) {
                                    Icon(Icons.Default.LibraryBooks, null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF6A1B9A))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        selectedCourse?.title ?: "เลือกชุดข้อสอบ...",
                                        fontSize = 12.sp,
                                        color = if (selectedCourse != null)
                                            Color(0xFF6A1B9A) else QTheme.colors.textSecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selectedCourse != null) {
                                        Surface(shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF6A1B9A).copy(0.12f)) {
                                            Text(
                                                "${importedQuestions.size} ข้อ",
                                                color = Color(0xFF6A1B9A),
                                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                // ปุ่มล้างการเลือก
                                if (selectedCourse != null) {
                                    TextButton(
                                        onClick  = { selectedCourse = null; importedQuestions = emptyList() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Clear, null,
                                            modifier = Modifier.size(12.dp),
                                            tint = QTheme.colors.textSecondary)
                                        Spacer(Modifier.width(4.dp))
                                        Text("ล้างการเลือก — สร้างข้อถามเองในหน้า Arena",
                                            color = QTheme.colors.textSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        // Info chip
                        if (selectedCourse == null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF6A1B9A).copy(0.08f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, null,
                                        tint = Color(0xFF6A1B9A),
                                        modifier = Modifier.size(14.dp))
                                    Text(
                                        "ถ้าไม่เลือกคลัง → ไปเพิ่มข้อถามเองในหน้า Arena แล้ว Publish",
                                        color = Color(0xFF6A1B9A), fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    if (title.isNotBlank()) {
                        val event = ScheduleEvent(
                            title               = title.trim(),
                            description         = description.trim(),
                            dateTime            = selectedMillis,
                            endTime             = if (hasEndTime) eventEndMillis else 0L,
                            type                = type,
                            notifyBeforeMinutes = notifyBefore
                        )
                        val examCfg = if (type == ScheduleEvent.TYPE_EXAM && createOnlineExam)
                            ExamConfig(
                                endTime           = examEndMillis,
                                autoGrade         = autoGrade,
                                showAnswers       = showAnswers,
                                selectedCourse    = selectedCourse,
                                questionsToImport = importedQuestions
                            ) else null
                        onConfirm(event, examCfg)
                    }
                },
                enabled = title.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text("บันทึก") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = QTheme.colors.textSecondary) }
        }
    )

    // ── Course Picker Dialog ───────────────────────────────────────────────
    if (showCoursePicker) {
        AlertDialog(
            onDismissRequest = { showCoursePicker = false },
            containerColor   = QTheme.colors.card,
            title = {
                Text("เลือกชุดข้อสอบ", color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                LazyColumn(
                    modifier       = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(courses) { course ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedCourse = course
                                showCoursePicker = false
                            },
                            shape  = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedCourse?.id == course.id)
                                    Color(0xFF6A1B9A).copy(0.12f) else QTheme.colors.background
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(shape = CircleShape,
                                    color = Color(0xFF6A1B9A).copy(0.15f),
                                    modifier = Modifier.size(36.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.LibraryBooks, null,
                                            tint = Color(0xFF6A1B9A),
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.title, color = QTheme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                        maxLines = 2)
                                    Text("${course.totalQuestions} ข้อ · เวลา ${course.timeLimit} นาที",
                                        color = QTheme.colors.textSecondary, fontSize = 11.sp)
                                }
                                if (selectedCourse?.id == course.id) {
                                    Icon(Icons.Default.CheckCircle, null,
                                        tint = Color(0xFF6A1B9A), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCoursePicker = false }) {
                    Text("ปิด", color = Color(0xFF6A1B9A))
                }
            }
        )
    }
}
