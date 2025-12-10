package com.shuati.shanghanlun.data.remote

import android.content.Context
import android.content.SharedPreferences

object AiConfigManager {
    private const val PREF_AI = "quiz_ai_config"
    private lateinit var prefs: SharedPreferences

    private const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val DEFAULT_MODEL = "x-ai/grok-4.1-fast"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_AI, Context.MODE_PRIVATE)
    }

    // [修改] 支持存储多行 Key
    var apiKeys: String
        get() = prefs.getString("api_keys", "") ?: ""
        set(value) = prefs.edit().putString("api_keys", value).apply()

    // 获取 Key 列表
    fun getKeyList(): List<String> {
        return apiKeys.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString("model", value).apply()

    var baseUrl: String
        get() = prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString("base_url", value).apply()

    // [新增] 代理设置
    var enableProxy: Boolean
        get() = prefs.getBoolean("enable_proxy", false)
        set(value) = prefs.edit().putBoolean("enable_proxy", value).apply()

    var proxyHost: String
        get() = prefs.getString("proxy_host", "127.0.0.1") ?: "127.0.0.1"
        set(value) = prefs.edit().putString("proxy_host", value).apply()

    var proxyPort: String
        get() = prefs.getString("proxy_port", "7890") ?: "7890"
        set(value) = prefs.edit().putString("proxy_port", value).apply()

    fun isConfigured(): Boolean = getKeyList().isNotEmpty()
}