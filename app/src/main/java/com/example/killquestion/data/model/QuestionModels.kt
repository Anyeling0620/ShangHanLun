package com.example.killquestion.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector

data class QuestionWrapper(
    val version: String,
    val source: String,
    val total_count: Int,
    val data: List<Question>
)

data class Option(
    val label: String,
    val text: String
)

data class Question(
    val id: String,
    val number: Int,
    val chapter: String,
    val category: String,
    val type: String,
    val content: String,
    val options: List<Option> = emptyList(),
    val answer: String,
    val analysis: String
) {
    fun getUiType(): QuestionType {
        return try {
            QuestionType.valueOf(type)
        } catch (e: Exception) {
            QuestionType.ESSAY
        }
    }

    fun getRealAnswer(): String {
        val typeEnum = getUiType()
        if (typeEnum == QuestionType.TRUE_FALSE) {
            if (answer.contains("正确") && !answer.contains("不正确")) return "A"
            if (answer.contains("对") && !answer.contains("不对")) return "A"
            if (answer.contains("不正确") || answer.contains("错误") || answer.contains("错")) return "B"
            val clean = answer.trim().uppercase()
            if (clean == "A" || clean == "T" || clean == "TRUE") return "A"
            if (clean == "B" || clean == "F" || clean == "FALSE") return "B"
            return "A"
        }
        val cleaned = answer.uppercase().filter { it in 'A'..'E' }
        return if (cleaned.isNotEmpty()) cleaned else answer
    }

    fun getFullExplanation(): String {
        if (getUiType() == QuestionType.TRUE_FALSE) {
            var cleanAns = answer.replace(Regex("答案|参考答案|正确答案|：|:"), "")
            cleanAns = cleanAns.replace(Regex("不正确|正确|错误|对|错|A|B|T|F|TRUE|FALSE", RegexOption.IGNORE_CASE), "")
            cleanAns = cleanAns.trim().trimStart('。', '，', '.', ',')
            val parts = listOf(cleanAns, analysis).filter { it.isNotBlank() }
            return if (parts.isEmpty()) "暂无详细解析" else parts.joinToString("\n\n")
        }
        return analysis.ifBlank { "暂无详细解析" }
    }
}

enum class QuestionType(val icon: ImageVector) {
    SINGLE_CHOICE(Icons.Default.CheckCircle),
    MULTI_CHOICE(Icons.AutoMirrored.Filled.List),
    TRUE_FALSE(Icons.Default.ThumbUp),
    FILL_BLANK(Icons.Default.Edit),
    ESSAY(Icons.Default.Face)
}

data class QuizContext(
    val title: String,
    val questions: List<Question>,
    val isMistakeMode: Boolean = false,
    val sourceScreen: String = "START"
)