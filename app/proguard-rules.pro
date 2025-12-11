# ================= 1. 核心保护：防止数据模型崩溃 =================
# 【重点】这里必须是你当前的包名 com.shuati.shanghanlun
-keep class com.shuati.shanghanlun.data.model.** { *; }

# 如果你还有 config 包，也建议保留
-keep class com.shuati.shanghanlun.config.** { *; }

# ================= 2. Gson 必需配置 =================
# 忽略 Unsafe 警告
-dontwarn sun.misc.Unsafe
# 保持泛型签名 (防止 List<Question> 变成 List<Object>)
-keepattributes Signature
-keepattributes *Annotation*
# 保护 Gson 自身
-keep class com.google.gson.** { *; }

# ================= 3. 其他配置 =================
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.compose.** { *; }