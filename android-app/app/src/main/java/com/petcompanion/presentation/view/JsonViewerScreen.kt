package com.petcompanion.presentation.view
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JsonViewerScreen(
    jsonContent: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // 📍 เครื่องมือเรียกระบบ "Save File" ของ Android (จะเด้งหน้าต่างให้ผู้ใช้เลือกโฟลเดอร์)
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                // ถ้าผู้ใช้เลือกที่เซฟแล้ว เราจะเขียนเนื้อหา JSON ลงไปในไฟล์นั้น
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray())
                }
                Toast.makeText(context, "บันทึกไฟล์ลงเครื่องสำเร็จ!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "บันทึกไม่สำเร็จ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("อ่านไฟล์ JSON", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // 📍 ปุ่ม Save มุมขวาบน
                    IconButton(onClick = {
                        // สั่งให้มือถือเปิดหน้าต่างเซฟไฟล์ โดยตั้งชื่อเริ่มต้นไว้ให้
                        saveFileLauncher.launch("Q-Growth_Exported.json")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Save", tint = Color(0xFF4CAF50))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // กรอบแสดงโค้ด
            Card(
                modifier = Modifier.fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                // ทำให้โค้ดเลื่อนดูได้
                SelectionContainer { // 📍 ครอบไว้เพื่อให้ผู้ใช้เอานิ้วคลุมดำก๊อปปี้ข้อความได้
                    Text(
                        text = jsonContent,
                        color = Color(0xFF00FF00), // สีเขียวแฮกเกอร์ เท่ๆ
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}