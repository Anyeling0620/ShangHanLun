package com.example.killquestion.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.killquestion.data.model.Question
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object CustomQuestionStorage {
    private const val PREF_NAME = "custom_questions_db"
    private const val KEY_QUESTIONS = "saved_questions"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    // 内存缓存，减少IO
    private val memoryCache = mutableListOf<Question>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val json = prefs.getString(KEY_QUESTIONS, null)
        if (json != null) {
            val type = object : TypeToken<List<Question>>() {}.type
            val list: List<Question> = gson.fromJson(json, type)
            memoryCache.clear()
            memoryCache.addAll(list)
        }
    }

    fun saveQuestions(newQuestions: List<Question>) {
        // 去重添加
        val existingIds = memoryCache.map { it.id }.toSet()
        val toAdd = newQuestions.filter { it.id !in existingIds }

        if (toAdd.isNotEmpty()) {
            memoryCache.addAll(toAdd)
            val json = gson.toJson(memoryCache)
            prefs.edit().putString(KEY_QUESTIONS, json).apply()
        }
    }

    // [新增] 删除指定章节的所有题目
    fun deleteQuestionsByChapter(chapterName: String) {
        // 过滤掉要删除的章节
        val filtered = memoryCache.filter { it.chapter != chapterName }

        // 更新内存
        memoryCache.clear()
        memoryCache.addAll(filtered)

        // 更新本地文件
        val json = gson.toJson(memoryCache)
        prefs.edit().putString(KEY_QUESTIONS, json).apply()
    }

    fun getAll(): List<Question> {
        return memoryCache
    }
}