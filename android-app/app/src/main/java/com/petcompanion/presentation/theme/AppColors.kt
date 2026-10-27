package com.petcompanion.presentation.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf


// 📍 1. นิยามสีคงที่ (Accent Colors) - สีพวกนี้จะคงเดิมทั้งในโหมดขาวและดำ
val PrimaryBlue = Color(0xFF00A8E8)
val GreenAccent = Color(0xFF4CAF50)
val OrangeAccent = Color(0xFFFFA726)
val PurpleAccent = Color(0xFFAB47BC)
val RedAccent = Color(0xFFE57373)

// 📍 2. สร้าง Data Class เพื่อเก็บโครงสร้างสีที่ต้อง "เปลี่ยนตามธีม"
data class QGrowthColors(
    val background: Color,      // เดิมคือ DarkBg
    val card: Color,            // เดิมคือ CardBg
    val textPrimary: Color,     // สีข้อความหลัก (ขาวในโหมดมืด / ดำในโหมดขาว)
    val textSecondary: Color,   // เดิมคือ TextGray
    val primary: Color = PrimaryBlue,
    val green: Color = GreenAccent,
    val orange: Color = OrangeAccent,
    val purple: Color = PurpleAccent,
    val red: Color = RedAccent
)

// 📍 3. กำหนดชุดสี Dark Mode (ใช้ค่าเดิมเป๊ะๆ ที่บอสส่งมา)
val DarkColorScheme = QGrowthColors(
   // background = Color(0xFF3399DA),
    background = Color(0xFF16181F),
    card = Color(0xFF22252D),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8A91A4)
)

// 📍 4. กำหนดชุดสี Light Mode (ขาวสะอาดตา แต่ยังคงโทนเดิม)
val LightColorScheme = QGrowthColors(
    background = Color(0xFFF7F9FC), // ขาวนวล
    card = Color(0xFFFFFFFF),       // ขาวจั๊วะ
    textPrimary = Color(0xFF1A1C1E), // ดำเกือบสนิท
    textSecondary = Color(0xFF64748B) // เทาเข้มขึ้นเพื่อให้เห็นชัดบนพื้นขาว
)

// 📍 5. สร้าง "ท่อส่งสี" (CompositionLocal)
val LocalAppColors = staticCompositionLocalOf { DarkColorScheme }

// 📍 6. วัตถุเทพ (Object) สำหรับเรียกใช้สีในทุกหน้าจอ
object QTheme {
    val colors: QGrowthColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}