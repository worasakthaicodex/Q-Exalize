package com.petcompanion.presentation.view

// Android & Compose Core
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType

// Layouts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions

// Material Design 3
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

// อย่าลืม 2 ตัวนี้เพื่อให้ 'by remember' ทำงานได้ (สำคัญมาก!)
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// สำหรับแปลงสี Hex String เป็น Color
import android.graphics.Color.parseColor
import com.petcompanion.domain.model.QuizCategoryEntity
import androidx.compose.ui.res.stringResource
import com.petcompanion.R
import com.petcompanion.presentation.theme.QTheme



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFormScreen(
    categoryToEdit: QuizCategoryEntity? = null,
    onSaveClick: (QuizCategoryEntity) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf(categoryToEdit?.title ?: "") }
    var description by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(categoryToEdit?.iconName ?: "Face") }
    var selectedColor by remember { mutableStateOf(categoryToEdit?.colorHex ?: "#2196F3") }

    val scrollState = rememberScrollState()

    // ใช้สีพื้นหลังของแอปคุณ (QTheme.colors.background)
    Scaffold(
        containerColor = QTheme.colors.background, // หรือเปลี่ยนเป็น QTheme.colors.background ของคุณ
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        // 📍 เลือกคำแปลตามสถานะ (สร้างใหม่ หรือ แก้ไข)
                        text = if (categoryToEdit == null) {
                            stringResource(id = R.string.title_new_category)
                        } else {
                            stringResource(id = R.string.title_edit_category)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = QTheme.colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // 📍 คำว่า "Back" ใน contentDescription ไม่ค่อยซีเรียสเพราะใช้กับ Screen Reader
                        // แต่ถ้าอยากแปลด้วยก็ใช้ stringResource(id = R.string.btn_back) ที่เราเคยทำไว้ได้ครับ
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(id = R.string.btn_back), tint = QTheme.colors.textPrimary)
                    }
                },
                // ปรับ TopAppBar ให้มืด
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = QTheme.colors.card
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- ส่วนที่ 1: Input Field (ปรับสีให้เข้าธีม) ---
            // 📍 1. ช่องกรอกชื่อหมวดหมู่
            CustomDarkTextField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(id = R.string.label_category_name),
                placeholder = stringResource(id = R.string.placeholder_category_name)
            )

            Spacer(modifier = Modifier.height(16.dp))

// 📍 2. ช่องกรอกรายละเอียด
            CustomDarkTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(id = R.string.label_description),
                placeholder = stringResource(id = R.string.placeholder_description)
            )

            // --- ส่วนที่ 2: Icon Selection (หน้าตาแบบการ์ดลอย) ---
            Text("SELECT SYMBOL", color = Color.Gray, style = MaterialTheme.typography.labelLarge)
            IconSelector(selectedIcon = selectedIcon, onIconSelected = { selectedIcon = it })

            // --- ส่วนที่ 3: Color Selection ---
            Text("SELECT THEME COLOR", color = Color.Gray, style = MaterialTheme.typography.labelLarge)
            ColorSelector(selectedColor = selectedColor, onColorSelected = { selectedColor = it })

            Spacer(modifier = Modifier.weight(1f))

            // --- ปุ่มบันทึก (PrimaryBlue สุดเท่) ---
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSaveClick(QuizCategoryEntity(
                            id = categoryToEdit?.id ?: 0,
                            title = title,
                            description = description,
                            progress = categoryToEdit?.progress ?: 0.0f,
                            iconName = selectedIcon,
                            colorHex = selectedColor,
                            totalQuizzes = categoryToEdit?.totalQuizzes ?: 0
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)), // PrimaryBlue
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("SAVE DATA", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// 📍 ตัวช่วยสร้าง TextField สไตล์ Dark
@Composable
fun CustomDarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isNumber: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Color.DarkGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = QTheme.colors.textPrimary,
                unfocusedTextColor = QTheme.colors.textPrimary,
                focusedBorderColor = Color(0xFF2196F3), // PrimaryBlue
                unfocusedBorderColor = Color.DarkGray,
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconSelector(selectedIcon: String, onIconSelected: (String) -> Unit) {
    // รายชื่อไอคอนที่เราจะให้เลือก
    val icons = listOf("Face", "Build", "Star", "Create", "Notifications", "Info", "Lock", "Person")

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(icons) { iconName ->
            FilterChip(
                selected = selectedIcon == iconName,
                onClick = { onIconSelected(iconName) },
                label = { Text(iconName) },
                leadingIcon = {
                    Icon(
                        imageVector = getIconByName(iconName),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun ColorSelector(selectedColor: String, onColorSelected: (String) -> Unit) {
    // รายชื่อสี Hex
    val colors = listOf("#2196F3", "#F44336", "#4CAF50", "#FFC107", "#9C27B0", "#795548", "#607D8B")

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(colors) { hex ->
            val color = Color(android.graphics.Color.parseColor(hex))
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (selectedColor == hex) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary, // ใช้สี Primary ของ Theme มาทำขอบตอนเลือก
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(hex) }
            )
        }
    }
}

// ฟังก์ชันแปลงชื่อ String เป็น ImageVector (ถ้าแดงตรง Icons.Default ให้ Alt+Enter เพื่อ Import)
fun getIconByName(name: String): ImageVector {
    return when (name) {
        "Face" -> Icons.Default.Face
        "Build" -> Icons.Default.Build
        "Star" -> Icons.Default.Star
        "Create" -> Icons.Default.Create
        "Notifications" -> Icons.Default.Notifications
        "Lock" -> Icons.Default.Lock
        "Person" -> Icons.Default.Person
        else -> Icons.Default.Info
    }
}


