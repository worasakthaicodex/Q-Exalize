package com.petcompanion.presentation.view

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.petcompanion.data.local.UserProfileDao
import com.petcompanion.data.local.copyImageToInternalStorage
import com.petcompanion.presentation.theme.PrimaryBlue
import com.petcompanion.presentation.theme.QTheme
import com.petcompanion.presentation.viewmodel.UserViewModel


@Composable
fun ProfileScreen(viewModel: UserViewModel, onBackClick: () -> Unit) {
            // 📍 1. ดึงข้อมูลจากฐานข้อมูลมาส่องไว้ (ใส่ไว้บนสุดของฟังก์ชัน)
            val profileState by viewModel.userProfile.collectAsState()
             val context = LocalContext.current
        // 📍 2. ตัวแปรสำหรับถือค่าในหน้าจอ (เปลี่ยนจาก "User Name" เป็น "" เพื่อรอโหลดจาก DB)
            var name by remember { mutableStateOf("") }
            var imageUri by remember { mutableStateOf<Uri?>(null) }

        // 📍 3. ตัวเชื่อมโยง: เมื่อ DB โหลดเสร็จ ให้เอาค่ามาใส่ในตัวแปรข้างบน
            LaunchedEffect(profileState) {
                profileState?.let {
                    name = it.name
                    imageUri = if (!it.profileImagePath.isNullOrEmpty()) {
                        // 📍 ปรับตรงนี้: แปลง Path ไฟล์ในเครื่องให้กลายเป็น Uri ที่ถูกต้อง (file://...)
                        Uri.fromFile(java.io.File(it.profileImagePath))
                    } else {
                        null
                    }
                }
            }

        // 📍 4. ตัวเลือกรูป (อันเดิมของคุณ เก็บไว้ต่อท้ายได้เลย)
            val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    imageUri = uri
                }
            }


    Scaffold(
        containerColor = QTheme.colors.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding() // 📍 พระเอกช่วยชีวิต! สั่งให้ดึงเนื้อหาลงมาหลบ Status Bar อัตโนมัติ
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, null, tint = QTheme.colors.textPrimary) }
                Text("My Profile", color = QTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(24.dp)) {
            // ส่วนรูปภาพ
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                if (imageUri != null) {
                    AsyncImage(model = imageUri, contentDescription = null, modifier = Modifier.size(100.dp).clip(CircleShape).background(QTheme.colors.card), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(QTheme.colors.card), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = QTheme.colors.textSecondary, modifier = Modifier.size(50.dp))
                    }
                }
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.align(Alignment.BottomEnd).size(32.dp).background(PrimaryBlue, CircleShape)
                ) { Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Personal Information", color = PrimaryBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedTextColor = QTheme.colors.textPrimary,
                    focusedTextColor = QTheme.colors.textPrimary,
                    unfocusedBorderColor = QTheme.colors.card,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedLabelColor = QTheme.colors.textSecondary,
                    focusedLabelColor = PrimaryBlue
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    var finalImagePath = profileState?.profileImagePath

                    // 📍 1. เช็คชัวร์ๆ ว่าเป็นรูปใหม่จาก Gallery ใช่ไหม (ถ้าใช่ มันต้องขึ้นต้นด้วย content://)
                    if (imageUri != null && imageUri.toString().startsWith("content://")) {

                        // 📍 2. เรียกคำสั่งดูดรูปลงเครื่อง
                        val savedLocalPath = copyImageToInternalStorage(context, imageUri!!)

                        if (savedLocalPath != null) {
                            finalImagePath = savedLocalPath // อัปเดต Path เป็นไฟล์ในเครื่อง
                            Toast.makeText(context, "ดูดรูปลงเครื่องสำเร็จ!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "❌ ดึงรูปไม่ได้ (ลองเลือกรูปอื่นดูครับ)", Toast.LENGTH_LONG).show()
                        }
                    }

                    // 📍 3. สั่งเซฟลง Room Database
                    viewModel.saveProfile(
                        newName = name,
                        newImageUri = finalImagePath
                    )

                    Toast.makeText(context, "บันทึกโปรไฟล์เรียบร้อย", Toast.LENGTH_SHORT).show()

                    // เด้งกลับหน้าหลัก
                    onBackClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Save Profile", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}