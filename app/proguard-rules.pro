# 只保留数据模型，其他的逻辑代码（如 ViewModel, Activity）都允许被混淆成 a.b.c
# 修正为你的新包名
-keep class com.shuati.shanghanlun.data.model.** { *; }
-keep class com.shuati.shanghanlun.config.** { *; }

# Gson 必需
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe

# 移除 Compose 的无用代码 (通常 R8 会自动处理，但加上不亏)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}