# 更新记录

## 1.5 — versionCode 6

### Shizuku：shell 身份下的导入 / 替换修复

**根因**：Shizuku 以 root 启动时服务是 `uid=0`，什么都读得了；以 ADB / 无线调试启动时是 `shell(2000)`，
读不了应用私有目录 `/data/user/0/<pkg>/`。此前替换源放在私有目录，一旦用户用 ADB 启动 Shizuku 就必然失败。

- 新增 `copyFromFd(fd, dst)`：应用自己打开替换源，把 fd 交给服务写入目标
- 新增 `diagnoseRead(path)`：导入失败时日志带出具体原因
- 所有服务端错误信息附带执行身份 `uid`
- 「源文件不存在」区分「真的不存在」与「无权访问」，后者额外给出最近一个可见的上级目录
- `listDir`：单个条目 `readAttributes` 失败只跳过该条目，不再让整个目录显示成「无权读取」
- `UserService` version 3 → 4（AIDL 有变动必须递增，否则 Shizuku 会继续复用旧进程，新方法直接 `NoSuchMethod`）
- 新增 `serverUid()`；授权后若检测到 `uid=2000`，日志里提示能力受限

### Shizuku：服务端 shell 通道

- 新增 `ShizukuShell`：经 `IShizukuService.newProcess` 在 Shizuku 服务端上下文执行 `stat` / `cp` / `touch`。
  部分 ROM 上 UserService 子进程遍历其他应用 `Android/data` 会 `EACCES`，服务端通道可以；UserService 失败时自动落到这里
- 路径一律经 POSIX 单引号转义后拼进命令
- stdout 与 stderr 都读：`stat` / `cp` 的报错走 stderr，只读 stdout 会把失败原因吞成空串

### 绑定可靠性

- 绑定超时 20 秒 → 90 秒（vivo 安卓 11 上 UserService 冷启动实测约 58 秒）
- 在途绑定改为共享 `CompletableDeferred`：后到的调用挂到同一个信号上，服务上线一起成功，
  不再出现「5 连超时 + 16 个调用排队」
- 拿到 binder 后再 `pingBinder()` 一次，防止已完成的 deferred 交回一个已死的 binder
- `onServiceDisconnected` 同时清掉在途信号

### Root：su 探测静默降级修复

- libsu 的 su 探测会**静默降级**：首次握手失败/超时（授权弹窗没来得及确认）会缓存一个非 root 主 shell，
  之后即使在管理器里授权，缓存也不会重建，`Shell.getShell()` 永远是非 root，
  `RootService.bind` 在 confirmed-non-root 时静默返回、连接回调永远不来
- 解法：`Shell.Builder.create().build()` 显式重建，build 成功会 `setCached`，拿到 root 即自愈，无需重启应用
- `FLAG_MOUNT_MASTER` 进 Magisk 全局命名空间；su 不支持时 libsu 自动回退普通 su
- 握手超时放宽到 30 秒，给 KSU / Magisk 授权弹窗留确认时间

### Material 3 Expressive

- 主操作吸底：`Scaffold.bottomBar` 放「替换｜还原」连接按钮组（内侧 8dp、外侧全圆，按下变全圆胶囊）；
  忙碌进度条也移到这里
- 一句话状态提示（`UiState.actionHint`）：告诉用户还差什么，而不是只把按钮置灰
- 启用条件统一派生：`UiState.canReplace` / `canRestore`，替换按钮与「保存快捷方式」共用同一份条件
- 输入框：Filled 样式、URI 键盘 + IME 动作、非空显示清除按钮；「保持原修改时间」说明并入目标路径输入框的 supportingText
- 「改用系统文件选择器」文字按钮左对齐
- 日志改为时间线：最新在前，圆点颜色表示结果，空状态带图标
- 状态卡副标题显示 Shizuku 启动身份：`root` / `shell(ADB)`

### 文件选择器

- 路径改为可点击面包屑，自动滚到末级，一步跳到任意上级
- 目录内过滤框（条目 > 8 时出现，全圆胶囊），项数提示同步显示「匹配 x / y」
- 按文件类型区分图标与容器色（`unity3d` / `obb` → tertiary，图片 / 压缩包 / 音视频 → secondary，代码 / 配置 → 中性）
- 记忆上次浏览目录、最近 8 个导入、排序方式与方向
- 快捷方式轮播：`rememberSnapFlingBehavior` 吸附滚动，下一张露边

### 形态与触感

- 形状档位补全 `largeIncreased(20)` / `extraLargeIncreased(32)` / `extraExtraLarge(48)`；
  状态卡、快捷方式卡、配置卡、日志卡、外观卡用 `extraLargeIncreased`，文件列表行用 `largeIncreased`
- 操作成功 `HapticFeedbackType.Confirm`，失败 `Reject`（由日志结果触发）
- 替换按钮忙碌态：图标位换成多边形形变 `LoadingIndicator`
