package com.petcompanion.presentation.view

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.data.local.BillingManager
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.QuizViewModel

private val SubGold = Color(0xFFF4A500)

@Composable
fun TeacherSubscriptionScreen(
    viewModel: QuizViewModel,
    onBackClick: () -> Unit,
    onSubscribed: () -> Unit = {}
) {
    val colors         = LocalAppColors.current
    val context        = LocalContext.current
    val activity       = context as? Activity
    val isPremium      by viewModel.isPremiumUser.collectAsState()
    val productDetails by viewModel.productDetails.collectAsState()

    // ถ้า subscribe แล้ว → กลับ
    LaunchedEffect(isPremium) {
        if (isPremium) onSubscribed()
    }

    val monthlyPrice = viewModel.getFormattedPrice(BillingManager.PRODUCT_MONTHLY)
        .ifBlank { "฿99" }
    val yearlyPrice  = viewModel.getFormattedPrice(BillingManager.PRODUCT_YEARLY)
        .ifBlank { "฿799" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            Text("Teacher Pro", color = colors.textPrimary,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Hero ──────────────────────────────────────────────────────
            Icon(Icons.Default.School, null,
                tint = SubGold, modifier = Modifier.size(64.dp))
            Text(
                "ครู Pro",
                color = SubGold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold
            )
            Text(
                "ปลดล็อคทุกฟีเจอร์ห้องเรียน\nจัดการได้ไม่จำกัด",
                color = colors.textSecondary, fontSize = 14.sp,
                textAlign = TextAlign.Center, lineHeight = 20.sp
            )

            // ── Features ──────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FeatureRow(Icons.Default.MeetingRoom,  "สร้างห้องเรียนได้ไม่จำกัด")
                    FeatureRow(Icons.Default.Quiz,         "ออกข้อสอบออนไลน์ Real-time")
                    FeatureRow(Icons.Default.People,       "จัดการนักเรียนไม่จำกัดคน")
                    FeatureRow(Icons.Default.Assignment,   "ระบบการบ้าน + เช็คชื่อ")
                    FeatureRow(Icons.Default.Leaderboard,  "Arena Leaderboard")
                    FeatureRow(Icons.Default.FolderOpen,   "อัพโหลดไฟล์ PDF ให้นักเรียน")
                    FeatureRow(Icons.Default.Share,        "แชร์ข้อสอบแบบล็อก (ขายได้)")
                }
            }

            // ── Plans ──────────────────────────────────────────────────────
            Text("เลือกแผน", color = colors.textPrimary,
                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth())

            // รายเดือน
            PlanCard(
                title      = "รายเดือน",
                price      = monthlyPrice,
                perUnit    = "/ เดือน",
                highlight  = false,
                badge      = null,
                onClick    = { activity?.let { viewModel.launchBillingFlow(it, BillingManager.PRODUCT_MONTHLY) } }
            )

            // รายปี (แนะนำ)
            PlanCard(
                title      = "รายปี",
                price      = yearlyPrice,
                perUnit    = "/ ปี",
                highlight  = true,
                badge      = "ประหยัด ~30%",
                onClick    = { activity?.let { viewModel.launchBillingFlow(it, BillingManager.PRODUCT_YEARLY) } }
            )

            Text(
                "ยกเลิกได้ทุกเมื่อ • ชำระผ่าน Google Play\nต่ออายุอัตโนมัติ จนกว่าจะยกเลิก",
                color = colors.textSecondary, fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = GreenAccent, modifier = Modifier.size(20.dp))
        Text(text, color = LocalAppColors.current.textPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun PlanCard(
    title: String, price: String, perUnit: String,
    highlight: Boolean, badge: String?, onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val borderColor = if (highlight) SubGold else colors.textSecondary.copy(0.3f)
    val bgColor     = if (highlight) SubGold.copy(0.08f) else colors.card

    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().border(
            width = if (highlight) 2.dp else 1.dp,
            color = borderColor, shape = RoundedCornerShape(14.dp)
        ),
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, color = colors.textPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SubGold
                        ) {
                            Text(badge, color = Color.White,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(price, color = if (highlight) SubGold else PrimaryBlue,
                    fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(perUnit, color = colors.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}
