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
        versionCode = 48
        versionName = "3.3.zilyf"
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
            // 体积压缩：R8 混淆 + 资源收缩
            isMinifyEnabled = true
            isShrinkResources = true
            // v3.3 待办（需要你本地执行，代码里无法代做）：
            //   1) 生成正式签名：keytool -genkeypair -v -keystore fafu-release.jks -alias fafu -keyalg RSA -keysize 2048 -validity 10000
            //   2) 把密钥信息放到 ~/.gradle/gradle.properties 或本地 keystore.properties（不要提交到仓库）：
            //        RELEASE_STORE_FILE=/绝对路径/fafu-release.jks
            //        RELEASE_STORE_PASSWORD=***
            //        RELEASE_KEY_ALIAS=fafu
            //        RELEASE_KEY_PASSWORD=***
            //   3) 取消下面注释、删掉 signingConfigs.getByName("debug")：
            // signingConfigs {
            //     create("release") {
            //         storeFile = file(project.findProperty("RELEASE_STORE_FILE") as String)
            //         storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String
            //         keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String
            //         keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String
            //     }
            // }
            // 说明：debug 签名会导致换机器后新版本无法覆盖安装（老用户必须卸载重装，课表/历史全丢），
            // 也无法上架任何应用商店。
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
        // v3.3：改回“发布构建也跑 lint”，但先不因警告中断构建。
        // （旧版两次都关掉，导致无用权限、API 版本、资源等问题一个都没被发现）
        checkReleaseBuilds = true
        abortOnError = false
        warningsAsErrors = false
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
    // - 照片方向纠正使用系统自带的 android.media.ExifInterface，无需额外依赖

    // ---- 亚克力模糊（底部悬浮栏）----
    // chrisbanes/haze, Apache-2.0, https://github.com/chrisbanes/haze
    implementation("dev.chrisbanes.haze:haze:2.0.0")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.0")
}
