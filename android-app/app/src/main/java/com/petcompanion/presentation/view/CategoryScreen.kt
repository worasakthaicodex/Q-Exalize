package com.petcompanion.presentation.view
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.QuizViewModel
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.*
import com.petcompanion.domain.model.QuizCategoryEntity

import androidx.compose.ui.res.stringResource

data class QuizCategory(val id: Int, val title: String, val totalQuizzes: Int, val completedPercent: Float, val icon: ImageVector, val color: Color)

@Composable
fun QuizCategoryScreen(
    viewModel: QuizViewModel,
    onBackClick: () -> Unit,
    onCategoryClick: (Int, String) -> Unit,
    currentScreen: String,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddClick: () -> Unit,
    onHomeClick: () -> Unit,
    onEditCategory: (QuizCategoryEntity) -> Unit,
) {
    val categoriesFromDb by viewModel.categoryList.collectAsState()
    val allCourses by viewModel.allCourses.collectAsState(initial = emptyList())

    // 📍 1. สร้าง State ไว้จำว่ากำลังจะลบหมวดหมู่ไหน (ถ้าเป็น null คือยังไม่ได้กดลบ)
    var categoryToDelete by remember { mutableStateOf<QuizCategoryEntity?>(null) }

    Scaffold(
        containerColor = QTheme.colors.background,
        bottomBar = {
            Box(modifier = Modifier.navigationBarsPadding()) {
            DarkBottomNav2(
                currentScreen = currentScreen,
                onHomeClick = onHomeClick,
                onListClick = { /* อยู่หน้า List อยู่แล้ว */ },
                onAddClick = onAddClick,
                onProfileClick = onProfileClick,
                onSettingsClick = onSettingsClick
            )
        }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // --- Top Bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(id = R.string.btn_back), tint = QTheme.colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.SelectCategory),
                    color = QTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // --- รายการหมวดหมู่วิชา ---
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (categoriesFromDb.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(id = R.string.empty_category_message),
                            color = QTheme.colors.textPrimary,
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp
                        )
                    }
                }

                items(categoriesFromDb, key = { it.id }) { entity ->
                    val categoryCourses = allCourses.filter { it.categoryId == entity.id }
                    val realTotalQuizzes = categoryCourses.size
                    val realProgress = if (realTotalQuizzes > 0) {
                        categoryCourses.map { it.progress }.average().toFloat()
                    } else {
                        0f
                    }

                    val mappedCategory = QuizCategory(
                        id = entity.id,
                        title = entity.title,
                        totalQuizzes = realTotalQuizzes,
                        completedPercent = realProgress,
                        icon = getIconByName(entity.iconName),
                        color = Color(android.graphics.Color.parseColor(entity.colorHex))
                    )

                    SwipeRevealCategoryCard(
                        category = mappedCategory,
                        onClick = { onCategoryClick(mappedCategory.id, mappedCategory.title) },
                        onEditClick = { onEditCategory(entity) },
                        // 📍 2. เปลี่ยนจากสั่งลบตรงๆ เป็นส่ง Entity ไปเข้าคิวรอ Confirm
                        onDeleteClick = { categoryToDelete = entity }
                    )
                }
            }
        }
    }

    // 📍 3. UI แจ้งเตือน (Dialog) จะโผล่มาเมื่อ categoryToDelete ไม่ใช่ null
    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            containerColor = QTheme.colors.card, // ใช้สีเทาเข้มของ Card
            title = {
                Text(
                    text = stringResource(id = R.string.dialog_delete_category_title),
                    color = QTheme.colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.dialog_delete_category_desc, categoryToDelete?.title ?: ""),
                    color = QTheme.colors.textPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 📍 กดยืนยันปุ๊บ ค่อยสั่ง ViewModel ให้ลบจริง แล้วเคลียร์ค่ากลับเป็น null
                        categoryToDelete?.let { viewModel.deleteCategory(it) }
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)) // สีแดงอันตราย
                ) {
                    Text(text = stringResource(id = R.string.btn_delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { // กดยกเลิกก็แค่เคลียร์ค่าทิ้ง
                    Text(text = stringResource(id = R.string.btn_cancel), color = QTheme.colors.textPrimary)
                }
            }
        )
    }
}






