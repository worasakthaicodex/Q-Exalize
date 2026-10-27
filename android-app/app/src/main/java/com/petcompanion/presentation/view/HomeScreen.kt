package com.petcompanion.presentation.view

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.R
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.UserViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage


import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.rounded.School
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
@Composable
fun DarkHomeDashboard(
    viewModel: UserViewModel,
    currentScreen: String,
    isPremiumUser: Boolean = true,
    onNavigateToCategory: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onOnlineClick: () -> Unit,
    onNotifiClick: () -> Unit,
    onMusicClick: () -> Unit,
    onGameClick: () -> Unit = {},
    onClassroomClick: () -> Unit = {},
    onQuizManageClick: () -> Unit = onNavigateToCategory,
    unreadNotifCount: Int = 0,
    // Dynamic Firebase data
    upcomingEvents: List<com.petcompanion.data.firebase.ScheduleEvent> = emptyList(),
    pendingHomework: List<com.petcompanion.data.firebase.HomeworkItem> = emptyList(),
    onScheduleClick: () -> Unit = onClassroomClick,
    onHomeworkClick: () -> Unit = onClassroomClick,
) {
    var showProGateDialog by remember { mutableStateOf(false) }
    if (showProGateDialog) {
        ProGateDialog(onDismiss = { showProGateDialog = false })
    }

    Scaffold(
        containerColor = QTheme.colors.background,
        // 📍 เอาเมนูด้านล่างสุดเท่ของคุณมาใส่ตรงนี้
        bottomBar = {
            Box(modifier = Modifier.navigationBarsPadding()) {
            DarkBottomNav(
                currentScreen = currentScreen,
                onHomeClick = { /* อยู่หน้า Home อยู่แล้ว */ },
                onListClick = onNavigateToCategory, // ให้ปุ่ม List พาไปหน้าเลือกหมวดหมู่
                onAddClick = { /* TODO: สร้างหน้า Add Quiz */ },
                onProfileClick = onProfileClick,    // วิ่งไปหา MainActivity
                onSettingsClick = onSettingsClick   // วิ่งไปหา MainActivity
            )
        }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            GreetingSection(viewModel = viewModel, onNotifiClick = onNotifiClick, unreadCount = unreadNotifCount)
            Spacer(modifier = Modifier.height(24.dp))

            // ── แบนเนอร์: เกมเพื่อการศึกษา ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
            ) {
                // 🖼️ รูปแบนเนอร์ชัดๆ ไม่มี overlay
                Image(
                    painter = painterResource(id = R.drawable.selectgame),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
                // 📝 ชื่อ + ปุ่ม มุมล่างซ้าย
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onGameClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.home_game_banner_btn),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── ช่องเล็ก 2 อัน: ข้อสอบ + ออนไลน์ ───────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SecondaryActionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(id = R.string.take_a_quiz),
                    subtitle = stringResource(id = R.string.skillsknowledge),
                    icon = Icons.Rounded.School,
                    accentColor = PrimaryBlue,
                    onClick = onNavigateToCategory
                )
                SecondaryActionCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(id = R.string.online_arena_title),
                    subtitle = stringResource(id = R.string.online_arena_subtitle),
                    icon = Icons.Rounded.Groups,
                    accentColor = GreenAccent,
                    onClick = onOnlineClick
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            MiniAppsSection(
                upcomingEvents   = upcomingEvents,
                pendingHomework  = pendingHomework,
                onScheduleClick  = onScheduleClick,
                onHomeworkClick  = onHomeworkClick,
                onClassroomClick = onClassroomClick,
                onQuizManageClick = onQuizManageClick,
                onOnlineClick    = onOnlineClick,
                onHistoryClick   = onHistoryClick
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DarkTopHeader(onNotifiClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(PrimaryBlue), contentAlignment = Alignment.Center) { Text("N", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.width(16.dp))
            Column { Text("Hello, Nayeem", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold);
                Text(stringResource(id = R.string.Readytolearn), color = QTheme.colors.textSecondary, fontSize = 14.sp) }
        }
        Box(
            modifier = Modifier.size(45.dp).clip(CircleShape).background(QTheme.colors.card).clickable { onNotifiClick() },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Notifications, null, tint = Color.White)
        }
    }
}

