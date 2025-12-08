package com.example.killquestion.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.killquestion.data.local.CustomQuestionStorage
import com.example.killquestion.data.local.MistakeManager
import com.example.killquestion.data.model.Question
import com.example.killquestion.data.model.QuestionWrapper
import com.example.killquestion.util.ChineseStringComparator
import com.google.gson.Gson
import java.io.IOException
import com.example.killquestion.data.local.ProgressManager // 导入新建的管理器

object QuestionRepository {
    private var allQuestions = mutableListOf<Question>() // 改为 MutableList
    var isLoaded by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)

    // ... categoryList, chapterList 保持不变 ...
    private var questionsByCategory = mutableMapOf<String, List<Question>>()
    var categoryList by mutableStateOf(listOf<String>())

    private var questionsByChapter = mutableMapOf<String, List<Question>>()
    var chapterList by mutableStateOf(listOf<String>())

    private var questionsById = mutableMapOf<String, Question>()

    fun load(context: Context) {
        if (isLoaded) return

        try {
            // 1. 加载 assets 里的静态题库
            val jsonString = context.assets.open("questions_full.json").bufferedReader().use { it.readText() }
            val wrapper = Gson().fromJson(jsonString, QuestionWrapper::class.java)
            val staticQuestions = wrapper.data

            // 2. 加载本地存储的动态题库 (联网搜的题)
            val customQuestions = CustomQuestionStorage.getAll()

            // 3. 合并
            allQuestions.clear()
            allQuestions.addAll(staticQuestions)
            allQuestions.addAll(customQuestions)

            // 4. 重建索引
            refreshIndexes()

            isLoaded = true
        } catch (e: IOException) {
            loadError = "未找到题库文件 (questions_full.json)。"
        } catch (e: Exception) {
            loadError = "数据解析错误: ${e.message}"
        }
    }

/**
 * Retrieves a list of questions that have been marked as mistakes.
 * This function fetches all mistake IDs from the MistakeManager and then maps
 * each ID to its corresponding question from the questionsById map.
 *
 * @return A List of Question objects that have been previously marked as mistakes.
 *         If any ID in mistakeIds does not have a corresponding question in questionsById,
 *         it will be filtered out (due to use of mapNotNull).
 */
    // [新增]：当联网生成新题时，调用此方法更新内存
    // Get all mistake IDs from MistakeManager
    fun addDynamicQuestions(newQuestions: List<Question>) {
    // Map each ID to its corresponding question, filtering out any null results
        CustomQuestionStorage.saveQuestions(newQuestions) // 持久化保存
        allQuestions.addAll(newQuestions) // 添加到内存列表
        refreshIndexes() // 刷新索引，这样 getMistakeQuestions 才能找到它们
    }

    private fun refreshIndexes() {
        val groupedCat = allQuestions.groupBy { it.category }
        questionsByCategory.putAll(groupedCat)
        categoryList = groupedCat.keys.filter { !it.contains("B型") }.sortedWith(ChineseStringComparator())

        val groupedChap = allQuestions.groupBy { it.chapter }
        questionsByChapter.putAll(groupedChap)
        chapterList = groupedChap.keys.sortedWith(ChineseStringComparator())

        questionsById = allQuestions.associateBy { it.id }.toMutableMap()
    }

    fun getQuestionsByCategory(category: String) = questionsByCategory[category] ?: emptyList()
    fun getQuestionsByChapter(chapter: String) = questionsByChapter[chapter] ?: emptyList()

    fun getMistakeQuestions(): List<Question> {
        val mistakeIds = MistakeManager.getAllMistakeIds()
        return mistakeIds.mapNotNull { questionsById[it] }
    }

    fun getTotalCount(): Int = allQuestions.size

    // [新增] 删除章节并刷新
    fun deleteChapter(chapterName: String) {
        // 1. 从持久化存储中删除
        CustomQuestionStorage.deleteQuestionsByChapter(chapterName)

        // 2. 从内存列表中删除
        allQuestions.removeAll { it.chapter == chapterName }

        // 3. 刷新索引 (Category 和 Chapter 映射)
        refreshIndexes()
    }
}