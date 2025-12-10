package com.shuati.shanghanlun.data.local

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import android.widget.Toast
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
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// 1. 定义数据类 (放在 object 外部)
data class FontConfig(
    val code: String,
    val name: String,
    val fileName: String,
    val url: String
)

// 2. 定义单例对象 (确保文件中只有一个 object FontManager)
object FontManager {
    private const val PREF_FONT = "app_font_pref"
    private const val KEY_CURRENT_FONT = "current_font_key"
    private const val CHANNEL_ID = "font_download_channel"
    private const val NOTIFICATION_ID = 1001

    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    // 基础 URL
    private const val BASE_URL = "https://xn--r7qu00bb2c.top/font"

    // 字体列表配置
    val fontList = listOf(
        FontConfig("SYSTEM", "系统默认", "", ""),
        FontConfig("YAHEI", "微软雅黑", "msyh.ttf", "https://111.228.58.72:41347/down/XscBKv1akCLx.ttf"),
        FontConfig("WK_REGULAR", "文楷-标准", "LXGWWenKai-Regular.ttf", "https://111.228.58.72:41347/down/9CbvERNEb3zs.ttf"),
        FontConfig("WK_MONO_REG", "文楷等宽-标准", "LXGWWenKaiMono-Regular.ttf", "https://111.228.58.72:41347/down/wIQMhPiwK5QR.ttf"),
        FontConfig("WK_MEDIUM", "文楷-中粗", "LXGWWenKai-Medium.ttf", "https://111.228.58.72:41347/down/dAAlXzwIpeb1.ttf"),
        FontConfig("WK_MONO_MED", "文楷等宽-中粗", "LXGWWenKaiMono-Medium.ttf", "https://111.228.58.72:41347/down/VUkgerewRaQN.ttf"),
        FontConfig("WK_LIGHT", "文楷-细体", "LXGWWenKai-Light.ttf", "https://111.228.58.72:41347/down/hq1M3lhmLKja.ttf"),
        FontConfig("WK_MONO_LGT", "文楷等宽-细体", "LXGWWenKaiMono-Light.ttf", "https://111.228.58.72:41347/down/9CbvERNEb3zs.ttf")
    )

    var currentFontFamily by mutableStateOf<FontFamily>(FontFamily.Default)
    var currentFontName by mutableStateOf("系统默认")

    // 下载状态管理
    var downloadingStates = mutableMapOf<String, Boolean>()
    var isBackgroundDownloading by mutableStateOf(false)

    // 初始化方法
    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREF_FONT, Context.MODE_PRIVATE)

        createNotificationChannel() // 初始化通知渠道

        val savedFontCode = prefs.getString(KEY_CURRENT_FONT, "SYSTEM") ?: "SYSTEM"
        if (isFontDownloaded(savedFontCode)) {
            applyFont(savedFontCode)
        } else {
            applyFont("SYSTEM")
        }
    }

    // 创建通知渠道 (Android 8.0+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "资源下载"
            val descriptionText = "显示字体下载进度"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // 启动后台静默下载 (不带回调)
    fun startBackgroundDownload() {
        isBackgroundDownloading = true
        CoroutineScope(Dispatchers.IO).launch {
            fontList.forEach { config ->
                if (config.code != "SYSTEM" && !isFontDownloaded(config.code)) {
                    // 调用下面的 downloadFont，传入空的回调
                    downloadFont(config.code) { }
                }
            }
            isBackgroundDownloading = false
        }
    }

    // 检查是否已下载
    fun isFontDownloaded(fontCode: String): Boolean {
        if (fontCode == "SYSTEM") return true
        val config = fontList.find { it.code == fontCode } ?: return false
        val file = File(appContext.filesDir, "fonts/${config.fileName}")
        return file.exists() && file.length() > 10240
    }

    // 切换字体
    fun switchFont(fontCode: String) {
        if (isFontDownloaded(fontCode)) {
            prefs.edit().putString(KEY_CURRENT_FONT, fontCode).apply()
            applyFont(fontCode)
        }
    }

    // [核心] 下载字体 (带进度回调)
    // [核心] 下载字体 (带进度回调 + 忽略SSL校验)
    suspend fun downloadFont(fontCode: String, onProgress: (Float) -> Unit): Boolean {
        val config = fontList.find { it.code == fontCode } ?: return false
        if (config.url.isEmpty()) return false

        withContext(Dispatchers.Main) { downloadingStates[fontCode] = true }

        // 准备通知
        val notifyManager = NotificationManagerCompat.from(appContext)
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setContentTitle("正在下载: ${config.name}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, 0, false)

        try { notifyManager.notify(NOTIFICATION_ID, builder.build()) } catch (e: SecurityException) {}

        return withContext(Dispatchers.IO) {
            var success = false
            try {
                val url = URL(config.url)
                val connection = url.openConnection() as HttpURLConnection

                // ▼▼▼▼▼▼ 核心修改区域 ▼▼▼▼▼▼
                // 如果是 HTTPS，强制信任所有证书
                if (connection is HttpsURLConnection) {
                    // 1. 创建一个信任所有证书的 TrustManager
                    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    })

                    // 2. 初始化 SSLContext
                    val sc = SSLContext.getInstance("SSL")
                    sc.init(null, trustAllCerts, SecureRandom())

                    // 3. 应用 SocketFactory (注意这里全是小写 ssl)
                    connection.sslSocketFactory = sc.socketFactory

                    // 4. 忽略主机名校验 (允许 IP 直连)
                    connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
                }
                // ▲▲▲▲▲▲ 修改结束 ▲▲▲▲▲▲

                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val fileLength = connection.contentLength
                    val inputStream = connection.inputStream

                    val fontDir = File(appContext.filesDir, "fonts")
                    if (!fontDir.exists()) fontDir.mkdirs()

                    val tmpFile = File(fontDir, "${config.fileName}.tmp")
                    val finalFile = File(fontDir, config.fileName)

                    val outputStream = FileOutputStream(tmpFile)
                    val data = ByteArray(4096)
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
                            if (now - lastNotifyTime > 500) {
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
                    // 错误提示
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "下载失败: ${connection.responseCode}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                success = false
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "出错: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) { downloadingStates[fontCode] = false }
                try { notifyManager.cancel(NOTIFICATION_ID) } catch (e: SecurityException) {}
            }
            success
        }
    }

    // 应用字体到 Compose
    private fun applyFont(fontCode: String) {
        val config = fontList.find { it.code == fontCode }

        if (config == null || fontCode == "SYSTEM") {
            currentFontFamily = FontFamily.Default
            currentFontName = "系统默认"
            return
        }

        try {
            val file = File(appContext.filesDir, "fonts/${config.fileName}")
            if (file.exists()) {
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