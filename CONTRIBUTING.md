# 贡献指南

先把结论放前面：**这个项目读得懂比跑得炫重要**。
它处理的是别人手机上的真实文件，一个静默的失败比一个明显的崩溃更糟。所以下面的规矩大多在讲
「怎么让失败可见」。

---

## 一、先跑起来

| 项 | 要求 |
|---|---|
| JDK | 17 或以上（作者用的是 Temurin 21） |
| SDK | `compileSdk 37`、`buildTools 37.0.0` |
| 本机配置 | 自己写 `local.properties`：`sdk.dir=/path/to/Android/Sdk`（Windows 写成 `sdk.dir=D\:/Android/Sdk`） |

```bash
./gradlew :app:assembleDebug
```

提交前这条命令必须通过。只跑 `compileDebugKotlin` 不算 —— 资源合并、Manifest 合并、R8 都可能在这一步之外炸。

---

## 二、分支与提交

- 从 `main` 切分支：`feat/xxx`、`fix/xxx`、`docs/xxx`
- 提交信息用 Conventional Commits 前缀（`feat:` / `fix:` / `refactor:` / `docs:` / `build:` / `chore:`），
  正文写中文，讲**为什么**改，不写「修改了某文件」
- 一个 PR 只做一件事。顺手重排了整个文件的导入顺序的 PR 会被要求拆开

```
fix: UserService 绑定超时从 20s 提到 90s

vivo 安卓 11 上首次冷启动实测 58 秒，20 秒会在服务起来前连环超时。
同时把在途绑定改成共享 deferred，后到的调用挂同一个信号上。
```

---

## 三、代码风格

- Kotlin 官方风格（`kotlin.code.style=official`），IDE 默认格式化即可，不引入 ktlint / spotless
- 不新增依赖，除非它能替掉一大段自己写的代码。加之前先在 issue 里说清「不加会怎样」
- 注释写**为什么**，不写**是什么**。现存的注释密度是基准线，别把解释性的注释删成「代码自解释」
- 面向用户的字符串进 `strings.xml`；日志文案可以留在代码里
- 中文与 emoji 字面量原样保留。`gradle.properties` 已锁 UTF-8，别把它改回去

---

## 四、改 AIDL 的硬性流程

`IFileService` / `IRootFileService` 是跨进程契约，改动有三条不可省的步骤：

1. **递增 `ShizukuManager` 里的 `version(n)`** —— Shizuku 靠版本号决定是否重启 UserService 进程。
   不递增的话，旧进程还在跑，新方法调用直接 `NoSuchMethodException`。这不是「可能出问题」，是必然。
2. **同步三处实现** —— AIDL 接口、`FileOpsImpl`、`FileOps` 的适配器（`ShizukuOps` / `RootOps` / `LocalOps`）。
   `LocalOps` 最容易漏，因为它没有 AIDL，却要和另外两条通道行为一致。
3. **确认 R8 规则仍然覆盖** —— `proguard-rules.pro` 里对服务类与 AIDL 接口的 `-keep` 是按名字写的。
   加接口要跟着加规则，否则反查 APK 时这些成员会「消失」。

---

## 五、新增执行通道的准入条件

想加第四条通道（比如某种新的提权方式）？先满足下面全部四条，否则不加：

- 有明确的**权限边界差异**，不是现有通道的另一种写法
- 失败时有**可读原因**，且带执行身份（`uid`）
- 能被 `EngineMode` 枚举与 UI 状态卡表达，不需要在 `MainViewModel` 里加特殊分支
- 有真机验证记录：机型、ROM、Android 版本、权限来源

「在我这里能跑」不是准入条件。

---

## 六、PR 检查清单

提交前自己过一遍：

- [ ] `./gradlew :app:assembleDebug` 通过
- [ ] 没有提交 `local.properties`、签名材料、任何密钥、`*.apk`
- [ ] 新文件带 SPDX 头：
      ```kotlin
      /*
       * Copyright 2026 kndnb666
       * SPDX-License-Identifier: Apache-2.0
       */
      ```
- [ ] 改了 AIDL 就递增了 `version`
- [ ] 面对用户的错误信息里带得出执行身份与最近可见上级目录
- [ ] 新增的字符串进了 `strings.xml`
- [ ] 如果改了依赖，重跑了 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
      并更新了 `THIRD-PARTY-NOTICES.md` 的 §5

---

## 七、许可

按 Apache-2.0 §5，**你提交的贡献默认以 Apache License 2.0 授权**（inbound = outbound），
无需另签 CLA 或 DCO 签署。请确认你有权这样授权 —— 别把公司内部代码、别人的私有代码贴进来。

---

## 八、不会被接受的改动

| 改动 | 原因 |
|---|---|
| 引入统计、埋点、任何联网上报 | 本项目刻意没有 `INTERNET` 权限，这是设计而非疏漏 |
| 内置游戏 / 应用文件、加下载源 | 项目只搬运用户自己选的文件 |
| 为某个特定应用写死路径或包名 | 通用性是这个工具的全部价值 |
| 关闭 R8 的 `-dontobfuscate` / `-dontoptimize` | 见 README「关于 R8」：关掉后类结构才能与源码一一对应 |
| 「重构」式的全文件重排、改名、换架构 | 单独开 issue 讨论，不夹在功能 PR 里 |
