# FileSwap

**把任意文件替换进 Android 的受限目录，并随时原样还原。**

面向游戏 mod、资源热替换、配置文件改写这类「改了要能退回去」的场景。用 Root / Shizuku 拿到权限，
在应用内选文件、备份、写入，一条命令级别的操作全部落在图形界面里完成。

`minSdk 24` · `targetSdk 36` · Kotlin + Jetpack Compose (Material 3 Expressive) · Apache-2.0

---

## 它解决什么问题

Android 11 之后，`/sdcard/Android/data/<包名>/` 和 `Android/obb/` 对其他应用彻底关闭：
系统文件选择器不给授权，「所有文件访问」也管不到。想改一个游戏的资源文件，常规手段全部失效。

FileSwap 把这件事拆成三步，并且**每一步都可逆**：

1. **导入** —— 把替换源复制进应用私有目录，之后不依赖外部文件还在不在。
2. **替换** —— 先把目标文件备份到你指定的位置，再把源写进去；可选保持目标文件原来的修改时间。
3. **还原** —— 从备份写回，恢复成替换前的样子。

---

## 四种执行引擎

| 引擎 | 身份 | 能碰到的范围 | 前置条件 |
|---|---|---|---|
| **自动** | 按可用性择优选 | — | 默认 |
| **Root** | `uid=0` | 几乎无限制，含其他应用私有数据 | Magisk / KernelSU / APatch 授权 |
| **Shizuku** | `shell (2000)` 或 `root (0)`，取决于 Shizuku 怎么启动 | 共享存储全量；`Android/data`、`Android/obb` | 已安装并运行 Shizuku |
| **本地存储** | 应用自身 uid | 安卓 11+ 的「所有文件访问」覆盖范围（**不含**其他应用 `Android/data`、`Android/obb`） | 系统里授予对应权限 |

引擎在顶部状态卡切换，选择会持久化。所有引擎共用同一份文件操作实现，行为一致，差别只在权限身份。

### Shizuku 双通道

Shizuku 模式内部有两条路径，按需自动回退：

- **UserService**（`IFileService`，运行在独立子进程）—— 常规路径，零 shell 拼接。
- **服务端 shell**（经 `IShizukuService.newProcess`）—— 在 Shizuku 服务端上下文执行 `stat` / `cp` / `touch`。
  部分 ROM（实测 vivo 安卓 11）上，UserService 子进程访问其他应用 `Android/data` 会连目录遍历都 `EACCES`，
  而同为 `shell(2000)` 的服务端通道可以。路径一律经单引号转义后拼进命令。

### SAF 回退

部分 Android 10 及以下的设备/文档提供器允许用户为受限目录授予树访问权。
SAF 通道用应用自身 uid 走 `ContentResolver`，与 shell 通道完全解耦；不可用时日志会说明原因。

---

## 功能

- **内置文件浏览器**：面包屑路径可跳任意上级、按类型着色图标、条目多于 8 条时出现过滤框、排序方式与方向跨会话记忆
- **最近导入**：记住最近 8 个替换源，mod 迭代时一键重新导入
- **快捷预设**：把「目标文件 + 备份路径 + 替换源」绑成一个组合，之后一键替换 / 一键还原
- **保持原修改时间**：写入后把目标的 mtime 设回备份文件记录的原始值，跨设备导入备份也能正确还原
- **失败诊断**：每个错误信息都带执行身份 `uid`，并区分「文件真的不存在」与「当前身份无权访问」，
  同时给出最近一个可见的上级目录
- **日志时间线**：最新在前，圆点颜色区分结果，可导出用于排查
- **Material 3 Expressive**：吸底主操作栏、弹簧形变指示器、动态取色开关、触感反馈

---

## 构建

### 环境要求

