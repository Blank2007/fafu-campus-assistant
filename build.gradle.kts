// 根构建脚本：只声明插件版本，具体依赖在 app 模块
plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9.0+ 内建 Kotlin 支持，不再需要 org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