@Composable
fun HeroActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    buttonText: String,
    onClick: () -> Unit
) {
    // 🎨 สร้าง Gradient แบบในเว็บ (ม่วง-ฟ้า-เขียว)
    // หรือจะใช้เฉดสีเดียวกับ accentColor ก็ได้ครับ
    val bannerGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF8E44AD), // ม่วง
            Color(0xFF3498DB), // ฟ้า
            Color(0xFF2ECC71)  // เขียว
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        // ใช้พื้นหลังโปร่งใสเพราะเราจะวาดสีเองข้างใน
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(bannerGradient) // ใส่ Gradient ที่นี่
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            // 🚀 Decorative Icon (ไอคอนตกแต่งด้านหลัง)
            Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp) // ให้ล้นขอบนิดๆ แบบในเว็บ
                    .graphicsLayer(alpha = 0.2f), // ปรับให้จาง
                tint = Color.White
            )

            // Content หลัก
            Column(
                modifier = Modifier.fillMaxWidth(0.85f) // เว้นที่ให้ไอคอนด้านหลังหน่อย
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ไอคอนจิ๋วในกรอบกระจก (Glassmorphism Effect)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ปุ่มกดสไตล์กระจก
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(25.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = buttonText,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SecondaryActionCard(
    modifier: Modifier = Modifier, // (ใส่ = Modifier ไว้เผื่อการ์ดไหนไม่ได้ส่งค่ามาจะได้ไม่พัง)
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit = {} // 📍 1. เพิ่มช่องรับคำสั่งกดตรงนี้
) {
    Card(
        modifier = modifier
            .height(160.dp)
            .clickable { onClick() }, // 📍 2. สั่งให้การ์ดทั้งใบกดได้!
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(modifier = Modifier.size(45.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accentColor)
            }
            Column {
                Text(title, color =QTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = QTheme.colors.textSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DarkBottomNav(
    currentScreen: String, // รับชื่อหน้าปัจจุบันมา เพื่อเปลี่ยนสีไอคอน
    onHomeClick: () -> Unit,
    onListClick: () -> Unit,
    onAddClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(color = QTheme.colors.card, modifier = Modifier.fillMaxWidth().height(80.dp)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            IconButton(onClick = onHomeClick) {
                Icon(Icons.Default.Home, contentDescription = "Home", tint = if (currentScreen == "Home") PrimaryBlue else QTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
            }
            // 2. List
            IconButton(onClick = onListClick) {
                Icon(Icons.Default.List, contentDescription = "List", tint = if (currentScreen == "QuizList") PrimaryBlue else QTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
            }

            /*
            // 3. Add (ปุ่มบวกตรงกลาง)
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.offset(y = (-15).dp).size(55.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
            */

            // 4. Person (Profile)
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Default.Person, contentDescription = "Profile", tint = if (currentScreen == "Profile") PrimaryBlue else QTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
            }
            // 5. Settings
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (currentScreen == "Settings") PrimaryBlue else QTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
            }
        }
    }
}


@Composable
fun GreetingSection(viewModel: UserViewModel, onNotifiClick: () -> Unit, unreadCount: Int = 0) {
    // 📍 1. ดึงข้อมูลจากฐานข้อมูลผ่าน ViewModel
    val profileState by viewModel.userProfile.collectAsState()

    // จัดการข้อมูลชื่อและรูป (ถ้าไม่มีให้ใช้ค่า Default)
    val displayName = profileState?.name ?: "User"
    val imageUri =profileState?.profileImagePath;

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {

            // 📍 2. ส่วนแสดงรูปโปรไฟล์
            if (!imageUri.isNullOrEmpty()) {
                // ถ้ามีรูปในฐานข้อมูล ให้แสดงรูปนั้น
                AsyncImage(
                    model = Uri.fromFile(java.io.File(imageUri)),
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // ถ้าไม่มีรูป ให้แสดงวงกลมสีฟ้าพร้อมตัวอักษรตัวแรกของชื่อ
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlue), // สีที่คุณตั้งไว้
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        color = QTheme.colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 📍 3. ส่วนแสดงชื่อผู้ใช้
            Column {
                Text(
                    text = stringResource(id = R.string.hello_user, displayName),
                    color = QTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.Readytolearn),
                    color = QTheme.colors.textSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // ปุ่มแจ้งเตือนด้านขวา + badge จำนวนที่ยังไม่อ่าน
        Box(
            modifier = Modifier
                .size(45.dp)
                .clip(CircleShape)
                .background(QTheme.colors.card).clickable { onNotifiClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Notifications, null, tint = QTheme.colors.textPrimary)
            // Badge แดง — แสดงเมื่อมีการแจ้งเตือนที่ยังไม่อ่าน
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(if (unreadCount > 9) 18.dp else 16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF5350)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else "$unreadCount",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MiniAppsSection — 5 ปุ่มถาวร (โชว์เสมอ)
// ตารางงาน · ห้องเรียน · ส่งการบ้าน · จัดการข้อสอบ · ประวัติ
// ตารางงาน/ส่งการบ้าน มี badge จำนวนเมื่อมีข้อมูลรวมทุกห้อง
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MiniAppsSection(
    upcomingEvents: List<com.petcompanion.data.firebase.ScheduleEvent> = emptyList(),
    pendingHomework: List<com.petcompanion.data.firebase.HomeworkItem> = emptyList(),
    onScheduleClick: () -> Unit = {},
    onHomeworkClick: () -> Unit = {},
    onClassroomClick: () -> Unit = {},
    onQuizManageClick: () -> Unit = {},
    onOnlineClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {

        // แถวที่ 1: ตารางงาน · ห้องเรียน · ส่งการบ้าน
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MiniAppButton(
                label      = "ตารางงาน",
                icon       = Icons.Default.CalendarMonth,
                color      = Color(0xFFE67E22),
                badgeCount = upcomingEvents.size,
                onClick    = onScheduleClick
            )
            MiniAppButton(
                label   = "ห้องเรียน",
                icon    = Icons.Default.MeetingRoom,
                color   = Color(0xFFE74C3C),
                onClick = onClassroomClick
            )
            MiniAppButton(
                label      = "ส่งการบ้าน",
                icon       = Icons.Default.UploadFile,
                color      = Color(0xFF2ECC71),
                badgeCount = pendingHomework.size,
                onClick    = onHomeworkClick
            )
        }

        // แถวที่ 2: จัดการข้อสอบ · ประวัติ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MiniAppButton(
                label   = "จัดการข้อสอบ",
                icon    = Icons.Default.MenuBook,
                color   = Color(0xFF9B59B6),
                onClick = onQuizManageClick
            )
            MiniAppButton(
                label   = "ประวัติ",
                icon    = Icons.Default.DateRange,
                color   = OrangeAccent,
                onClick = onHistoryClick
            )
            // ช่องว่างให้จัดเรียงสวย (3 คอลัมน์)
            Spacer(Modifier.width(72.dp))
        }
    }
}

// ── Dynamic Info Card ─────────────────────────────────────────────────────────
@Composable
private fun DynamicInfoCard(
    icon: ImageVector, iconColor: Color,
    title: String, count: Int,
    preview: String, subPreview: String,
    onClick: () -> Unit
) {
    val colors = QTheme.colors
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = colors.card)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = colors.textPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Surface(shape = RoundedCornerShape(6.dp), color = iconColor) {
                        Text("$count", color = Color.White, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                Text(preview, color = colors.textPrimary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subPreview.isNotBlank()) {
                    Text(subPreview, color = colors.textSecondary, fontSize = 11.sp)
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}


// 📍 Pro Gate Dialog
@Composable
fun ProGateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QTheme.colors.card,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ฟีเจอร์ Pro", color = QTheme.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(stringResource(id = R.string.pro_gate_title), color = QTheme.colors.textSecondary, fontSize = 12.sp)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ฟีเจอร์นี้ใช้ได้เฉพาะเวอร์ชัน Pro เท่านั้น", color = QTheme.colors.textPrimary, textAlign = TextAlign.Center, fontSize = 14.sp)
                Text(stringResource(id = R.string.pro_gate_desc), color = QTheme.colors.textSecondary, textAlign = TextAlign.Center, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("เข้าใจแล้ว", color = Color.White, fontWeight = FontWeight.Bold)
                Text("  /  ${stringResource(id = R.string.pro_gate_btn)}", color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
        }
    )
}

// 📍 Component ลูก: ดีไซน์ปุ่มจิ๋วแบบในรูป
@Composable
fun MiniAppButton(
    label: String,
    icon: ImageVector,
    color: Color,
    isLocked: Boolean = false,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isLocked) color.copy(alpha = 0.4f) else color,
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = if (isLocked) 0.5f else 1f),
                modifier = Modifier.size(28.dp)
            )
            // 🔒 Lock badge มุมขวาบน
            if (isLocked) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                )
            }
            // 🔢 จำนวน badge มุมขวาบน
            if (badgeCount > 0 && !isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(20.dp)
                        .background(Color(0xFFE53935), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 9) "9+" else "$badgeCount",
                        color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            color = if (isLocked) QTheme.colors.textSecondary.copy(alpha = 0.5f)
                    else QTheme.colors.textSecondary.copy(alpha = 0.9f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}