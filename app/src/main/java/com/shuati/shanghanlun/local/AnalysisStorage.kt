package com.shuati.shanghanlun.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AnalysisStorage {
    private const val PREF_NAME = "analysis_storage"
    private const val KEY_CARDS = "weakness_cards"
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // 保存卡片数据
    fun saveCards(cards: List<Pair<String, String>>) {
        val json = gson.toJson(cards)
        prefs.edit().putString(KEY_CARDS, json).apply()
    }

    // 读取卡片数据
    fun getCards(): List<Pair<String, String>> {
        val json = prefs.getString(KEY_CARDS, null) ?: return emptyList()
        val type = object : TypeToken<List<Pair<String, String>>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 检查是否有缓存
    fun hasData(): Boolean = prefs.contains(KEY_CARDS)
}