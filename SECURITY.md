# 安全策略

## 支持范围

| 版本 | 状态 |
|---|---|
| 1.5（`versionCode 6`） | 接收报告 |
| 更早的版本 | 请在最新版上复现后再报 |

## 怎么报

**首选** [GitHub 私有安全公告](https://github.com/Soul97Risk/FileSwap/security/advisories/new)，
其次 Telegram [@kndnb666](https://t.me/kndnb666)。

**不要**开公开 issue 贴可利用细节 —— 这个项目的用户大多不具备自行判断风险的能力。

报告里请带上：

- 应用版本（设置页可见）与 `versionCode`
- 执行引擎（自动 / Root / Shizuku / 本地）与 Shizuku 的启动方式（root 还是 ADB）
- 机型、ROM、Android 版本
- 复现步骤，以及导出的日志（含 `uid` 与失败原因）

收到后我会先复现，再确认影响面，最后和你商量披露时间。不要在没有沟通的情况下先行公开。

## 哪些算问题

- **命令注入** —— `ShizukuShell` 用单引号转义拼命令。任何能让路径里的 `'` 逃逸出引号、
  或让 `exec()` 收到非预期脚本的输入，都算严重问题
- **越权文件写** —— 应用以 `shell(2000)` 或 `root(0)` 身份写文件。任何能让写入路径脱离用户显式选择的方式
  （Intent 注入、`exported` 组件被第三方拉起、`SafPending` 状态被伪造）都算
- **路径穿越** —— 导入源、目标、备份三个路径都由用户输入。任何绕过、任何把相对路径解析到预期之外的行为都算
- **数据泄漏** —— 备份文件、日志、快捷预设里可能含用户路径。离开应用沙箱即算
- **zip 路径穿越 / 解压逃逸** —— 目前不处理压缩包；一旦加入相关能力，这条自动生效
- **权限声明回退** —— 比如引入 `INTERNET`、放宽 `exported`、去掉 `allowBackup="false"`

## 哪些不算

- **需要已经在 Magisk / KernelSU 里给过 Root 才能造成的破坏。** 那种身份本来就有全权，
  不是本应用引入的风险
- **Shizuku / libsu 上游自身的问题** —— 请报到 [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API/issues)
  或 [topjohnwu/libsu](https://github.com/topjohnwu/libsu/issues)。如果问题出在本项目对它们的使用方式上，
  那算本项目的
- **替换文件导致某个游戏崩溃、封号、存档损坏** —— 这是使用行为的结果，不是漏洞。
  详见 README 的「已知限制」
- **仅凭 `adb shell` 就能做到的事** —— `shell` 身份的能力边界由 Android 定义，不由本项目定义
