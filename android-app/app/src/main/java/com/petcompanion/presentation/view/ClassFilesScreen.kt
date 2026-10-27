package com.petcompanion.presentation.view

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.data.firebase.ClassFile
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.theme.QTheme
import com.petcompanion.presentation.viewmodel.ClassroomViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClassFilesScreen(
    viewModel: ClassroomViewModel,
    onBackClick: () -> Unit
) {
    val files        by viewModel.files.collectAsState()
    val isTeacher    by viewModel.isTeacherMode.collectAsState()
    val teacherError by viewModel.teacherError.collectAsState()
    val context      = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt = SimpleDateFormat("d MMM yyyy", Locale("th", "TH"))

    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(teacherError) {
        teacherError?.let { viewModel.clearTeacherError(); snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = QTheme.colors.background,
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isTeacher) {
                FloatingActionButton(
                    onClick        = { showAddDialog = true },
                    containerColor = Color(0xFF0097A7),
                    contentColor   = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "เพิ่มไฟล์") }
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
                    Text(stringResource(R.string.files_title),
                        color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.files_subtitle),
                        color = QTheme.colors.textSecondary, fontSize = 12.sp)
                }
                if (isTeacher) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0097A7).copy(0.15f)) {
                        Text("ครู", color = Color(0xFF0097A7), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (files.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, null,
                                tint = QTheme.colors.textSecondary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.files_no_files),
                                color = QTheme.colors.textSecondary, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(files) { file ->
                    FileCard(
                        file      = file,
                        fmt       = fmt,
                        isTeacher = isTeacher,
                        onDelete  = { viewModel.deleteFile(file.id) },
                        onOpen    = {
                            if (file.downloadUrl.isNotBlank()) {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(file.downloadUrl)).apply {
                                            setDataAndType(Uri.parse(file.downloadUrl), "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    )
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(file.downloadUrl)))
                                }
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddFileDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { file ->
                viewModel.addFile(file)
                showAddDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FileCard
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FileCard(
    file: ClassFile,
    fmt: SimpleDateFormat,
    isTeacher: Boolean,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val (fileIcon, fileColor) = when (file.type.lowercase()) {
        "pdf"         -> Pair(Icons.Default.PictureAsPdf, Color(0xFFE53935))
        "doc", "docx" -> Pair(Icons.Default.Description,  Color(0xFF1565C0))
        "xls", "xlsx" -> Pair(Icons.Default.TableChart,   Color(0xFF2E7D32))
        "ppt", "pptx" -> Pair(Icons.Default.Slideshow,    Color(0xFFE65100))
        else          -> Pair(Icons.Default.InsertDriveFile, Color(0xFF546E7A))
    }
    val sizeMb = if (file.sizeBytes > 0) "%.1f MB".format(file.sizeBytes / 1_048_576.0) else ""

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
                shape    = RoundedCornerShape(10.dp),
                color    = fileColor.copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(fileIcon, null, tint = fileColor, modifier = Modifier.size(26.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sizeMb.isNotBlank())
                        Text("${stringResource(R.string.files_size_label)} $sizeMb",
                            color = QTheme.colors.textSecondary, fontSize = 11.sp)
                    if (file.uploadedAt > 0)
                        Text("${stringResource(R.string.files_uploaded_label)} ${fmt.format(Date(file.uploadedAt))}",
                            color = QTheme.colors.textSecondary, fontSize = 11.sp)
                }
            }

            // Delete (teacher only)
            if (isTeacher) {
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteOutline, null,
                        tint = Color(0xFFEF5350), modifier = Modifier.size(20.dp))
                }
            }

            // Open button
            Button(
                onClick = onOpen,
                shape   = RoundedCornerShape(8.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = fileColor),
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.files_open_btn), fontSize = 12.sp)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = QTheme.colors.card,
            title  = { Text("ลบไฟล์?", color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text   = { Text("ลบ \"${file.name}\" ออกจากรายการ?", color = QTheme.colors.textSecondary) },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))) { Text("ลบ") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("ยกเลิก") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AddFileDialog  (teacher only)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AddFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (ClassFile) -> Unit
) {
    var name    by remember { mutableStateOf("") }
    var url     by remember { mutableStateOf("") }
    var type    by remember { mutableStateOf("pdf") }

    val types = listOf("pdf", "doc", "xls", "ppt", "other")
    val typeLabels = mapOf(
        "pdf" to "📄 PDF", "doc" to "📝 Word", "xls" to "📊 Excel",
        "ppt" to "📊 PPT", "other" to "📁 อื่นๆ"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = QTheme.colors.card,
        title = { Text("เพิ่มไฟล์ / ลิ้งก์",
            color = QTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("ชื่อไฟล์ *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors())
                OutlinedTextField(value = url, onValueChange = { url = it },
                    label = { Text("URL / ลิ้งก์ดาวน์โหลด *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = classroomTfColors())
                // Type chips
                Text("ประเภทไฟล์", color = QTheme.colors.textSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    types.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick  = { type = t },
                            label    = { Text(typeLabels[t] ?: t, fontSize = 10.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0097A7).copy(0.2f),
                                selectedLabelColor     = Color(0xFF0097A7)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    if (name.isNotBlank() && url.isNotBlank())
                        onConfirm(ClassFile(name = name.trim(), downloadUrl = url.trim(), type = type))
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF0097A7))
            ) { Text("บันทึก") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = QTheme.colors.textSecondary) }
        }
    )
}
