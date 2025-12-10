package com.shuati.shanghanlun.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shuati.shanghanlun.data.local.CustomQuestionStorage
import com.shuati.shanghanlun.data.local.MistakeManager
import com.shuati.shanghanlun.data.local.ProgressManager // 确保导入了 ProgressManager
import com.shuati.shanghanlun.data.model.Question
import com.shuati.shanghanlun.data.model.QuestionWrapper
import com.shuati.shanghanlun.util.ChineseStringComparator
import com.google.gson.Gson

object QuestionRepository {
    private var allQuestions = mutableListOf<Question>() // 改为 MutableList
    var isLoaded by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)

    private var questionsByCategory = mutableMapOf<String, List<Question>>()
    var categoryList by mutableStateOf(listOf<String>())

    private var questionsByChapter = mutableMapOf<String, List<Question>>()
    var chapterList by mutableStateOf(listOf<String>())

    private var questionsById = mutableMapOf<String, Question>()

    fun load(context: Context) {
        if (isLoaded) return

        try {
            // [修改] 显式声明变量类型，确保不被错误推断
            val jsonString: String = context.assets.open("questions_full.json").bufferedReader().use {
                it.readText()
            }

            // [修改] 显式声明 wrapper 类型
            val wrapper: QuestionWrapper? = Gson().fromJson(jsonString, QuestionWrapper::class.java)

            // [安全检查] 避免 wrapper 为空导致空指针
            if (wrapper != null) {
                val staticQuestions: List<Question> = wrapper.data

                // 2. 加载本地存储的动态题库
                val customQuestions: List<Question> = CustomQuestionStorage.getAll()

                // 3. 合并
                allQuestions.clear()
                allQuestions.addAll(staticQuestions)
                allQuestions.addAll(customQuestions)

                // 4. 重建索引
                refreshIndexes()

                isLoaded = true
            } else {
                loadError = "题库解析结果为空"
            }
        } catch (e: Exception) { // 捕获所有异常
            e.printStackTrace()
            loadError = "数据解析错误: ${e.message}"
        }
    }

    // 当联网生成新题时，调用此方法更新内存
    fun addDynamicQuestions(newQuestions: List<Question>) {
        CustomQuestionStorage.saveQuestions(newQuestions) // 持久化保存
        allQuestions.addAll(newQuestions) // 添加到内存列表
        refreshIndexes() // 刷新索引
    }

    private fun refreshIndexes() {
        val groupedCat = allQuestions.groupBy { it.category }
        questionsByCategory.clear() // 清理旧数据
        questionsByCategory.putAll(groupedCat)
        categoryList = groupedCat.keys.filter { !it.contains("B型") }.sortedWith(
            ChineseStringComparator()
        )

        val groupedChap = allQuestions.groupBy { it.chapter }
        questionsByChapter.clear() // 清理旧数据
        questionsByChapter.putAll(groupedChap)
        chapterList = groupedChap.keys.sortedWith(ChineseStringComparator())

        questionsById = allQuestions.associateBy { it.id }.toMutableMap()
    }

    fun getQuestionsByCategory(category: String) = questionsByCategory[category] ?: emptyList()
    fun getQuestionsByChapter(chapter: String) = questionsByChapter[chapter] ?: emptyList()

    // [核心修复] 获取错题列表
    // 增加 filter 逻辑：如果 MistakeManager 里有 ID，但 allQuestions 里找不到这题（说明已被删除），则过滤掉
    fun getMistakeQuestions(): List<Question> {
        val mistakeIds = MistakeManager.getAllMistakeIds()
        return mistakeIds.mapNotNull { questionsById[it] }
    }

    fun getTotalCount(): Int = allQuestions.size

    // [核心修复] 删除章节并联动清理数据
    fun deleteChapter(chapterName: String) {
        // 1. 找出该章节下的所有题目
        val questionsToDelete = allQuestions.filter { it.chapter == chapterName }

        if (questionsToDelete.isEmpty()) return

        // 2. 提取这些题目的 ID
        val idsToDelete = questionsToDelete.map { it.id }

        // 3. [关键步骤] 通知 Managers 清理数据
        // 这会让首页的错题数立即下降，进度记录也会被清除
        MistakeManager.removeMistakes(idsToDelete)
        ProgressManager.removeRecords(idsToDelete)

        // 4. 从持久化存储中删除
        CustomQuestionStorage.deleteQuestionsByChapter(chapterName)

        // 5. 从内存列表中删除
        allQuestions.removeAll(questionsToDelete)

        // 6. 刷新索引 (Category 和 Chapter 映射)
        refreshIndexes()
    }
}