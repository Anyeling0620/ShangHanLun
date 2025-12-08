package com.example.killquestion.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ProgressManager {
    private const val PREF_PROGRESS = "quiz_progress_v3"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_PROGRESS, Context.MODE_PRIVATE)
    }

    fun markCompleted(questionId: String) {
        prefs.edit().putBoolean(questionId, true).apply()
    }

    fun isCompleted(questionId: String): Boolean {
        return prefs.getBoolean(questionId, false)
    }
}

object MistakeManager {
    private const val PREF_MISTAKES = "quiz_mistakes_v3"
    private lateinit var prefs: SharedPreferences
    var mistakeChangeTrigger by mutableStateOf(0L)

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_MISTAKES, Context.MODE_PRIVATE)
    }

    fun addMistake(questionId: String) {
        prefs.edit().putBoolean(questionId, true).apply()
        mistakeChangeTrigger = System.currentTimeMillis()
    }

    fun removeMistake(questionId: String) {
        if (isMistake(questionId)) {
            prefs.edit().remove(questionId).apply()
            mistakeChangeTrigger = System.currentTimeMillis()
        }
    }

    fun toggleMistake(questionId: String) {
        if (isMistake(questionId)) {
            removeMistake(questionId)
        } else {
            addMistake(questionId)
        }
    }

    fun isMistake(questionId: String): Boolean {
        return prefs.contains(questionId)
    }

    fun getAllMistakeIds(): Set<String> {
        return prefs.all.keys
    }
}