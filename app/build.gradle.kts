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
        // C-2（审查 v4.10）：targetSdk 收敛到 36（API 37 编译环境非 REL，行为变更未经真机回归）
        minSdk = 31
        targetSdk = 36
        versionCode = 81
        versionName = "4.17.sljzy"
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
        // C-3（审查 v4.10）：排除调试/无用产物，保持轻量
        resources {
            excludes += setOf(
                "DebugProbesKt.bin",          // kotlinx-coroutines 调试探针，Release 不需要
                "META-INF/*.version",
                "kotlin/**",                  // Kotlin builtins 元数据（未用反射）
            )
        }
        // ⚠️ v4.15：**不要**去排除 `META-INF/services/*`。
        // 这里原本有两行 `META-INF/services/m5` / `META-INF/services/tp`（C-3 加的"瘦身"），
        // 实测**从未生效**：R8 会把 kotlinx-coroutines 的 ServiceLoader 描述符改名，
        // 文件名是混淆后的**接口名**，每次构建都可能不同（v4.14 是 m5+up，v4.15 是 m5+tp），
        // 按名字写死根本匹配不上，所以 APK 里一直留着它们。
        // 更要紧的是：这两个描述符正是 `Dispatchers.Main` 能被 ServiceLoader 找到的原因，
        // 一旦有人把这条排除"修好"（例如改成 `META-INF/services/*`），App 会直接起不来。
        // 故删掉那两行死配置，留此说明。 / Do NOT exclude META-INF/services/*: those are
        // R8-renamed kotlinx-coroutines ServiceLoader descriptors that back Dispatchers.Main.
    }

    // U2 正式签名：在项目根放 keystore.properties（勿提交！已 gitignore）：
    //   storeFile=/绝对路径/你的.jks
    //   storePassword=xxx
    //   keyAlias=xxx
    //   keyPassword=xxx
    // 不放则回退 debug 签名（方便直接安装调试）
    val ksFile = rootProject.file("keystore.properties")
    val ksMap: Map<String, String> =
        if (ksFile.exists()) {
            ksFile.readLines()
                .mapNotNull { line ->
                    line.split("=", limit = 2)
                        .takeIf { it.size == 2 }
                        ?.let { it[0].trim() to it[1].trim() }
                }
                .toMap()
        } else emptyMap()
    signingConfigs {
        if (ksMap.isNotEmpty()) {
            create("release") {
                storeFile = file(ksMap.getValue("storeFile"))
                storePassword = ksMap["storePassword"]
                keyAlias = ksMap["keyAlias"]
                keyPassword = ksMap["keyPassword"]
            }
        }
    }

    buildTypes {
        release {
            // 体积压缩：R8 混淆 + 资源收缩；用 debug 密钥签名方便直接安装
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
        // C-5（审查 v4.10）：构建机内存有限，lintVital 与 R8 并行会挂（两次实测），
        // 采用报告备选方案：assembleRelease 不内嵌 lint，改为发布流程单独跑
        // :app:lintRelease 并把报告归档进 docs/audit/（发布 checklist 已固化）。
        checkReleaseBuilds = false
        abortOnError = false
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    // ---- Compose（版本由 BOM 统一管理）----
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.exifinterface:exifinterface:1.3.7")   // A3：EXIF 方向
    testImplementation("junit:junit:4.13.2")   // V4-28：纯逻辑单测
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
