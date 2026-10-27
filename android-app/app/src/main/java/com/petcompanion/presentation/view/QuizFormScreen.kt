package com.petcompanion.presentation.view



import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.petcompanion.domain.model.CourseEntity
import com.petcompanion.domain.model.QuizImportModel
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.QuizViewModel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.petcompanion.data.local.BillingManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.compose.ui.res.stringResource
import com.petcompanion.R
import com.petcompanion.domain.model.QuizCategoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizFormScreen(
    application: Application,
    categoryId: Int, // 📍 ถ้ามาจากหน้าปกติ จะมีเลขส่งมา / ถ้ามาจากไฟล์นำเข้า จะเป็น -1
    viewModel: QuizViewModel,
    onBack: (Int) -> Unit,
    formMode: String = "IMPORT"   // "IMPORT" = นำเข้า(วิชาใหม่/เดิม) / "MANUAL" = สร้างเอง / "CODE" = รับโค้ด
) {
    val context = LocalContext.current
    val allCourses by viewModel.allCourses.collectAsState(initial = emptyList())
    // 📍 1. ดึงข้อมูลหมวดหมู่ทั้งหมดมาเตรียมไว้
    val allCategories by viewModel.categoryList.collectAsState()
    val isPremiumUser by viewModel.isPremiumUser.collectAsState(initial = false)

    var importMode by remember {
        mutableStateOf(when (formMode) { "MANUAL" -> "MANUAL"; "CODE" -> "CODE"; else -> "NEW" })
    }
    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var selectedCategory by remember { mutableStateOf<QuizCategoryEntity?>(null) }

    var fileName by remember { mutableStateOf<String?>(null) }
    var jsonContent by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    // ── Code receive mode state ───────────────────────────────────────────
    val importCodeState by viewModel.importCodeState.collectAsState()
    val deviceId = remember {
        context.getSharedPreferences("classroom_prefs", android.content.Context.MODE_PRIVATE)
            .getString("device_id", null) ?: java.util.UUID.randomUUID().toString()
    }
    var inputCode by remember { mutableStateOf("") }

    LaunchedEffect(importCodeState) {
        if (importCodeState is com.petcompanion.presentation.viewmodel.QuizViewModel.ImportCodeState.Success) {
            isSuccess = true
            errorMessage = null
            viewModel.clearImportCodeState()
        } else if (importCodeState is com.petcompanion.presentation.viewmodel.QuizViewModel.ImportCodeState.Error) {
            errorMessage = (importCodeState as com.petcompanion.presentation.viewmodel.QuizViewModel.ImportCodeState.Error).message
            viewModel.clearImportCodeState()
        }
    }

    // ── Manual mode state ──────────────────────────────────────────────────
    var manualTitle       by remember { mutableStateOf("") }
    var manualCode        by remember { mutableStateOf("") }
    var manualChapter     by remember { mutableStateOf("บทที่ 1") }
    var manualDesc        by remember { mutableStateOf("") }
    var manualTimeLimit   by remember { mutableStateOf("30") }
    var manualPassing     by remember { mutableStateOf("60") }
    var manualQuestions   by remember { mutableStateOf<List<com.petcompanion.domain.model.QuestionEntity>>(emptyList()) }
    var showAddQDialog    by remember { mutableStateOf(false) }
    var editingManualQ    by remember { mutableStateOf<com.petcompanion.domain.model.QuestionEntity?>(null) }

    // 📍 3. เครื่องจักรดูดไฟล์! (ถ้ามีไฟล์ค้างในกระเป๋า ให้เอามาใส่ฟอร์มทันที)
    LaunchedEffect(Unit) {
        if (!viewModel.pendingImportJson.isNullOrEmpty()) {
            jsonContent = viewModel.pendingImportJson!!
            fileName = viewModel.pendingImportFileName ?: "Imported_from_LINE.json"
        }
    }

    // 📍 4. เลือกระบุหมวดหมู่อัตโนมัติ (ถ้าเข้ามาตามปกติ ไม่ใช่จากไฟล์)
    LaunchedEffect(allCategories) {
        if (categoryId != -1 && selectedCategory == null) {
            selectedCategory = allCategories.find { it.id == categoryId }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                fileName = it.path?.substringAfterLast('/')
                val inputStream = context.contentResolver.openInputStream(it)
                val reader = BufferedReader(InputStreamReader(inputStream))
                jsonContent = reader.readText()
                inputStream?.close()
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = context.getString(R.string.error_read_file, e.message ?: "Unknown Error")
            }
        }
    }

    Scaffold(
        containerColor = QTheme.colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val screenTitle = when (formMode) {
                        "MANUAL" -> "สร้างข้อสอบเอง"
                        "CODE"   -> "รับข้อสอบจากโค้ด"
                        else     -> stringResource(id = R.string.title_import_quiz)
                    }
                    Text(screenTitle, color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // 📍 กดยกเลิกการเพิ่มข้อสอบ ก็แค่ส่ง ID หมวดหมู่เดิมกลับไป!
                        onBack(categoryId)
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(id = R.string.btn_back), tint = QTheme.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = QTheme.colors.card)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // --- ส่วนที่ 0: 📍 เลือกหมวดหมู่ (จะโชว์ให้เลือก ถ้ายังไม่ได้ระบุ) ---
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("หมวดหมู่วิชา (Category)", color = PrimaryBlue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                CategoryDropdown(allCategories, selectedCategory) { selectedCategory = it }
            }

            // --- ส่วนที่ 1: เลือกโหมด (เฉพาะหน้านำเข้า — วิชาใหม่/วิชาเดิม) ---
            if (formMode == "IMPORT") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(id = R.string.header_import_mode), color = PrimaryBlue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeButton(stringResource(id = R.string.btn_mode_new), importMode == "NEW", Modifier.weight(1f)) { importMode = "NEW" }
                        ModeButton(stringResource(id = R.string.btn_mode_existing), importMode == "EXISTING", Modifier.weight(1f)) { importMode = "EXISTING" }
                    }
                }
            }

            // --- ส่วนที่ 2: เลือกวิชาเดิม ---
            if (importMode == "EXISTING") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(id = R.string.header_select_target), color = PrimaryBlue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    CourseDropdown(allCourses, selectedCourse) { selectedCourse = it }
                }
            }

            // --- ส่วนที่ 2b: Manual mode — พิมพ์คำถามเอง ---
            if (importMode == "MANUAL") {
                ManualCourseForm(
                    title       = manualTitle,       onTitleChange   = { manualTitle = it },
                    code        = manualCode,         onCodeChange    = { manualCode = it },
                    chapter     = manualChapter,      onChapterChange = { manualChapter = it },
                    desc        = manualDesc,         onDescChange    = { manualDesc = it },
                    timeLimit   = manualTimeLimit,    onTimeLimitChange = { manualTimeLimit = it },
                    passing     = manualPassing,      onPassingChange = { manualPassing = it },
                    questions   = manualQuestions,
                    onAddClick  = { editingManualQ = null; showAddQDialog = true },
                    onEditClick = { q -> editingManualQ = q; showAddQDialog = true },
                    onDeleteClick = { q -> manualQuestions = manualQuestions - q }
                )
            }

            // --- Code mode — รับข้อสอบด้วยโค้ด ---
            if (importMode == "CODE") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("รับข้อสอบด้วยโค้ด", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    Text("ขอโค้ด 6 ตัวอักษรจากผู้แชร์แล้วพิมพ์ด้านล่าง",
                        color = QTheme.colors.textSecondary, fontSize = 13.sp)

                    OutlinedTextField(
                        value = inputCode.uppercase(),
                        onValueChange = { if (it.length <= 6) inputCode = it.uppercase() },
                        label = { Text("โค้ด (6 ตัวอักษร)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 8.sp,
                            color = PrimaryBlue
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = QTheme.colors.textSecondary.copy(0.4f),
                            focusedTextColor = PrimaryBlue,
                            unfocusedTextColor = PrimaryBlue
                        )
                    )

                    if (importCodeState is com.petcompanion.presentation.viewmodel.QuizViewModel.ImportCodeState.Loading) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(28.dp), color = PrimaryBlue
                            )
                        }
                    }
                }
            }

            // dialog เพิ่ม/แก้คำถาม
            if (showAddQDialog) {
                ManualQuestionDialog(
                    question   = editingManualQ,
                    chapter    = manualChapter,
                    nextIndex  = manualQuestions.size,
                    onDismiss  = { showAddQDialog = false },
                    onConfirm  = { q ->
                        manualQuestions = if (editingManualQ != null)
                            manualQuestions.map { if (it === editingManualQ) q else it }
                        else
                            manualQuestions + q
                        showAddQDialog = false
                    }
                )
            }

            // --- ส่วนที่ 3: ปุ่มเลือกไฟล์ (เฉพาะ NEW/EXISTING) ---
            if (importMode == "NEW" || importMode == "EXISTING") Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(id = R.string.header_upload_json), color = PrimaryBlue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clickable { filePickerLauncher.launch("application/json") },
                    colors = CardDefaults.cardColors(containerColor = QTheme.colors.card),
                    border = BorderStroke(1.dp, if (jsonContent.isEmpty()) QTheme.colors.textSecondary.copy(alpha = 0.3f) else PrimaryBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (jsonContent.isEmpty()) Icons.Default.Add else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (jsonContent.isEmpty()) PrimaryBlue else GreenAccent,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (jsonContent.isEmpty()) {
                                stringResource(id = R.string.placeholder_select_file)
                            } else {
                                stringResource(id = R.string.label_file_name, fileName ?: "")
                            },
                            color = if (jsonContent.isEmpty()) QTheme.colors.textSecondary else Color.White
                        )
                    }
                }
            }

            // --- ข้อความแจ้งเตือน ---
            if (errorMessage != null) Text(errorMessage!!, color = RedAccent, fontWeight = FontWeight.Bold)
            if (isSuccess) Text(stringResource(id = R.string.msg_success_import), color = GreenAccent, fontWeight = FontWeight.Bold)

            LaunchedEffect(isSuccess) {
                if (isSuccess) {
                    delay(800)
                    onBack(selectedCategory?.id ?: categoryId)
                }
            }
            Spacer(modifier = Modifier.weight(1f))

            // --- ปุ่มกดยืนยัน ---
            Button(
                onClick = {
                    val finalCategoryId = selectedCategory?.id ?: -1
                    if (finalCategoryId == -1) {
                        errorMessage = "กรุณาเลือกหมวดหมู่วิชาก่อนบันทึกครับ"
                        return@Button
                    }

                    // ── CODE mode ───────────────────────────────────────────
                    if (importMode == "CODE") {
                        if (inputCode.length < 6) {
                            errorMessage = "กรุณากรอกโค้ด 6 ตัวอักษร"
                            return@Button
                        }
                        viewModel.importFromCode(inputCode, finalCategoryId, deviceId)
                        return@Button
                    }

                    // ── MANUAL mode ─────────────────────────────────────────
                    if (importMode == "MANUAL") {
                        if (manualTitle.isBlank()) {
                            errorMessage = "กรุณาใส่ชื่อวิชา"
                            return@Button
                        }
                        if (manualQuestions.isEmpty()) {
                            errorMessage = "กรุณาเพิ่มข้อสอบอย่างน้อย 1 ข้อ"
                            return@Button
                        }
                        viewModel.addManualCourse(
                            categoryId   = finalCategoryId,
                            title        = manualTitle.trim(),
                            courseCode   = manualCode.trim(),
                            description  = manualDesc.trim(),
                            timeLimit    = manualTimeLimit.toIntOrNull() ?: 30,
                            passingScore = manualPassing.toIntOrNull() ?: 60,
                            questions    = manualQuestions,
                            onDone       = { isSuccess = true; errorMessage = null }
                        )
                        return@Button
                    }

                    // ── Import JSON mode ─────────────────────────────────────
                    if (jsonContent.isEmpty()) {
                        errorMessage = context.getString(R.string.err_no_file)
                        return@Button
                    }

                    try {
                        val importedData = Gson().fromJson(jsonContent, QuizImportModel::class.java)

                        if (importMode == "EXISTING") {
                            if (selectedCourse == null) {
                                errorMessage = context.getString(R.string.err_no_target)
                                return@Button
                            }
                            if (importedData.courseCode != selectedCourse?.courseCode) {
                                errorMessage = context.getString(R.string.err_code_mismatch, importedData.courseCode)
                                return@Button
                            }
                        }

                        viewModel.importQuizData(importedData, selectedCourse?.id, finalCategoryId)
                        viewModel.clearPendingImport()

                        isSuccess = true
                        errorMessage = null
                    } catch (e: Exception) {
                        errorMessage = context.getString(R.string.err_json_invalid)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    when (importMode) {
                        "MANUAL" -> "บันทึกวิชา (${manualQuestions.size} ข้อ)"
                        "CODE"   -> "รับข้อสอบจากโค้ด"
                        else     -> stringResource(id = R.string.btn_process_import)
                    },
                    color = Color.White, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
// 📍 Component: ปุ่มเลือกโหมด
@Composable
fun ModeButton(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) PrimaryBlue else QTheme.colors.card,
            contentColor = if (isSelected) Color.White else QTheme.colors.textSecondary
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (!isSelected) BorderStroke(1.dp, QTheme.colors.textSecondary.copy(alpha = 0.2f)) else null
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

// 📍 Component: Dropdown ค้นหาวิชา (สไตล์ Material 3)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDropdown(
    courses: List<CourseEntity>,
    selectedCourse: CourseEntity?,
    onCourseSelected: (CourseEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedCourse?.let { "${it.courseCode} - ${it.title}" } ?: "กรุณาเลือกวิชาที่ต้องการอัปเดต...",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = QTheme.colors.card,
                unfocusedContainerColor = QTheme.colors.card,
                focusedTextColor = QTheme.colors.textPrimary,
                unfocusedTextColor = QTheme.colors.textSecondary,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = QTheme.colors.textSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(QTheme.colors.card)
        ) {
            courses.forEach { course ->
                DropdownMenuItem(
                    text = { Text("${course.courseCode} - ${course.title}", color = QTheme.colors.textPrimary) },
                    onClick = {
                        onCourseSelected(course)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
// 📍 Component: Dropdown ค้นหา "หมวดหมู่"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    categories: List<QuizCategoryEntity>,
    selectedCategory: QuizCategoryEntity?,
    onCategorySelected: (QuizCategoryEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedCategory?.title ?: "กรุณาเลือกหมวดหมู่วิชา...",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = QTheme.colors.card,
                unfocusedContainerColor = QTheme.colors.card,
                focusedTextColor = QTheme.colors.textPrimary,
                unfocusedTextColor = QTheme.colors.textSecondary,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = QTheme.colors.textSecondary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(QTheme.colors.card)
        ) {
            if (categories.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("ไม่มีหมวดหมู่ กรุณาสร้างก่อน", color = QTheme.colors.textSecondary) },
                    onClick = { expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            } else {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.title, color = QTheme.colors.textPrimary) },
                        onClick = {
                            onCategorySelected(category)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ManualCourseForm — ฟอร์มสร้างวิชา + รายการข้อสอบที่เพิ่มแล้ว
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ManualCourseForm(
    title: String, onTitleChange: (String) -> Unit,
    code: String, onCodeChange: (String) -> Unit,
    chapter: String, onChapterChange: (String) -> Unit,
    desc: String, onDescChange: (String) -> Unit,
    timeLimit: String, onTimeLimitChange: (String) -> Unit,
    passing: String, onPassingChange: (String) -> Unit,
    questions: List<com.petcompanion.domain.model.QuestionEntity>,
    onAddClick: () -> Unit,
    onEditClick: (com.petcompanion.domain.model.QuestionEntity) -> Unit,
    onDeleteClick: (com.petcompanion.domain.model.QuestionEntity) -> Unit
) {
    val colors = QTheme.colors
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        Text("ข้อมูลวิชา", color = PrimaryBlue, fontWeight = FontWeight.Bold)

        androidx.compose.material3.OutlinedTextField(
            value = title, onValueChange = onTitleChange,
            label = { Text("ชื่อวิชา *") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue, unfocusedBorderColor = colors.textSecondary.copy(0.4f),
                focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
            )
        )
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = code, onValueChange = onCodeChange,
                label = { Text("รหัสวิชา") }, modifier = Modifier.weight(1f), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue, unfocusedBorderColor = colors.textSecondary.copy(0.4f),
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                )
            )
            androidx.compose.material3.OutlinedTextField(
                value = chapter, onValueChange = onChapterChange,
                label = { Text("หัวข้อ/บท") }, modifier = Modifier.weight(1f), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue, unfocusedBorderColor = colors.textSecondary.copy(0.4f),
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                )
            )
        }
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = timeLimit, onValueChange = onTimeLimitChange,
                label = { Text("เวลา (นาที)") }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue, unfocusedBorderColor = colors.textSecondary.copy(0.4f),
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                )
            )
            androidx.compose.material3.OutlinedTextField(
                value = passing, onValueChange = onPassingChange,
                label = { Text("คะแนนผ่าน (%)") }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue, unfocusedBorderColor = colors.textSecondary.copy(0.4f),
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                )
            )
        }

        // ── รายการข้อสอบ ──────────────────────────────────────────────────
        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("ข้อสอบ (${questions.size} ข้อ)", color = PrimaryBlue, fontWeight = FontWeight.Bold)
            androidx.compose.material3.FilledTonalButton(
                onClick = onAddClick,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = PrimaryBlue.copy(0.15f))
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Default.Add, null,
                    modifier = Modifier.size(16.dp), tint = PrimaryBlue
                )
                androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                Text("เพิ่มข้อ", color = PrimaryBlue, fontSize = 13.sp)
            }
        }

        questions.forEachIndexed { i, q ->
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card)
            ) {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${i + 1}", color = PrimaryBlue, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(q.text, color = colors.textPrimary, fontSize = 13.sp, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(
                            "✓ ${q.options.getOrElse(q.correctIndex) { "" }}",
                            color = GreenAccent, fontSize = 11.sp
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = { onEditClick(q) }) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Edit,
                            null, tint = colors.textSecondary, modifier = Modifier.size(18.dp)
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = { onDeleteClick(q) }) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Delete,
                            null, tint = RedAccent, modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (questions.isEmpty()) {
            Text("ยังไม่มีข้อสอบ กด + เพิ่มข้อ เพื่อเริ่มต้น",
                color = colors.textSecondary, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ManualQuestionDialog — dialog เพิ่ม/แก้ไขข้อสอบ
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ManualQuestionDialog(
    question: com.petcompanion.domain.model.QuestionEntity?,
    chapter: String,
    nextIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (com.petcompanion.domain.model.QuestionEntity) -> Unit
) {
    val isEdit = question != null
    var text          by remember { mutableStateOf(question?.text ?: "") }
    var choices       by remember { mutableStateOf(
        question?.options?.takeIf { it.size in 3..5 } ?: listOf("", "", "", "")
    )}
    var correctIdx    by remember { mutableIntStateOf(question?.correctIndex ?: 0) }
    var explanation   by remember { mutableStateOf(question?.explanation ?: "") }
    val colors = QTheme.colors

    val choiceLabels = listOf("A", "B", "C", "D", "E")

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = colors.card,
        title = {
            Text(if (isEdit) "แก้ไขข้อที่ ${(question?.let { _ -> nextIndex } ?: nextIndex) + 1}"
                 else "เพิ่มข้อที่ ${nextIndex + 1}",
                color = PrimaryBlue, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                // โจทย์
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("โจทย์คำถาม *") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue, unfocusedBorderColor = colors.textSecondary.copy(0.4f),
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                    )
                )

                // ตัวเลือก 3-5 ข้อ (เลือกข้อที่ถูกต้อง)
                Text("ตัวเลือก ${choices.size} ข้อ (เลือกข้อที่ถูกต้อง)",
                    color = colors.textSecondary, fontSize = 12.sp)
                choices.forEachIndexed { i, choice ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        androidx.compose.material3.RadioButton(
                            selected = correctIdx == i,
                            onClick  = { correctIdx = i },
                            colors   = RadioButtonDefaults.colors(selectedColor = GreenAccent)
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value         = choice,
                            onValueChange = { v -> choices = choices.toMutableList().also { it[i] = v } },
                            label         = { Text(choiceLabels[i]) },
                            singleLine    = true, modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = if (correctIdx == i) GreenAccent else PrimaryBlue,
                                unfocusedBorderColor = if (correctIdx == i) GreenAccent.copy(0.5f) else colors.textSecondary.copy(0.4f),
                                focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                            )
                        )
                        // ปุ่มลบ — ลบได้เมื่อเหลือมากกว่า 3 ข้อ
                        if (choices.size > 3) {
                            androidx.compose.material3.IconButton(onClick = {
                                val newList = choices.toMutableList().also { it.removeAt(i) }
                                choices = newList
                                // ปรับ correctIdx ถ้าจำเป็น
                                if (correctIdx >= newList.size) correctIdx = newList.size - 1
                                else if (correctIdx == i) correctIdx = 0
                            }) {
                                Icon(Icons.Default.Close, "ลบตัวเลือก",
                                    tint = RedAccent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // ปุ่มเพิ่มตัวเลือก — เพิ่มได้สูงสุด 5 ข้อ
                if (choices.size < 5) {
                    androidx.compose.material3.TextButton(
                        onClick = { choices = choices + "" }
                    ) {
                        Icon(Icons.Default.Add, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("เพิ่มตัวเลือก", color = PrimaryBlue, fontSize = 13.sp)
                    }
                }

                // คำอธิบาย
                androidx.compose.material3.OutlinedTextField(
                    value = explanation, onValueChange = { explanation = it },
                    label = { Text("คำอธิบาย / เฉลย (ไม่บังคับ)") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue, unfocusedBorderColor = colors.textSecondary.copy(0.4f),
                        focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    if (text.isBlank() || choices.any { it.isBlank() }) return@Button
                    onConfirm(
                        com.petcompanion.domain.model.QuestionEntity(
                            qid          = question?.qid ?: 0,
                            courseId     = 0,
                            chapter      = chapter,
                            text         = text.trim(),
                            options      = choices.map { it.trim() },
                            correctIndex = correctIdx,
                            explanation  = explanation.trim()
                        )
                    )
                },
                enabled = text.isNotBlank() && choices.all { it.isNotBlank() },
                colors  = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text(if (isEdit) "บันทึก" else "เพิ่ม", color = Color.White) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = colors.textSecondary)
            }
        }
    )
}