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

## 许可

[Apache License 2.0](LICENSE) · Copyright 2026 kndnb666

## 致谢

- [Shizuku](https://github.com/RikkaApps/Shizuku)（Apache-2.0）—— `shell` 权限的统一入口
- [libsu](https://github.com/topjohnwu/libsu)（Apache-2.0）—— su 会话与 RootService
- [Jetpack Compose](https://developer.android.com/jetpack/compose) / [Material 3](https://m3.material.io/)（Apache-2.0）

---

作者 [@kndnb666](https://t.me/kndnb666)
