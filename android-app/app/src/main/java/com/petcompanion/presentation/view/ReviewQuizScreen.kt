package com.petcompanion.presentation.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petcompanion.domain.model.QuestionEntity
import com.petcompanion.presentation.theme.*
import com.petcompanion.presentation.viewmodel.QuizViewModel

import androidx.compose.ui.res.stringResource
import com.petcompanion.R
@Composable
fun ReviewQuizScreen(
    courseId: Int,
    viewModel: QuizViewModel,
    userAnswers: Map<Int, Int>,
    onBackClick: () -> Unit
) {

    LaunchedEffect(courseId) {
        viewModel.loadQuestionsForReview(courseId)
    }

    val questions by viewModel.reviewQuestions.collectAsState()

    Scaffold(
        containerColor = QTheme.colors.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(id = R.string.btn_back),
                        tint = QTheme.colors.textPrimary
                    )
                }
                Text(
                    text = stringResource(id = R.string.title_review_quiz),
                    color = QTheme.colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(questions) { index, question ->
                ReviewQuestionCard(
                    index = index + 1,
                    question = question,
                    userAnswer = userAnswers[question.qid]
                )
            }
        }
    }
}

@Composable
fun ReviewQuestionCard(index: Int, question: QuestionEntity, userAnswer: Int?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = QTheme.colors.card)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // หัวข้อ: ข้อที่ %d + บทที่
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(id = R.string.label_question_number, index),
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("• ${question.chapter}", color = QTheme.colors.textSecondary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(question.text, color = QTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(16.dp))

            // รายการตัวเลือก
            question.options.forEachIndexed { optIndex, optionText ->
                val isCorrect = optIndex == question.correctIndex
                val isSelected = optIndex == userAnswer

                val borderColor = when {
                    isCorrect -> GreenAccent
                    isSelected && !isCorrect -> RedAccent
                    else -> Color.Transparent
                }

                val bgColor = when {
                    isCorrect -> GreenAccent.copy(alpha = 0.1f)
                    isSelected && !isCorrect -> RedAccent.copy(alpha = 0.1f)
                    else -> QTheme.colors.background.copy(alpha = 0.5f)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor,
                    border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = optionText,
                            modifier = Modifier.weight(1f),
                            color = if (isCorrect) GreenAccent else if (isSelected) RedAccent else QTheme.colors.textPrimary
                        )

                        if (isCorrect) Icon(Icons.Default.CheckCircle, null, tint = GreenAccent)
                        if (isSelected && !isCorrect) Icon(Icons.Default.Cancel, null, tint = RedAccent)
                    }
                }
            }

            // 📍 ส่วนคำอธิบาย (Explanation)
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue.copy(0.05f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.label_explanation),
                        color = PrimaryBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(question.explanation, color = QTheme.colors.textPrimary.copy(0.8f), fontSize = 14.sp)

                // ถ้าตอบข้อเกือบถูก (Close Index) ให้โชว์คำแนะนำพิเศษ
                if (userAnswer == question.closeIndex && userAnswer != question.correctIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.hint_close_answer),
                        color = OrangeAccent,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}