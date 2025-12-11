package com.shuati.shanghanlun.data.repository

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken
import com.shuati.shanghanlun.data.local.CustomQuestionStorage
import com.shuati.shanghanlun.data.local.MistakeManager
import com.shuati.shanghanlun.data.local.ProgressManager // 确保导入了 ProgressManager
import com.shuati.shanghanlun.data.model.Question
import com.shuati.shanghanlun.data.model.QuestionWrapper
import com.shuati.shanghanlun.util.ChineseStringComparator
import com.google.gson.Gson
import com.shuati.shanghanlun.config.AppConfig
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. [IO线程] 读取文件
                val assetName = AppConfig.ASSET_QUESTION_FILE
                val jsonString = context.assets.open(assetName).bufferedReader().use { it.readText() }

                // 2. [IO线程] 解析 JSON
                val loadedList = mutableListOf<Question>()
                if (jsonString.trim().startsWith("[")) {
                    val type = object : TypeToken<List<Question>>() {}.type
                    val list: List<Question>? = Gson().fromJson(jsonString, type)
                    if (list != null) loadedList.addAll(list)
                } else {
                    val wrapper = Gson().fromJson(jsonString, QuestionWrapper::class.java)
                    if (wrapper != null) loadedList.addAll(wrapper.data)
                }

                // 3. [IO线程] 提前准备好本地题库
                val customQuestions = CustomQuestionStorage.getAll()
                val finalAllQuestions = mutableListOf<Question>()
                finalAllQuestions.addAll(loadedList)
                finalAllQuestions.addAll(customQuestions)

                // 4. [IO线程 - 关键优化] 在后台就把最耗时的排序和分组做完！
                // 之前这些是在 refreshIndexes() 里主线程做的，现在我们提前做

                // 4.1 准备 Map
                val byCategory = finalAllQuestions.groupBy { it.category }
                val byChapter = finalAllQuestions.groupBy { it.chapter }
                val byId = finalAllQuestions.associateBy { it.id }.toMutableMap()

                // 4.2 准备排序后的 List (中文排序很耗时，务必在后台做)
                val comparator = com.shuati.shanghanlun.util.ChineseStringComparator() // 确保引用了你的比较器
                val catList = byCategory.keys.filter { !it.contains("B型") }.sortedWith(comparator)
                val chapList = byChapter.keys.sortedWith(comparator)

                // 5. [主线程] 只做最后一步赋值，瞬间完成
                withContext(Dispatchers.Main) {
                    if (finalAllQuestions.isNotEmpty()) {
                        // 更新内存中的总表
                        allQuestions.clear()
                        allQuestions.addAll(finalAllQuestions)

                        // 直接赋值预计算好的结果，不再调用耗时的 refreshIndexes()
                        questionsByCategory.clear()
                        questionsByCategory.putAll(byCategory)

                        questionsByChapter.clear()
                        questionsByChapter.putAll(byChapter)

                        questionsById = byId

                        // 更新 Compose 状态 (这步会触发 UI 刷新，但因为数据已准备好，会非常快)
                        categoryList = catList
                        chapterList = chapList

                        isLoaded = true
                        loadError = null
                        android.util.Log.d("QuizDebug", "✅ 加载完成，UI 已刷新")
                    } else {
                        loadError = "解析为空"
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadError = "加载失败: ${e.message}"
                }
            }
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