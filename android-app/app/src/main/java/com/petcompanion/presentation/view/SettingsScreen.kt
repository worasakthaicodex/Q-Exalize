package com.petcompanion.presentation.view
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.presentation.theme.PrimaryBlue

import androidx.compose.ui.res.stringResource // ต้อง import ตัวนี้ด้วย
import androidx.core.os.LocaleListCompat
import com.petcompanion.R // import ไฟล์ R ของโปรเจกต์คุณ
@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
    onClearDataClick: () -> Unit,
    onServerSetupClick: () -> Unit = {},
    isFirebaseConnected: Boolean = false,
    isDevProActive: Boolean = false,
    onDevProToggle: (Boolean) -> Unit = {},
    isPro: Boolean = false,
    onSubscriptionClick: () -> Unit = {}
) {
    val currentLocaleTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    var selectedLanguage by remember {
        mutableStateOf(if (currentLocaleTag.startsWith("en")) "English" else "Thai")
    }
    var showClearDataDialog by remember { mutableStateOf(false) }

    // 🎨 1. กำหนดถังสีเฉพาะหน้าสำหรับสลับธีม
    val currentBg = if (isDarkMode) Color(0xFF16181F) else Color(0xFFF7F9FC)
    val currentCard = if (isDarkMode) Color(0xFF22252D) else Color(0xFFFFFFFF)
    val currentText = if (isDarkMode) Color(0xFFFFFFFF) else Color(0xFF1A1C1E)
    val currentTextGray = if (isDarkMode) Color(0xFF8A91A4) else Color(0xFF64748B)
    val primaryBlue = Color(0xFF00A8E8)
    val redAccent = Color(0xFFE57373)

    Scaffold(
        containerColor = currentBg, // 📍 ใช้สีตามธีม
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, null, tint = currentText)
                }
                Text("Settings", color = currentText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {

            // --- 🌓 หมวดหมู่ Appearance (ใหม่!) ---
            Text("Appearance", color = primaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = currentCard),
                shape = RoundedCornerShape(12.dp),
                border = if (!isDarkMode) BorderStroke(1.dp, currentTextGray.copy(alpha = 0.2f)) else null
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = if (isDarkMode) primaryBlue else Color(0xFFFBC02D) // สีเหลืองสว่างถ้าเป็นโหมดขาว
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Dark Mode", color = currentText)
                    }
                    // 🔘 สวิตช์เปิด-ปิด
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { onThemeToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = primaryBlue,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = currentTextGray.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- หมวดหมู่ General ---
            Text("General", color = primaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = currentCard),
                shape = RoundedCornerShape(12.dp),
                border = if (!isDarkMode) BorderStroke(1.dp, currentTextGray.copy(alpha = 0.2f)) else null
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Language", color = currentText)
                    Row {
                        Text(
                            text = "TH",
                            color = if(selectedLanguage == "Thai") primaryBlue else currentTextGray,
                            modifier = Modifier.clickable {
                                selectedLanguage = "Thai"
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("th"))
                            }
                        )
                        Text(" | ", color = currentTextGray)
                        Text(
                            text = "EN",
                            color = if(selectedLanguage == "English") primaryBlue else currentTextGray,
                            modifier = Modifier.clickable {
                                selectedLanguage = "English"
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- หมวดหมู่ สมัครสมาชิก (Teacher Pro) ---
            val goldColor = Color(0xFFF4A500)
            Text("สมาชิก Teacher Pro", color = goldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSubscriptionClick() },
                colors = CardDefaults.cardColors(containerColor = currentCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, goldColor.copy(alpha = if (isPro) 0.6f else 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = goldColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Teacher Pro", color = currentText, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isPro) "กำลังใช้งาน ✓" else "สมัครเพื่อปลดล็อกห้องเรียน + จัดการข้อสอบ",
                                color = if (isPro) Color(0xFF4CAF50) else currentTextGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = currentTextGray)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- หมวดหมู่ Server (Pro) ---
            Text("Pro — Classroom Server", color = primaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onServerSetupClick() },
                colors = CardDefaults.cardColors(containerColor = currentCard),
                shape = RoundedCornerShape(12.dp),
                border = if (!isDarkMode) BorderStroke(1.dp, currentTextGray.copy(alpha = 0.2f)) else null
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isFirebaseConnected)
                                androidx.compose.material.icons.Icons.Default.CloudDone
                            else
                                androidx.compose.material.icons.Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isFirebaseConnected) Color(0xFF4CAF50) else currentTextGray
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("เชื่อมต่อเซิร์ฟเวอร์", color = currentText)
                            Text(
                                text = if (isFirebaseConnected) "Connected ✓" else "Not connected",
                                color = if (isFirebaseConnected) Color(0xFF4CAF50) else currentTextGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = currentTextGray)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- หมวดหมู่ AI Settings ---
            Text("AI Integrations", color = primaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onAiSettingsClick() },
                colors = CardDefaults.cardColors(containerColor = currentCard),
                shape = RoundedCornerShape(12.dp),
                border = if (!isDarkMode) BorderStroke(1.dp, currentTextGray.copy(alpha = 0.2f)) else null
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = primaryBlue)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("AI Quiz Generator (Prompt)", color = currentText)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = currentTextGray)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- 🔧 DEV — ลบออกก่อน Release ---
            var devCode by remember { mutableStateOf("") }
            val devColor = Color(0xFFFF9800)
            Text("🔧 Developer", color = devColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = currentCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, devColor.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isDevProActive) "✅ Pro Active (Dev)" else "กรอกรหัสเพื่อเปิด Pro (เทส)",
                        color = if (isDevProActive) Color(0xFF4CAF50) else currentTextGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = devCode,
                        onValueChange = { input ->
                            devCode = input
                            if (input.uppercase() == "QPRO2025") {
                                onDevProToggle(true)
                                devCode = ""
                            }
                        },
                        label = { Text("Dev Code", color = currentTextGray) },
                        placeholder = { Text("กรอกรหัส...", color = currentTextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = currentText,
                            unfocusedTextColor = currentText,
                            focusedBorderColor = devColor,
                            unfocusedBorderColor = devColor.copy(0.4f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- 📍 หมวดหมู่ Data Management (Danger Zone) ---
            Text("Data Management", color = redAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showClearDataDialog = true },
                colors = CardDefaults.cardColors(containerColor = currentCard),
                shape = RoundedCornerShape(12.dp),
                border = if (!isDarkMode) BorderStroke(1.dp, redAccent.copy(alpha = 0.2f)) else null
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Clear Data", tint = redAccent)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Clear All App Data", color = redAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 📍 Alert Dialog (ปรับสีตามธีม)
        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                containerColor = currentCard,
                title = {
                    Text(text = "คำเตือน", color = redAccent, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        text = "คุณแน่ใจหรือไม่ว่าต้องการล้างข้อมูลทั้งหมด? ประวัติการสอบและรายวิชาที่นำเข้าทั้งหมดจะหายไปและไม่สามารถกู้คืนได้",
                        color = currentText
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearDataDialog = false
                            onClearDataClick()
                        }
                    ) {
                        Text("ล้างข้อมูล", color = redAccent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text("ยกเลิก", color = currentTextGray)
                    }
                }
            )
        }
    }
}