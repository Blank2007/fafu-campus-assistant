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
        versionCode = 35
        versionName = "2.15"
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
    // - 液态玻璃 Haze 库已按“原生安卓界面、不要花哨”需求移除
    // - 课表模块（含 ML Kit 中文 OCR、WebView 教务抓取）已按需求暂时下线，
    //   源码保留在 parked/schedule/，需要时移回 src 并恢复下方两行依赖即可：
    // implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    // implementation("androidx.webkit:webkit:1.14.0")
}
