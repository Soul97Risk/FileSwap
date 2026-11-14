# 第三方组件声明

FileSwap 随包分发（APK 内含其字节码）的第三方组件、其许可，以及**再分发时必须保留的声明**。

许可分布与两个许可族的义务写入 §1；逐模块清单在 §3 与 §5。

---

## 1. 许可分布

| 许可 | 模块数 | 再分发时要做什么 |
|---|---|---|
| **Apache License 2.0** | 108 | 附上许可副本（§4a）；保留版权 / 专利 / 商标 / 署名声明（§4c）；改过的文件加显著说明（§4b）；上游若带 `NOTICE` 则一并附带（§4d） |
| **MIT** | 4 | 在所有副本中附上版权声明**与本许可全文** —— 这就是下面要贴 MIT 原文的原因 |

两族均为宽松许可：允许闭源再分发、允许商用、**都不要求你公开自己的代码**。
它们与本项目自身的 Apache-2.0 兼容，不存在许可传染。

---

## 2. 直接依赖

| 组件 | 坐标 | 版本 | 许可 | 用途 |
|---|---|---|---|---|
| Shizuku API | `dev.rikka.shizuku:api` / `:provider` / `:aidl` / `:shared` | 13.1.5 | MIT | `shell` 权限授权与 UserService 绑定 |
| libsu | `com.github.topjohnwu:libsu`（`:core` / `:io` / `:nio` / `:service`） | 6.0.0 | Apache-2.0 | su 会话与 `RootService` |
| Jetpack Compose | `androidx.compose.*`（BOM `2026.05.01`） | — | Apache-2.0 | UI 运行时、布局、动画、文本 |
| Material 3 | `androidx.compose.material3:material3` | 1.5.0-alpha22 | Apache-2.0 | 组件与 M3 Expressive 形状 / 动效 |
| Material Icons Extended | `androidx.compose.material:material-icons-extended` | 1.7.8 | Apache-2.0 | 界面图标 |
| AndroidX Core / Activity / Lifecycle | `androidx.core` / `androidx.activity` / `androidx.lifecycle` | 见 §5 | Apache-2.0 | 生命周期、`enableEdgeToEdge`、`ViewModel` |
| Kotlin Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-*` | 1.11.0 | Apache-2.0 | 协程与状态流 |
| Kotlin Standard Library | `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.0 | Apache-2.0 | 语言运行时 |

---

## 3. MIT 组件全清单

这 4 个模块同属 **Shizuku-API**，共用一份许可与一份版权声明。

| 模块 | 版本 |
|---|---|
| `dev.rikka.shizuku:aidl` | 13.1.5 |
| `dev.rikka.shizuku:api` | 13.1.5 |
| `dev.rikka.shizuku:provider` | 13.1.5 |
| `dev.rikka.shizuku:shared` | 13.1.5 |

上游：<https://github.com/RikkaApps/Shizuku-API>

> 别与 `RikkaApps/Shizuku` 混淆。那是 Shizuku **管理器应用**，许可为 Apache-2.0，
> 本项目只在运行时调用它，不打包它的代码，因此不在本文件中列版权声明。

---

## 4. MIT 许可全文（Shizuku-API）

