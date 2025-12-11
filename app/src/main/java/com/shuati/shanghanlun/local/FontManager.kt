package com.shuati.shanghanlun.data.local

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class FontConfig(
    val code: String,
    val name: String,
    val fileName: String,
    val url: String
)

object FontManager {
    private const val PREF_FONT = "app_font_pref"
    private const val KEY_CURRENT_FONT = "current_font_key"
    private const val CHANNEL_ID = "font_download_channel"
    private const val NOTIFICATION_ID = 1001

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    // 文楷 Lite Release v1.521 加速基地址
    private const val WK_BASE = "https://github.com/lxgw/LxgwWenKai-Lite/releases/download/v1.521"

    // 微软雅黑托管地址 (第三方)
    private const val YAHEI_URL = "https://mirror.ghproxy.com/https://raw.githubusercontent.com/dolbydu/font/master/unicode/Microsoft%20Yahei.ttf"

    // 字体列表：每两个一组，偶数位(Index 1,3,5,7)为右侧列
    val fontList = listOf(
        // Row 1
        FontConfig("SYSTEM", "系统默认", "", ""),
        FontConfig("YAHEI", "微软雅黑", "https://111.228.58.72:41347/down/XscBKv1akCLx.ttf", YAHEI_URL),

        // Row 2
        FontConfig(
            "WK_LITE_REG", "文楷-标准",
            "LXGWWenKaiLite-Regular.ttf", "$WK_BASE/LXGWWenKaiLite-Regular.ttf"
        ),
        FontConfig(
            "WK_MONO_LITE_REG", "文楷等宽-标准",
            "LXGWWenKaiMonoLite-Regular.ttf", "$WK_BASE/LXGWWenKaiMonoLite-Regular.ttf"
        ),

        // Row 3
        FontConfig(
            "WK_LITE_MED", "文楷-中粗",
            "LXGWWenKaiLite-Medium.ttf", "$WK_BASE/LXGWWenKaiLite-Medium.ttf"
        ),
        FontConfig(
            "WK_MONO_LITE_MED", "文楷等宽-中粗",
            "LXGWWenKaiMonoLite-Medium.ttf", "$WK_BASE/LXGWWenKaiMonoLite-Medium.ttf"
        ),

        // Row 4
        FontConfig(
            "WK_LITE_LGT", "文楷-细体",
            "LXGWWenKaiLite-Light.ttf", "$WK_BASE/LXGWWenKaiLite-Light.ttf"
        ),
        FontConfig(
            "WK_MONO_LITE_LGT", "文楷等宽-细体",
            "LXGWWenKaiMonoLite-Light.ttf", "$WK_BASE/LXGWWenKaiMonoLite-Light.ttf"
        )
    )

    var currentFontFamily by mutableStateOf<FontFamily>(FontFamily.Default)
    var currentFontName by mutableStateOf("系统默认")

    var downloadingStates = mutableMapOf<String, Boolean>()
    var isBackgroundDownloading by mutableStateOf(false)

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREF_FONT, Context.MODE_PRIVATE)
        createNotificationChannel()

        val savedFontCode = prefs.getString(KEY_CURRENT_FONT, "SYSTEM") ?: "SYSTEM"
        if (isFontDownloaded(savedFontCode)) {
            applyFont(savedFontCode)
        } else {
            applyFont("SYSTEM")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "资源下载", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示字体下载进度"
            }
            val notificationManager: NotificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun startBackgroundDownload() {
        // 默认不开启后台自动下载，节省流量
    }

    fun isFontDownloaded(fontCode: String): Boolean {
        if (fontCode == "SYSTEM") return true
        val config = fontList.find { it.code == fontCode } ?: return false
        val file = File(appContext.filesDir, "fonts/${config.fileName}")
        // Lite字体通常 > 1MB，设100KB阈值防止空文件
        return file.exists() && file.length() > 102400
    }

    fun switchFont(fontCode: String) {
        if (isFontDownloaded(fontCode)) {
            prefs.edit().putString(KEY_CURRENT_FONT, fontCode).apply()
            applyFont(fontCode)
        }
    }

    suspend fun downloadFont(fontCode: String, onProgress: (Float) -> Unit): Boolean {
        val config = fontList.find { it.code == fontCode } ?: return false
        if (config.url.isEmpty()) return false

        withContext(Dispatchers.Main) { downloadingStates[fontCode] = true }

        val notifyManager = NotificationManagerCompat.from(appContext)
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("下载中: ${config.name}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, false)

        try { notifyManager.notify(NOTIFICATION_ID, builder.build()) } catch (e: SecurityException) {}

        return withContext(Dispatchers.IO) {
            var success = false
            try {
                android.util.Log.d("FontDownload", "Start downloading: ${config.url}")
                val url = URL(config.url)
                val connection = url.openConnection() as HttpURLConnection

                if (connection is HttpsURLConnection) {
                    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    })
                    val sc = SSLContext.getInstance("SSL")
                    sc.init(null, trustAllCerts, SecureRandom())
                    connection.sslSocketFactory = sc.socketFactory
                    connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }

                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val fileLength = connection.contentLength
                    val inputStream = connection.inputStream

                    val fontDir = File(appContext.filesDir, "fonts")
                    if (!fontDir.exists()) fontDir.mkdirs()

                    val tmpFile = File(fontDir, "${config.fileName}.tmp")
                    val finalFile = File(fontDir, config.fileName)

                    val outputStream = FileOutputStream(tmpFile)
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    var lastNotifyTime = 0L

                    while (inputStream.read(data).also { count = it } != -1) {
                        total += count
                        outputStream.write(data, 0, count)

                        if (fileLength > 0) {
                            val progress = total.toFloat() / fileLength
                            onProgress(progress)

                            val now = System.currentTimeMillis()
                            if (now - lastNotifyTime > 300) {
                                builder.setProgress(100, (progress * 100).toInt(), false)
                                    .setContentText("${(progress * 100).toInt()}%")
                                try { notifyManager.notify(NOTIFICATION_ID, builder.build()) } catch (e: SecurityException) {}
                                lastNotifyTime = now
                            }
                        }
                    }

                    outputStream.close()
                    inputStream.close()

                    if (tmpFile.renameTo(finalFile)) {
                        success = true
                    }
                } else {
                    android.util.Log.e("FontDownload", "Failed: HTTP ${connection.responseCode}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("FontDownload", "Exception: ${e.message}")
                success = false
            } finally {
                withContext(Dispatchers.Main) { downloadingStates[fontCode] = false }
                try { notifyManager.cancel(NOTIFICATION_ID) } catch (e: SecurityException) {}
            }
            success
        }
    }

    private fun applyFont(fontCode: String) {
        val config = fontList.find { it.code == fontCode }

        if (config == null || fontCode == "SYSTEM") {
            currentFontFamily = FontFamily.Default
            currentFontName = "系统默认"
            return
        }

        try {
            val file = File(appContext.filesDir, "fonts/${config.fileName}")
            if (file.exists() && file.length() > 102400) {
                val typeface = Typeface.createFromFile(file)
                currentFontFamily = FontFamily(typeface)
                currentFontName = config.name
            } else {
                currentFontFamily = FontFamily.Default
                currentFontName = "系统默认 (未下载)"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            currentFontFamily = FontFamily.Default
        }
    }
}