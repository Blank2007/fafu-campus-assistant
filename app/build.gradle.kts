plugins {
    id("com.android.application")
    // AGP 9.0+ 内建 Kotlin 支持（kotl.in/gradle/agp-built-in-kotlin），
    // 只保留 Compose 编译器插件
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.campusglass"
    compileSdk = 37          // Android 17 (API 37)

    defaultConfig {
        // 目标环境 Android 15+（HyperOS 4 / Android 17 优先适配），minSdk 31 兼容更广
        minSdk = 31
        targetSdk = 37
        versionCode = 52
        versionName = "3.7.zilyf"
        vectorDrawables.useSupportLibrary = true
    }

    // 体积压缩：只发 arm64-v8a（Android 15+ 机型全部适用），so 不打包成压缩态
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }
    packaging {
        jniLibs.useLegacyPackaging = false
    }

    buildTypes {
        release {
            // 体积压缩：R8 混淆 + 资源收缩；用 debug 密钥签名方便直接安装
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true   // 关于页动态显示版本号用
    }
    lint {
        // 小内存构建机上 lintVital 会和 R8 抢内存导致守护进程 OOM，关闭发布前检查
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // ---- Compose（版本由 BOM 统一管理）----
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- AndroidX 基础 ----
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose")          // BOM 管理
    implementation("androidx.navigation:navigation-compose:2.10.2")

    // 说明：
    // - 课表模块的手动模式已上线（ML Kit OCR / WebView 教务抓取源码保留在 parked/schedule）

    // ---- 亚克力模糊（底部悬浮栏）----
    // chrisbanes/haze, Apache-2.0, https://github.com/chrisbanes/haze
    implementation("dev.chrisbanes.haze:haze:2.0.0")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.0")
}
