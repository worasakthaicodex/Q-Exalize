package com.petcompanion.presentation.view
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun AiPromptScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    val currentLanguage = context.resources.configuration.locales[0].language
    val isEnglish = currentLanguage == "en"

    val screenTitle = if (isEnglish) "AI Quiz Prompt" else "คัดลอกคำสั่ง AI"
    val instructionText = if (isEnglish) {
        "Copy the prompt below and paste it into an AI (like ChatGPT or Gemini)."
    } else {
        "คัดลอกข้อความด้านล่างนี้ไปวางใน AI (เช่น ChatGPT, Gemini)"
    }
    val warningText = if (isEnglish) {
        "⚠️ IMPORTANT: Do not forget to ATTACH YOUR PDF FILE in the AI chat!"
    } else {
        "⚠️ สำคัญมาก: อย่าลืม 'แนบไฟล์ PDF' เข้าไปในแชท AI ด้วยนะครับ!"
    }

    val promptThai = """
        คุณคือผู้เชี่ยวชาญด้านการออกข้อสอบ (Quiz Generator)
        กรุณาอ่านเนื้อหาจากไฟล์ PDF ที่แนบมานี้ แล้วสร้างชุดข้อสอบจำนวน 30 ข้อ (นับเป็น 1 บท)
        โดยให้ผลลัพธ์ออกมาเป็นรูปแบบ JSON เท่านั้น ห้ามมีข้อความอื่นปะปน
        เเล้วทำลิงค์ให้ผู้ใช้โหลดไฟล์ JSON ได้เลย
        โครงสร้างความยากของข้อสอบให้แบ่งคละกัน ดังนี้:

        1. ระดับง่าย (Easy): 
        - ถามตรงประเด็นตามเนื้อหา
        - ตัวเลือก 5 ข้อ: มีคำตอบที่ถูกต้อง 1 ข้อ, คำตอบที่คล้ายกันเพื่อหลอก 1 ข้อ, และอีก 3 ข้อเป็นคำตอบที่เกี่ยวข้องแต่แยกแยะได้ง่าย

        2. ระดับปานกลาง (Medium):
        - ถามตรงประเด็น แต่ต้องใช้ความเข้าใจ
        - ตัวเลือก 5 ข้อ: มีคำตอบที่ถูกต้อง 1 ข้อ, คำตอบที่หลอกแนบเนียน 1 ข้อ, และอีก 3 ข้อให้นำเนื้อหาจาก "เรื่องอื่นหรือบทอื่นในหนังสือเล่มนี้" มาเป็นตัวลวง

        3. ระดับยาก (Hard):
        - เน้นการวิเคราะห์ ถามจุดเด่น จุดด้อย หรือการประยุกต์ใช้
        - ตัวเลือก 5 ข้อ: ทุกตัวเลือกต้องดูเหมือนถูกและมีความเกี่ยวข้องกันทั้งหมด แต่ต้องมีเพียง 1 ตัวเลือกที่เป็น "คำตอบที่ถูกต้องที่สุดและครอบคลุมที่สุด"

        รูปแบบ JSON ที่ต้องการ (กรุณาทำตามให้เป๊ะเพื่อให้แอปรองรับได้):
        {
          "courseCode": "รหัสวิชา (อ้างอิงจาก PDF)",
          "title": "ชื่อวิชา",
          "description": "คำอธิบายเนื้อหาโดยย่อ",
          "timeLimit": 30,
          "passingScore": 15,
          "questions": [
            {
              "chapter": "ชื่อบท (เช่น บทที่ 1: พื้นฐาน)",
              "text": "โจทย์ข้อสอบ?",
              "options": [
                "ตัวเลือก A",
                "ตัวเลือก B",
                "ตัวเลือก C",
                "ตัวเลือก D",
                "ตัวเลือก E"
              ],
              "correctIndex": 0,
              "closeIndex" : null,
              "explanation": "คำอธิบายเฉลยอย่างละเอียด"
            }
          ]
        }
    """.trimIndent()

    val promptEnglish = """
        You are an expert Quiz Generator.
        Please read the attached PDF file and create a 30-question quiz (considered as 1 chapter).
        The output MUST be strictly in JSON format. Do not include any other text.
        Please provide a direct download link for the JSON file.
        The difficulty level of the questions must be mixed as follows:

        1. Easy:
        - Direct questions based on the content.
        - 5 Options: 1 correct answer, 1 similar decoy answer, and 3 easily distinguishable related answers.

        2. Medium:
        - Direct questions but require comprehension.
        - 5 Options: 1 correct answer, 1 subtle decoy, and 3 decoy options pulled from "other topics or chapters in this book".

        3. Hard:
        - Focus on analysis, strengths/weaknesses, or application.
        - 5 Options: All options must seem correct and related, but only 1 is the "most correct and comprehensive" answer.

        Required JSON Format (Strictly follow this for app compatibility):
        {
          "courseCode": "Course Code (from PDF)",
          "title": "Course Title",
          "description": "Brief description",
          "timeLimit": 30,
          "passingScore": 15,
          "questions": [
            {
              "chapter": "Chapter Name",
              "text": "Question text?",
              "options": [
                "Option A",
                "Option B",
                "Option C",
                "Option D",
                "Option E"
              ],
              "correctIndex": 0,
              "closeIndex" : null,
              "explanation": "Detailed explanation for the answer"
            }
          ]
        }
    """.trimIndent()

    val finalPromptText = if (isEnglish) promptEnglish else promptThai

    Scaffold(
        containerColor = Color(0xFF121212), // เปลี่ยนเป็น DarkBg ของบอสได้เลยครับ
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Text(screenTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(text = instructionText, color = Color.LightGray, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1010))
            ) {
                Text(
                    text = warningText,
                    color = Color(0xFFFF8A80),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), // เปลี่ยนเป็น CardBg ของบอสได้
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(
                        text = finalPromptText,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 📍 แถวที่ 1: ปุ่มคัดลอกแล้วเปิดแอปทันที (Quick Actions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(finalPromptText))
                        Toast.makeText(context, if(isEnglish) "Copied! Paste & attach PDF" else "คัดลอกแล้ว! อย่าลืมแนบ PDF ด้วยนะ", Toast.LENGTH_LONG).show()
                        try {
                            uriHandler.openUri("https://chatgpt.com/")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error opening browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ChatGPT", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(finalPromptText))
                        Toast.makeText(context, if(isEnglish) "Copied! Paste & attach PDF" else "คัดลอกแล้ว! อย่าลืมแนบ PDF ด้วยนะ", Toast.LENGTH_LONG).show()
                        try {
                            uriHandler.openUri("https://gemini.google.com/")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error opening browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gemini", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 📍 แถวที่ 2: ปุ่มคัดลอกแบบธรรมดา (Fallback Plan)
            OutlinedButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(finalPromptText))
                    Toast.makeText(context, if(isEnglish) "Prompt copied!" else "คัดลอกคำสั่งเรียบร้อย!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEnglish) "Copy Prompt Only" else "คัดลอกคำสั่งเท่านั้น", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}