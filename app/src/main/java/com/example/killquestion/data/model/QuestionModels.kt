package com.example.killquestion.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.gson.annotations.SerializedName

// [核心修改] 给所有字段加上 = "" 或 = 0 的默认值
// 这样编译后会生成无参构造函数，防止 R8 混淆导致 Gson 无法初始化
data class QuestionWrapper(
    @SerializedName("version") val version: String = "1.0",
    @SerializedName("source") val source: String = "",
    @SerializedName("total_count") val total_count: Int = 0,
    @SerializedName("data") val data: List<Question> = emptyList()
)

data class Option(
    @SerializedName("label") val label: String = "",
    @SerializedName("text") val text: String = ""
)

data class Question(
    @SerializedName("id") val id: String = "",
    @SerializedName("number") val number: Int = 0,
    @SerializedName("chapter") val chapter: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("type") val type: String = "ESSAY",
    @SerializedName("content") val content: String = "",
    @SerializedName("options") val options: List<Option> = emptyList(),
    @SerializedName("answer") val answer: String = "",
    @SerializedName("analysis") val analysis: String = ""
) {
    // ... 下面的逻辑方法保持不变 ...
    fun getUiType(): QuestionType {
        return try {
            QuestionType.valueOf(type)
        } catch (e: Exception) {
            QuestionType.ESSAY
        }
    }

    // ... getRealAnswer 和 getFullExplanation 方法保持不变 ...
    fun getRealAnswer(): String {
        // ... 原有逻辑 ...
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
        // ... 原有逻辑 ...
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
// ... Enum 和 QuizContext 保持不变 ...
enum class QuestionType(val icon: ImageVector) {
    SINGLE_CHOICE(Icons.Default.CheckCircle),
    MULTI_CHOICE(Icons.AutoMirrored.Filled.List),
    TRUE_FALSE(Icons.Default.ThumbUp),
    FILL_BLANK(Icons.Default.Edit),
    ESSAY(Icons.Default.Face)
}

data class QuizContext(
    val title: String = "",
    val questions: List<Question> = emptyList(),
    val isMistakeMode: Boolean = false,
    val sourceScreen: String = "START"
)