# 1. 保护你的数据类不被改名/移除 (最强保护)
-keep class com.example.killquestion.data.model.** { *; }

# 2. 必须保留泛型签名 (解决 TypeToken 报错的关键)
# 否则 List<Question> 会变成 List<Object>，导致 Gson 崩溃
-keepattributes Signature
-keepattributes *Annotation*

# 3. 忽略 Unsafe 警告 (防止编译报错)
-dontwarn sun.misc.Unsafe

# 4. 保留 Gson 自身
-keep class com.google.gson.** { *; }