| 项 | 版本 |
|---|---|
| JDK | 17+ |
| Gradle | 9.5.1（随 wrapper 拉取） |
| Android Gradle Plugin | 9.2.1 |
| compileSdk / buildTools | 37 / 37.0.0 |
| NDK | 不需要 |
| minSdk / targetSdk | 24 / 36 |

> AGP 9 起内置 Kotlin 支持（`android.builtInKotlin`），工程里**不再**声明
> `org.jetbrains.kotlin.android` 插件——重复应用会被 AGP 直接拒绝。Compose 编译器插件仍需单独声明。

### 步骤

```bash
git clone https://github.com/Soul97Risk/FileSwap.git
cd FileSwap

# 1. 指到你本机的 SDK（这个文件不入库）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
#   Windows 写法：sdk.dir=D\:/AndroidDevEnv/Sdk

# 2. Debug 构建
./gradlew :app:assembleDebug
#   产物：app/build/outputs/apk/debug/app-debug.apk

# 3. Release 构建（需自备签名配置）
./gradlew :app:assembleRelease
```

### 关于编码

源码注释与字符串里含中文与 emoji 字面量。`gradle.properties` 已强制 `UTF-8`：

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8
systemProp.file.encoding=UTF-8
systemProp.sun.jnu.encoding=UTF-8
```

去掉这几行的直接后果是 Windows 下产物里出现乱码。

### 关于 R8

`app/proguard-rules.pro` 刻意只开**压缩**，不开混淆与优化：

```proguard
-dontobfuscate
-dontoptimize
```

只加 `-dontobfuscate` 时，R8 的优化阶段仍会把 `MainViewModel` 里的业务方法内联进调用点并从类里删掉。
两条都关掉后，反查 APK 时类结构仍与源码一一对应。代价是体积略大。

服务类另需 `-keep`：`FileService` 由 Shizuku 服务端反射实例化，`RootFileService` 由 libsu 在 root 进程反射实例化，
R8 看不到这些引用，会把构造函数当无用成员删掉。

---

## 项目结构

```
app/src/main/
├── aidl/com/fileswap/app/
│   ├── IFileService.aidl          # Shizuku UserService 契约（version 4）
│   └── IRootFileService.aidl      # libsu RootService 契约
├── java/com/fileswap/app/
│   ├── MainActivity.kt            # Compose UI 全部界面
│   ├── MainViewModel.kt           # 状态机、引擎解析、导入/替换/还原/诊断
│   ├── FileOps.kt                 # 引擎无关门面 + Shizuku/Root 适配器
│   ├── FileOpsImpl.kt             # 三条通道共享的文件操作实现
│   ├── LocalOps.kt                # 本地引擎（进程内，零 IPC）
│   ├── FileService.kt             # Shizuku UserService 入口
│   ├── RootFileService.kt         # libsu RootService 入口
│   ├── ShizukuManager.kt          # 权限申请 + UserService 绑定
│   ├── ShizukuShell.kt            # 服务端 shell 通道
│   ├── RootManager.kt             # su 探测 + RootService 绑定
│   ├── SafOps.kt                  # SAF 树授权通道
│   ├── BrowserMemory.kt           # 选择器记忆（上次目录 / 最近导入 / 排序）
│   ├── QuickPreset.kt             # 快捷预设模型与持久化
│   └── ui/theme/                  # M3 色板、形状、字体
└── res/
```

### 两条容易踩的实现约定

**1. 绑定必须共享等待。** Shizuku UserService 冷启动在部分 ROM 上实测可达 58 秒。
早先「每个调用各自 20 秒串行等待」的实现会把一次冷启动放大成连续超时。现在的做法是：
在途绑定挂到同一个 `CompletableDeferred` 上，服务一旦上线所有等待者一起成功；超时只报一次原因。

**2. 私有目录必须走 fd。** shell 身份读不了 `/data/user/0/<pkg>/`。
所以导入方向用 `openRead()` 把 fd 交回应用自己落盘，替换方向用 `copyFromFd()` 由应用打开源、服务只负责写入。

---

## 已知限制

- 部分 API 处于 Material 3 **alpha** 通道（`material3:1.5.0-alpha22`、expressive 实验接口）。
  升级依赖版本时，`ButtonShapes` / `TextFieldDefaults.colors` 之类的签名可能需要跟着调整。
- Android 11+ 的「所有文件访问」按官方定义**不覆盖**其他应用的 `Android/data` 与 `Android/obb`。
  这类目标只能用 Root 或 Shizuku。
- Shizuku 13.6.0 有 `Android/data` 可见性与 UserService 启动崩溃两组已知问题
  （上游 issue #1278 / #1171），诊断信息里会带出 Shizuku 的实际发行版本名以便区分。

> 替换系统或应用的被引用文件可能导致该应用崩溃、校验失败或存档损坏。
> 请确认备份路径有效后再执行替换。

---

## 致谢

这个项目没有自己的 `shell`、没有自己的 su 实现、也没有自己的设计语言。它站在三个方向上。

### 一、代码依赖（直接）

| 项目 | 版本 | 许可 | 在 FileSwap 里承担什么 |
|---|---|---|---|
| [Shizuku API](https://github.com/RikkaApps/Shizuku-API) · RikkaW | 13.1.5 | **MIT** | `shell` 权限入口：运行时授权、UserService 绑定、服务端进程执行 |
| [libsu](https://github.com/topjohnwu/libsu) · topjohnwu | 6.0.0 | Apache-2.0 | su 会话探测与 `RootService`（root 进程中的文件服务） |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | BOM 2026.05.01 | Apache-2.0 | 全部界面 |
| [Material 3](https://m3.material.io/) | 1.5.0-alpha22 | Apache-2.0 | 组件、形状档位、动效、`LoadingIndicator` |
| [Material Icons Extended](https://fonts.google.com/icons) | 1.7.8 | Apache-2.0 | 界面图标 |
| [AndroidX Core / Activity / Lifecycle](https://developer.android.com/jetpack/androidx) | — | Apache-2.0 | `ComponentActivity`、`enableEdgeToEdge`、`AndroidViewModel` |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) · JetBrains | 1.11.0 | Apache-2.0 | 绑定、IO、状态流 |
| [Kotlin Standard Library](https://kotlinlang.org/) · JetBrains | 2.4.0 | Apache-2.0 | 语言运行时 |

版本取自 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 的实际解析结果。
112 个模块（含传递依赖）的完整清单与逐个许可核对见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。

> 构建期工具（Gradle、AGP、Kotlin 编译器、R8）不进入 APK，其许可与署名见各自仓库，此处不列。

### 二、运行时依赖（不打包，但决定这个项目能不能用）

- **[Shizuku](https://github.com/RikkaApps/Shizuku)** —— 项目目录一度叫 `ShizukuFileTool`。没有它，
  「免 Root 但仍能写 `Android/data`」这条路根本不存在。
  注意区分：Shizuku **API 库**是 MIT，Shizuku **管理器应用**是 Apache-2.0，两者不是同一份许可。
- **[Magisk](https://github.com/topjohnwu/Magisk)** / **[KernelSU](https://github.com/tiann/KernelSU)** / **[APatch](https://github.com/bmax121/APatch)** ——
  Root 引擎的 su 提供方。三者对 `su -M` 的支持不一致，`RootManager` 里「su 不支持该参数时自动回退普通 su」就是为它们写的。
- **Android `ExternalStorageProvider`** —— SAF 通道的对手方。`SafOps` 里「`query` 会丢弃 `selection` 参数」
  「覆盖既存文档要走先删后建」这两条结论，全部来自对着它的实测。

### 三、规范与文档

- **[Material 3 Expressive](https://m3.material.io/)** —— 吸底操作栏、连接按钮组、形状档位、多边形形变加载器的依据。
- **Android API 文档**中关于 `MANAGE_EXTERNAL_STORAGE` 排除项的说明 —— 「所有文件访问也管不到别人 `Android/data`」
  这条边界直接决定了功能设计，而不是实现细节。
- **Shizuku 上游 issue #1278 / #1171** —— 13.6.0 的 `Android/data` 可见性回归与 UserService 启动崩溃，
  是 `appVersionName()` 这个方法存在的原因。

### 四、踩过的坑

`ShizukuManager` / `RootManager` 里那些 90 秒超时、共享 `CompletableDeferred`、`Shell.Builder.create().build()`
重建缓存的注释，每一条背后都是一台真机上的失败。没有那些机型，这个项目会一直停在「我这里能跑」。

---

## 开源规范

### 许可

**Apache License 2.0**。三份文件分工不同，缺一不可：

| 文件 | 作用 |
|---|---|
| [LICENSE](LICENSE) | 许可全文。法律文本，不要改 |
| [NOTICE](NOTICE) | 项目级署名 |
| [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) | 第三方组件清单 + MIT 许可全文 |

### 使用这份代码，需要做什么

| 你想做的事 | 你需要做的 |
|---|---|
| 读代码、自己改着用、不分发 | 无强制义务。保留 `LICENSE` 是最低礼貌 |
| fork 后改，自己编译自己用 | 同上 |
| 分发**修改后的源码** | ① 附上 `LICENSE` 全文（§4a）② 保留原有版权 / 专利 / 商标 / 署名声明（§4c）③ 在改过的文件里加显著说明（§4b）④ 附上 `NOTICE`（§4d） |
| 发布基于它的 **APK** | 与上一行完全相同。APK 属于 Object form，同样受 §4 约束。在「关于」页放许可与署名即可满足 |
| 商用、放进自己的产品 | 允许，不收费，**也不需要公开你的代码** —— Apache-2.0 不是 copyleft |
| 二次闭源分发 | 允许，但 §4 的署名与声明义务照旧 |
| 把它改名成 FileSwap 再发布 | 不行。§6 只授予「为说明来源而合理使用名称」的权利，不含商标 |
| 要求本项目把你的修改也开源 | 不能。除非你另外同意，否则你的改动可以完全私有 |

### 两处最容易漏掉的上游义务

1. **Shizuku API 是 MIT，不是 Apache-2.0。** MIT 要求「在所有副本中附上版权声明与本许可声明」。
   本项目已把 `Copyright (c) 2021 RikkaW` 与 MIT 全文放进 `THIRD-PARTY-NOTICES.md`——
   **你再分发时不要删掉**。删掉就是违约，与你删的是 Markdown 还是 APK 里的文件无关。
2. **libsu 是 Apache-2.0，但上游仓库没有 `NOTICE` 文件**，因此 §4(d) 不额外要求你附带什么；
   保留 `LICENSE` 与代码里的版权头即可。

### 参与贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。摘要四条：

- 提交前 `./gradlew :app:assembleDebug` 必须通过
- Kotlin 官方代码风格，不引入新的格式化工具链
- 改 AIDL 必须递增 `UserService` 的 `version`，否则 Shizuku 会继续复用旧进程，新方法直接 `NoSuchMethod`
- 不提交 `local.properties`、签名材料、任何密钥

按 Apache-2.0 §5，你提交的贡献默认以同一许可授权（inbound = outbound），无需另签协议。

### 安全

漏洞报告走 [SECURITY.md](SECURITY.md) 里的渠道，不要开公开 issue 贴可利用细节。

### 本项目不做什么

- 不内置、不提供任何游戏或应用的文件与下载源
- 不绕过签名校验，不伪造服务端响应。它只负责把文件写到你有权限写的位置，剩下的事它不管
- 不联网：`AndroidManifest.xml` 里没有 `INTERNET` 权限，可以自己 grep 确认

---

作者 [@kndnb666](https://t.me/kndnb666)
