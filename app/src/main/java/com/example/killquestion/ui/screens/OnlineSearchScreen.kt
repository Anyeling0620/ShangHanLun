package com.example.killquestion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.killquestion.data.model.Question
import com.example.killquestion.data.remote.SimpleAiClient
import com.example.killquestion.data.repository.QuestionRepository
import com.example.killquestion.ui.components.BouncyButton
import com.example.killquestion.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun OnlineSearchScreen(
    onBack: () -> Unit,
    onQuizStart: (String, List<Question>) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var countStr by remember { mutableStateOf("10") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun startSearch() {
        if (keyword.isBlank()) return
        val count = countStr.toIntOrNull() ?: 10

        isLoading = true
        errorMsg = null

        scope.launch {
            val questions = SimpleAiClient.generateOnlineQuestions(keyword, count)
            if (questions.isNotEmpty()) {
                QuestionRepository.addDynamicQuestions(questions)
                isLoading = false
                onQuizStart("搜索: $keyword", questions)
            } else {
                isLoading = false
                errorMsg = "生成失败，请换个关键词试试"
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.White, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("联网智能刷题", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ZenDark)
            }
        },
        containerColor = ZenBackgroundStart
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = ZenGreenPrimary.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "输入考点，AI 为你出题",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ZenDark
            )
            Text(
                "实时生成 · 自动收录 · 无限题库",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            // --- 1. 关键词输入区域 ---
            Column(modifier = Modifier.fillMaxWidth()) {
                // 标题在框外
                Text("考点关键词", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("例如：太阳病、桂枝汤") }, // 提示语在框内
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZenGreenPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- 2. 数量输入区域 ---
            Column(modifier = Modifier.fillMaxWidth()) {
                // 标题在框外
                Text("生成数量", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = countStr,
                    onValueChange = { if (it.all { char -> char.isDigit() }) countStr = it },
                    placeholder = { Text("默认为 10") }, // 提示语
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZenGreenPrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "注：题目数量越多，AI 生成时间越长，请耐心等待。",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))
            if (errorMsg != null) {
                Text(errorMsg!!, color = ColorMistake, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))

            BouncyButton(
                onClick = { startSearch() },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (isLoading) Color.Gray else ZenGreenPrimary,
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("AI 正在出题中...", color = Color.White)
                        }
                    } else {
                        Text("开始生成题目", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}