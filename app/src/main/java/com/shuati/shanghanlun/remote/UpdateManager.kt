package com.shuati.shanghanlun.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

data class AppVersion(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val note: String
)

object UpdateManager {
    private const val VERSION_URL = "https://xn--r7qu00bb2c.top/version.json" // 你的版本文件地址

    suspend fun checkUpdate(context: Context): AppVersion? {
        return withContext(Dispatchers.IO) {
            try {
                val json = URL(VERSION_URL).readText()
                val remoteVersion = Gson().fromJson(json, AppVersion::class.java)

                // 获取当前 App 版本
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= 28) {
                    pInfo.longVersionCode.toInt()
                } else {
                    pInfo.versionCode
                }

                if (remoteVersion.versionCode > currentVersionCode) {
                    remoteVersion
                } else {
                    null // 没有新版本
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun openBrowserDownload(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}