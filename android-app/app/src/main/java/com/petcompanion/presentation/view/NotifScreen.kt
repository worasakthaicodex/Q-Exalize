package com.petcompanion.presentation.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.petcompanion.notification.InAppNotification
import com.petcompanion.notification.NotificationStore
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.theme.QTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotifScreen(onBackClick: () -> Unit) {
    val context       = LocalContext.current
    val notifications by NotificationStore.notifications.collectAsState()

    // ทำเครื่องหมายอ่านทั้งหมดเมื่อเปิดหน้า
    LaunchedEffect(Unit) {
        NotificationStore.markAllRead(context)
    }

    val timeFmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("th", "TH")) }

    Scaffold(
        containerColor = QTheme.colors.background,
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
                    Text(
                        stringResource(R.string.title_notifications),
                        color = QTheme.colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (notifications.isNotEmpty()) {
                        Text(
                            "${notifications.size} รายการ",
                            color = QTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                // ปุ่มลบทั้งหมด
                if (notifications.isNotEmpty()) {
                    IconButton(onClick = { NotificationStore.clear(context) }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "ลบทั้งหมด",
                            tint = QTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        null,
                        tint = QTheme.colors.textSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        stringResource(R.string.empty_notifications),
                        color = QTheme.colors.textSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "การแจ้งเตือนจากห้องเรียน\nจะขึ้นที่นี่โดยอัตโนมัติ",
                        color = QTheme.colors.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { notif ->
                    NotifCard(notif = notif, timeFmt = timeFmt)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NotifCard(notif: InAppNotification, timeFmt: SimpleDateFormat) {
    val typeColor = when (notif.type) {
        InAppNotification.TYPE_HW_SUBMITTED   -> Color(0xFF1565C0)  // น้ำเงิน
        InAppNotification.TYPE_HW_GRADED      -> Color(0xFF2E7D32)  // เขียว
        InAppNotification.TYPE_ATT_OPENED     -> Color(0xFF388E3C)  // เขียวอ่อน
        InAppNotification.TYPE_ATT_CHECKED    -> Color(0xFF00838F)  // เขียวฟ้า
        InAppNotification.TYPE_ROOM_APPROVED  -> Color(0xFFF57F17)  // ส้มทอง
        InAppNotification.TYPE_EXAM_PUBLISHED -> Color(0xFF6A1B9A)  // ม่วงเข้ม
        else                                  -> PrimaryBlue
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (!notif.isRead)
                typeColor.copy(alpha = 0.08f)
            else
                QTheme.colors.card
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Icon วงกลม
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(notif.emoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        notif.title,
                        color = QTheme.colors.textPrimary,
                        fontWeight = if (!notif.isRead) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    // จุดแดง = ยังไม่ได้อ่าน
                    if (!notif.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF5350))
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    notif.body,
                    color = QTheme.colors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    timeFmt.format(Date(notif.createdAt)),
                    color = QTheme.colors.textSecondary.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
