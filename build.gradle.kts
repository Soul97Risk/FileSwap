// AGP 9.0 起内置 Kotlin 支持（android.builtInKotlin），不再需要 org.jetbrains.kotlin.android
// 插件；重复应用会被 AGP 直接拒绝。Compose 编译器插件仍需单独声明。
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}
