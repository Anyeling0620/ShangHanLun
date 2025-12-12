plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shuati.shanghanlun"
    compileSdk = 35

    packaging {
        packaging {
            resources {
                // [修正] 注意拼写是 dump_syms (有个 m)
                excludes += "**/dump_syms/**"

                // 为了保险，把下面那个 mozilla 文件夹也剔除（通常是一伙的）
                excludes += "**/mozilla/**"

                // 其他通用的垃圾剔除规则保持不变
                excludes += "META-INF/{AL2.0,LGPL2.1}"
                excludes += "META-INF/LICENSE*"
                excludes += "META-INF/NOTICE*"
                excludes += "win32-x86/**"
                excludes += "win32-x86-64/**"
                excludes += "linux-x86/**"
                excludes += "linux-x86-64/**"
                excludes += "darwin/**"
            }
        }
    }

    defaultConfig {
        applicationId = "com.shuati.shanghanlun"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.14"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        signingConfig = signingConfigs.getByName("debug")
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            // 1. 开启代码混淆 (移除无用代码)
            isMinifyEnabled = true
            // 2. 开启资源压缩 (移除无用资源)
            isShrinkResources = true

            // 3. 加载混淆规则 (关键！)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.google.gson)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.appcompat)
    implementation("io.coil-kt:coil-compose:2.5.0") // 或者是最新版本
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    }
}
dependencies {
    implementation(libs.firebase.crashlytics.buildtools)
}
