package com.petcompanion.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.presentation.theme.*

// ─────────────────────────────────────────────────────────────────────────────
// QuizManageScreen — โต๊ะบริการจัดการข้อสอบ
// คลังข้อสอบ · สร้างเอง · นำเข้าไฟล์ · รับโค้ด
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizManageScreen(
    onBackClick: () -> Unit,
    onLibraryClick: () -> Unit,     // คลังข้อสอบ (Category)
    onManualClick: () -> Unit,      // สร้างข้อสอบเอง
    onImportClick: () -> Unit,      // นำเข้าจากไฟล์
    onReceiveCodeClick: () -> Unit  // รับข้อสอบจากโค้ด
) {
    val colors = QTheme.colors
    Scaffold(
        containerColor = colors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("จัดการข้อสอบ", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = colors.card)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "เลือกบริการที่ต้องการ",
                color = colors.textSecondary, fontSize = 14.sp
            )

            ServiceCard(
                icon       = Icons.Default.LibraryBooks,
                iconColor  = PrimaryBlue,
                title      = "คลังข้อสอบของฉัน",
                subtitle   = "ดู / แก้ไข / แชร์ ข้อสอบที่มีอยู่",
                onClick    = onLibraryClick
            )
            ServiceCard(
                icon       = Icons.Default.EditNote,
                iconColor  = Color(0xFF9B59B6),
                title      = "สร้างข้อสอบเอง",
                subtitle   = "พิมพ์คำถาม-ตัวเลือกทีละข้อ",
                onClick    = onManualClick
            )
            ServiceCard(
                icon       = Icons.Default.UploadFile,
                iconColor  = GreenAccent,
                title      = "นำเข้าข้อสอบ",
                subtitle   = "นำเข้าไฟล์ JSON (วิชาใหม่ / วิชาเดิม)",
                onClick    = onImportClick
            )
            ServiceCard(
                icon       = Icons.Default.VpnKey,
                iconColor  = OrangeAccent,
                title      = "รับข้อสอบจากโค้ด",
                subtitle   = "กรอกโค้ด 6 ตัวที่ได้รับจากผู้แชร์",
                onClick    = onReceiveCodeClick
            )
        }
    }
}

@Composable
private fun ServiceCard(
    icon: ImageVector, iconColor: Color,
    title: String, subtitle: String,
    onClick: () -> Unit
) {
    val colors = QTheme.colors
    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = colors.card)
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(iconColor.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        }
    }
}