```text
MIT License

Copyright (c) 2021 RikkaW

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 5. Apache-2.0 组件全清单

108 个模块。均以 Apache License 2.0 分发，版权归各自贡献者（Google / Android Open Source Project、
JetBrains、topjohnwu 等）。

| 模块 | 版本 |
|---|---|
| `androidx.activity:activity` | 1.13.0 |
| `androidx.activity:activity-compose` | 1.13.0 |
| `androidx.activity:activity-ktx` | 1.13.0 |
| `androidx.annotation:annotation` | 1.9.1 |
| `androidx.annotation:annotation-experimental` | 1.4.1 |
| `androidx.annotation:annotation-jvm` | 1.9.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.autofill:autofill` | 1.0.0 |
| `androidx.collection:collection` | 1.5.0 |
| `androidx.collection:collection-jvm` | 1.5.0 |
| `androidx.collection:collection-ktx` | 1.5.0 |
| `androidx.compose:compose-bom` | 2026.05.01 |
| `androidx.compose.animation:animation` | 1.12.0-alpha03 |
| `androidx.compose.animation:animation-android` | 1.12.0-alpha03 |
| `androidx.compose.animation:animation-core` | 1.12.0-alpha03 |
| `androidx.compose.animation:animation-core-android` | 1.12.0-alpha03 |
| `androidx.compose.foundation:foundation` | 1.12.0-alpha03 |
| `androidx.compose.foundation:foundation-android` | 1.12.0-alpha03 |
| `androidx.compose.foundation:foundation-layout` | 1.12.0-alpha03 |
| `androidx.compose.foundation:foundation-layout-android` | 1.12.0-alpha03 |
| `androidx.compose.material:material-icons-core` | 1.7.8 |
| `androidx.compose.material:material-icons-core-android` | 1.7.8 |
| `androidx.compose.material:material-icons-extended-android` | 1.7.8 |
| `androidx.compose.material:material-ripple` | 1.12.0-alpha03 |
| `androidx.compose.material:material-ripple-android` | 1.12.0-alpha03 |
| `androidx.compose.material3:material3` | 1.5.0-alpha22 |
| `androidx.compose.material3:material3-android` | 1.5.0-alpha22 |
| `androidx.compose.runtime:runtime` | 1.12.0-alpha03 |
| `androidx.compose.runtime:runtime-android` | 1.12.0-alpha03 |
| `androidx.compose.runtime:runtime-annotation` | 1.12.0-alpha03 |
| `androidx.compose.runtime:runtime-annotation-android` | 1.12.0-alpha03 |
| `androidx.compose.runtime:runtime-retain` | 1.12.0-alpha03 |
| `androidx.compose.runtime:runtime-retain-android` | 1.12.0-alpha03 |
| `androidx.compose.runtime:runtime-saveable` | 1.12.0-alpha03 |
| `androidx.compose.runtime:runtime-saveable-android` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-android` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-geometry` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-geometry-android` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-graphics` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-graphics-android` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-text` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-text-android` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-tooling-preview-android` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-unit` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-unit-android` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-util` | 1.12.0-alpha03 |
| `androidx.compose.ui:ui-util-android` | 1.12.0-alpha03 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core` | 1.18.0 |
| `androidx.core:core-ktx` | 1.18.0 |
| `androidx.core:core-viewtree` | 1.0.0 |
| `androidx.customview:customview-poolingcontainer` | 1.0.0 |
| `androidx.emoji2:emoji2` | 1.4.0 |
| `androidx.graphics:graphics-path` | 1.0.1 |
| `androidx.graphics:graphics-shapes` | 1.0.1 |
| `androidx.graphics:graphics-shapes-android` | 1.0.1 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common` | 2.10.0 |
| `androidx.lifecycle:lifecycle-common-java8` | 2.10.0 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.10.0 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.10.0 |
| `androidx.lifecycle:lifecycle-process` | 2.10.0 |
| `androidx.lifecycle:lifecycle-runtime` | 2.10.0 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.10.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.10.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android` | 2.10.0 |
| `androidx.navigationevent:navigationevent` | 1.1.0 |
| `androidx.navigationevent:navigationevent-android` | 1.1.0 |
| `androidx.navigationevent:navigationevent-compose` | 1.1.0 |
| `androidx.navigationevent:navigationevent-compose-android` | 1.1.0 |
| `androidx.profileinstaller:profileinstaller` | 1.4.0 |
| `androidx.savedstate:savedstate` | 1.4.0 |
| `androidx.savedstate:savedstate-android` | 1.4.0 |
| `androidx.savedstate:savedstate-compose` | 1.4.0 |
| `androidx.savedstate:savedstate-compose-android` | 1.4.0 |
| `androidx.savedstate:savedstate-ktx` | 1.4.0 |
| `androidx.startup:startup-runtime` | 1.1.1 |
| `androidx.tracing:tracing` | 1.2.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `androidx.window:window` | 1.5.0 |
| `androidx.window:window-core` | 1.5.0 |
| `androidx.window:window-core-android` | 1.5.0 |
| `com.github.topjohnwu:libsu` | 6.0.0 |
| `com.github.topjohnwu.libsu:core` | 6.0.0 |
| `com.github.topjohnwu.libsu:io` | 6.0.0 |
| `com.github.topjohnwu.libsu:nio` | 6.0.0 |
| `com.github.topjohnwu.libsu:service` | 6.0.0 |
| `com.google.guava:listenablefuture` | 1.0 |
| `org.jetbrains:annotations` | 23.0.0 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.4.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-common` | 2.4.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.11.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.11.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.7.3 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 |
| `org.jspecify:jspecify` | 1.0.0 |

---

## 6. Apache-2.0 许可全文

本项目的 [LICENSE](LICENSE) 就是 Apache License 2.0 全文，同一份文本，可直接当作上述组件的许可副本一并分发。

如果你只取走部分代码、不带 `LICENSE`，请从 <https://www.apache.org/licenses/LICENSE-2.0.txt> 取原文附上。

---

## 7. 本文件的生成与核对

| 项 | 值 |
|---|---|
| 核对日期 | 2026-11-14 |
| 解析命令 | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` |
| 许可来源 | 各模块 POM 的 `<licenses>` 字段（Google Maven / Maven Central / JitPack） |
| 覆盖范围 | `release` 变体的运行时 classpath。`test` / `androidTest` / 构建期工具不在内 —— 它们的代码不进入 APK |

两个无法直接从 POM 取到的特例，判定依据写在下面，可自行复核：

- `com.github.topjohnwu:libsu` 及其子模块 —— JitPack 构建的 POM 没有 `<licenses>` 字段。
  按上游仓库 <https://github.com/topjohnwu/libsu> 的 `LICENSE` 判定为 **Apache-2.0**。
- `com.google.guava:listenablefuture:1.0` —— POM 同样没有该字段。
  该 artifact 是从 Guava 抽出的单接口包，按上游 <https://github.com/google/guava> 的 `LICENSE` 判定为 **Apache-2.0**。

升级任何依赖后，请重跑上面的解析命令并更新 §5。`LICENSE` 与 `THIRD-PARTY-NOTICES.md` 必须随发行物一起分发。