@Composable
// 📍 จุดที่ 3: เพิ่ม onClick เข้ามาที่ Component ของการ์ด
fun CategoryProgressCard(category: QuizCategory, onClick: () -> Unit) {
    Card(
        // 📍 จุดที่ 4: สั่งให้การ์ดกดได้ผ่าน modifier
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(category.color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(category.title, color = QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${category.totalQuizzes} Quizzes Available", color = QTheme.colors.textPrimary, fontSize = 13.sp)
                }

                Text(
                    text = "${(category.completedPercent * 100).toInt()}%",
                    color = category.color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = category.completedPercent,
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = category.color,
                    trackColor = QTheme.colors.background,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val statusText = when {
                category.completedPercent == 1.0f -> "Completed!"
                category.completedPercent > 0f -> "In Progress"
                else -> "Not Started"
            }
            Text(statusText, color = QTheme.colors.textPrimary, fontSize = 12.sp)
        }
    }
}

@Composable
fun SwipeRevealCategoryCard(
    category: QuizCategory,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // 📍 ปรับระยะการปัดให้สั้นลง พอดีกับปุ่มกลม 2 อัน (ปุ่ม 48dp * 2 + ช่องว่าง)
    val buttonWidth = 130.dp
    val buttonWidthPx = with(LocalDensity.current) { buttonWidth.toPx() }

    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 📍 1. ส่วนปุ่มด้านหลัง (เปลี่ยนเป็นวงกลมสไตล์ iOS)
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 16.dp), // ขยับปุ่มให้ห่างจากขอบจอด้านขวานิดนึง
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- ปุ่ม Edit (สีเขียว วงกลม) ---
            Box(
                modifier = Modifier
                    .size(48.dp) // ขนาดกลมๆ กำลังดี ไม่เล็กไม่ใหญ่ไป
                    .clip(CircleShape) // ตัดขอบเป็นวงกลม
                    .background(Color(0xFF4CAF50))
                    .clickable {
                        coroutineScope.launch { offsetX.animateTo(0f) }
                        onEditClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(12.dp)) // ระยะห่างระหว่าง 2 ปุ่ม

            // --- ปุ่ม Delete (สีแดง วงกลม) ---
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape) // ตัดขอบเป็นวงกลม
                    .background(Color(0xFFF44336))
                    .clickable {
                        coroutineScope.launch { offsetX.animateTo(0f) }
                        onDeleteClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // 📍 2. ส่วนตัวการ์ดด้านหน้า (เอานิ้วลากได้เหมือนเดิม)
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < -buttonWidthPx / 2) {
                                    offsetX.animateTo(-buttonWidthPx)
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-buttonWidthPx, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            // เรียก Card ของจริงมาโชว์
            CategoryProgressCard(
                category = category,
                onClick = {
                    if (offsetX.value < 0f) {
                        coroutineScope.launch { offsetX.animateTo(0f) }
                    } else {
                        onClick()
                    }
                }
            )
        }
    }
}


@Composable
fun DarkBottomNav2(
    currentScreen: String, // รับชื่อหน้าปัจจุบันมา เพื่อเปลี่ยนสีไอคอน
    onHomeClick: () -> Unit,
    onListClick: () -> Unit,
    onAddClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,

    ) {
    Surface(color = QTheme.colors.card, modifier = Modifier.fillMaxWidth().height(80.dp)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            IconButton(onClick = onHomeClick) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = if (currentScreen == "Home") PrimaryBlue else QTheme.colors.textPrimary, modifier = Modifier.size(28.dp))
            }
            // 2. List
            IconButton(onClick = onListClick) {
                Icon(Icons.Default.List, contentDescription = "List", tint = if (currentScreen == "QuizList") PrimaryBlue else QTheme.colors.textPrimary, modifier = Modifier.size(28.dp))
            }


            // 3. Add (ปุ่มบวกตรงกลาง)
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.offset(y = (-10).dp).size(55.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }


            // 4. Person (Profile)
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Default.Person, contentDescription = "Profile", tint = if (currentScreen == "Profile") PrimaryBlue else QTheme.colors.textPrimary, modifier = Modifier.size(28.dp))
            }
            // 5. Settings
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (currentScreen == "Settings") PrimaryBlue else QTheme.colors.textPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }
}