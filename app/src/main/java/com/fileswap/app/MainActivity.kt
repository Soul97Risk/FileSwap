package com.fileswap.app

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.withStyle
import androidx.graphics.shapes.Morph
import com.fileswap.app.ui.theme.ShizukuFileToolTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36 起安卓强制 edge-to-edge；显式开启让低版本观感一致。
        // 系统栏 insets 由 M3 Scaffold / LargeFlexibleTopAppBar / ModalBottomSheet 自行消费。
        enableEdgeToEdge()
        setContent {
            // 主题参数来自持久化设置：动态取色开关能即时生效
            val ui by vm.state.collectAsState()
            ShizukuFileToolTheme(dynamicColor = ui.dynamicColor) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    AppScreen(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.checkShizuku()
        // 从「所有文件访问」设置页返回后刷新本地引擎状态（纯本地检查，无副作用）
        vm.probeLocal()
    }
}

/** 待确认的替换请求（主按钮与预设卡共用） */
private data class ReplaceRequest(
    val from: String,
    val target: String,
    val backup: String,
    val sourcePath: String,
    val sourceName: String
)

/** 待确认的还原请求 */
private data class RestoreRequest(
    val from: String,
    val target: String,
    val backup: String
)

@Composable
fun AppScreen(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val android10LegacyUnavailable = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
        !Environment.isExternalStorageLegacy()
    val selectedEngineUnavailable = state.engineMode == EngineMode.LOCAL && android10LegacyUnavailable

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.importFile(context, it) }
    }

    // 本地引擎授权：安卓 11+ 请求「所有文件访问」；安卓 10 及以下请求旧存储权限。
    // Android 10 若未处于 legacy 模式，targetSdk 36 下申请 WRITE 不会启用传统全盘访问。
    val localPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.probeLocal() }
    val requestLocalAccess = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            }.onFailure {
                // 个别 ROM 没有带包名的 action，退到通用列表页
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
            !Environment.isExternalStorageLegacy()
        ) {
            Toast.makeText(
                context,
                "Android 10 的传统存储模式不可用，请改用 Root 或 Shizuku",
                Toast.LENGTH_LONG
            ).show()
        } else {
            localPermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        Unit
    }

    // SAF 目录授权：替换/还原失败且目标在 Android/data 下时由 VM 请求弹出；
    // 授权结果回到 onSafTreePicked 继续挂起的操作。
    // 初始位置直接开到目标目录（documentId primary:Android/data/...），少点几下
    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        vm.onSafTreePicked(uri)
    }
    LaunchedEffect(state.safPending) {
        val req = state.safPending
        if (req != null) {
            treePicker.launch(SafOps.initialTreeUri(req.target.substringBeforeLast('/')))
        }
    }

    // 结果触感：成功 Confirm / 失败 Reject（M3E 强调多模态反馈，不只靠视觉）
    val haptic = LocalHapticFeedback.current
    var seenLog by remember { mutableStateOf(state.log) }
    LaunchedEffect(state.log) {
        if (state.log !== seenLog) {
            seenLog = state.log
            // 触感只看用户级结果；DEBUG 过程细节不参与
            val last = state.log.lastOrNull { it.level != LogLevel.DEBUG }?.message.orEmpty()
            when {
                last.startsWith("❌") -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
                last.startsWith("✅") -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }

    // 日志复制：新版 Clipboard API 是 suspend，经 rememberCoroutineScope 提交
    val clipboard = LocalClipboard.current
    val copyScope = rememberCoroutineScope()
    val logTimeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var pendingReplace by remember { mutableStateOf<ReplaceRequest?>(null) }
    var pendingRestore by remember { mutableStateOf<RestoreRequest?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showPermissionSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        // 主操作吸底：不用滚到页面底部才能点；忙碌进度与「还差什么」提示也放这里
        bottomBar = {
            ActionBar(
                state = state,
                engineUnavailable = selectedEngineUnavailable,
                onReplace = {
                    pendingReplace = ReplaceRequest(
                        from = "当前配置",
                        target = state.targetPath,
                        backup = state.backupPath,
                        sourcePath = state.importedLocalPath ?: "",
                        sourceName = state.importedFileName ?: ""
                    )
                },
                onRestore = {
                    pendingRestore = RestoreRequest(
                        from = "当前配置",
                        target = state.targetPath,
                        backup = state.backupPath
                    )
                }
            )
        }
    ) { padding ->
        // 大屏保持表单可读宽度，主操作栏仍使用全宽布局。
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
            EngineStatusCard(
                engineMode = state.engineMode,
                shizukuGranted = state.shizukuGranted,
                shizukuUid = state.shizukuUid,
                rootReady = state.rootReady,
                localReady = state.localReady,
                onClick = {
                    vm.probeEngines()
                    showPermissionSheet = true
                }
            )

            // 局部化捕获：内容 lambda 捕获稳定的 presets 引用而非整个 state，
            // 避免无关状态变化（如日志追加）重跑 LazyRow 内容
            val presets = state.presets
            val presetItemFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val presetItemPlacement = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()

            AnimatedVisibility(
                visible = presets.isNotEmpty(),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader("快捷方式")
                    Spacer(Modifier.height(8.dp))
                    // 吸附滚动：每次停在卡片起点，下一张露边提示可继续滑动
                    val railState = rememberLazyListState()
                    LazyRow(
                        state = railState,
                        flingBehavior = rememberSnapFlingBehavior(railState),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(presets, key = { it.id }) { preset ->
                            // 事件回调随 preset 记住，避免滚动/重组时每张卡
                            // 重复创建 4 个新 lambda 与新的 Modifier 临时链。
                            val onReplace = remember(preset) {
                                {
                                    pendingReplace = ReplaceRequest(
                                        from = "「${preset.name}」",
                                        target = preset.targetPath,
                                        backup = preset.backupPath,
                                        sourcePath = preset.sourcePath,
                                        sourceName = preset.sourceName
                                    )
                                }
                            }
                            val onRestore = remember(preset) {
                                {
                                    pendingRestore = RestoreRequest(
                                        from = "「${preset.name}」",
                                        target = preset.targetPath,
                                        backup = preset.backupPath
                                    )
                                }
                            }
                            QuickPresetCard(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = presetItemFade,
                                    placementSpec = presetItemPlacement,
                                    fadeOutSpec = presetItemFade
                                ),
                                preset = preset,
                                enabled = !state.busy && !selectedEngineUnavailable,
                                onReplace = onReplace,
                                onRestore = onRestore,
                                onLoad = { vm.loadPresetToForm(preset) },
                                onRebind = { vm.rebindPresetSource(preset.id) },
                                onDelete = { vm.deleteQuickPreset(preset.id) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            ConfigSection(
                busy = state.busy,
                importedFileName = state.importedFileName,
                targetPath = state.targetPath,
                backupPath = state.backupPath,
                targetLastModified = state.targetLastModified,
                renameToTarget = state.renameToTarget,
                dateFormat = dateFormat,
                onBrowse = { vm.openBrowser() },
                onUseSystemPicker = { pickFileLauncher.launch(arrayOf("*/*")) },
                onTargetChange = { vm.setTargetPath(it) },
                onBackupChange = { vm.setBackupPath(it) },
                onRenameToggle = { vm.setRenameToTarget(it) }
            )

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showSaveDialog = true },
                enabled = state.canReplace,
                shapes = appButtonShapes(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("保存为快捷方式")
            }

            Spacer(Modifier.height(20.dp))
            // remember 快照：仅在日志变化时重切 50 条，避免无关重组反复分配
            val recentLog = remember(state.log) { state.log.takeLast(50).asReversed() }
            LogSection(
                log = recentLog,
                timeFormat = logTimeFormat,
                canCopy = state.log.isNotEmpty(),
                onCopy = {
                    // 导出完整日志（含版本 / 设备 / 引擎上下文），出问题直接粘给开发者
                    val text = buildLogExport(state, logTimeFormat)
                    copyScope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("FileSwap 日志", text))
                        )
                    }
                    Toast.makeText(context, "已复制 ${state.log.size} 条日志", Toast.LENGTH_SHORT).show()
                }
            )
            Spacer(Modifier.height(20.dp))
            
            // 外观设置卡片组（仿 aShellYou 风格）
            Column(Modifier.fillMaxWidth()) {
                SectionHeader("外观")
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        AppearanceSetting(
                            dynamicColor = state.dynamicColor,
                            onToggle = { vm.setDynamicColor(it) }
                        )
                        // 未来的其他外观设置（字体大小、动画速度）可以加在这里
                    }
                }
            }
            
                Spacer(Modifier.height(16.dp))
                AuthorFooter()
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // 应用内文件选择器
    state.browser?.let { browser ->
        FileBrowserSheet(
            browser = browser,
            dateFormat = dateFormat,
            onDismiss = { vm.closeBrowser() },
            onNavigate = { vm.browseTo(it) },
            onUp = { vm.browseUp() },
            onImport = { vm.importFromBrowser(it) },
            onUseSystemPicker = {
                vm.closeBrowser()
                pickFileLauncher.launch(arrayOf("*/*"))
            },
            onSortBy = { vm.setSortBy(it) },
            onToggleSortDir = { vm.toggleSortDir() }
        )
    }

    pendingReplace?.let { req ->
        AlertDialog(
            onDismissRequest = { pendingReplace = null },
            title = { Text("确认替换${req.from}？") },
            text = {
                Text(
                    "将用「${req.sourceName}」替换：\n${req.target}\n\n" +
                        "原文件先备份到：\n${req.backup}\n（含原修改时间，可用于一键还原）"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingReplace = null
                    vm.quickReplace(req.target, req.backup, req.sourcePath, req.sourceName)
                }) { Text("替换") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplace = null }) { Text("取消") }
            }
        )
    }

    pendingRestore?.let { req ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("确认还原${req.from}？") },
            text = {
                Text(
                    "将用备份覆盖：\n${req.target}\n\n备份来源：\n${req.backup}\n（时间戳一并还原）"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    vm.quickRestore(req.target, req.backup)
                }) { Text("还原") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("取消") }
            }
        )
    }

    if (showSaveDialog) {
        SavePresetDialog(
            defaultName = state.importedFileName ?: "",
            onConfirm = { name ->
                showSaveDialog = false
                vm.saveQuickPreset(name)
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showPermissionSheet) {
        PermissionSheet(
            engineMode = state.engineMode,
            shizukuGranted = state.shizukuGranted,
            rootReady = state.rootReady,
            rootProbing = state.rootProbing,
            localReady = state.localReady,
            android10LegacyUnavailable = android10LegacyUnavailable,
            onDismiss = { showPermissionSheet = false },
            onSelect = { vm.setEngineMode(it) },
            onRequestShizuku = { vm.requestShizukuPermission() },
            onProbeRoot = { vm.probeRoot() },
            onRequestLocal = requestLocalAccess
        )
    }
}

// ---------------------------------------------------------------------------
// 区块组件
// ---------------------------------------------------------------------------

/** 分组标题（aShellYou 式）：labelLarge + primary，比卡片内标题低一级 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 4.dp)
            .semantics { heading() }
    )
}

/** 权限状态卡（KSU 式）：工作状态 + 当前引擎标签 + 可用性汇总，点按打开权限抽屉 */
@Composable
private fun EngineStatusCard(
    engineMode: EngineMode,
    shizukuGranted: Boolean,
    shizukuUid: Int,
    rootReady: Boolean?,
    localReady: Boolean?,
    onClick: () -> Unit
) {
    val hasPermission = shizukuGranted || rootReady == true || localReady == true
    // 容器/前景色随授权状态过渡（effects 档：颜色类动画，M3E 规范）
    val containerColor by animateColorAsState(
        targetValue = if (hasPermission) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "statusContainer"
    )
    val onContainer by animateColorAsState(
        targetValue = if (hasPermission) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onErrorContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "statusOnContainer"
    )
    // 引擎 tag 底色（errorContainer ↔ surface）同走 effects 档
    val tagColor by animateColorAsState(
        targetValue = if (hasPermission) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "statusTag"
    )
    // M3E 形状 Morph：授权态为 Sunny，未授权 morph 到 Ghostish（spatial 档）
    val statusMorph = remember { Morph(MaterialShapes.Sunny, MaterialShapes.Ghostish) }
    val morphProgress by animateFloatAsState(
        targetValue = if (hasPermission) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "statusShapeMorph"
    )
    val morphShape = remember(statusMorph, morphProgress) {
        statusMorph.toMorphShape(morphProgress)
    }
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .semantics {
                stateDescription = if (hasPermission) "已就绪" else "需要授权"
            }
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 形状容器随授权态 morph；✓↔⚠ 图标以 Crossfade 过渡（effects 档）
            Surface(
                shape = morphShape,
                color = onContainer.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Crossfade(
                        targetState = hasPermission,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "statusIcon"
                    ) { granted ->
                        Icon(
                            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = onContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (hasPermission) "工作中" else "未授权",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    // 内联引擎标签（对标 KSU 的「Root 模式」tag；按 M3 chip 规格用 small 圆角）
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = tagColor
                    ) {
                        Text(
                            engineMode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasPermission) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                val ready = buildList {
                    if (rootReady == true) add("Root 已就绪")
                    if (shizukuGranted) add(
                        // 显示 Shizuku 的启动身份：root 与 shell(ADB) 的文件权限差别很大
                        when (shizukuUid) {
                            0 -> "Shizuku 已授权（root）"
                            2000 -> "Shizuku 已授权（shell / ADB）"
                            else -> "Shizuku 已授权"
                        }
                    )
                    if (localReady == true) add("本地存储可用")
                }
                Text(
                    when {
                        ready.isNotEmpty() -> ready.joinToString(" · ")
                        rootReady == false -> "尚未获得任何权限"
                        else -> "点按选择权限来源"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "选择权限",
                tint = onContainer
            )
        }
    }
}

@Composable
private fun QuickPresetCard(
    modifier: Modifier = Modifier,
    preset: QuickPreset,
    enabled: Boolean,
    onReplace: () -> Unit,
    onRestore: () -> Unit,
    onLoad: () -> Unit,
    onRebind: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = modifier.width(264.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // expressive 形状容器（预设卡的身份标识，与状态卡 Morph、
                    // 空态 Ghostish 同属一套形状语言）
                    Surface(
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        preset.targetPath.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("加载到下方表单") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null)
                            },
                            onClick = { menuOpen = false; onLoad() }
                        )
                        DropdownMenuItem(
                            text = { Text("替换源设为当前导入文件") },
                            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                            onClick = { menuOpen = false; onRebind() }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    preset.sourceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            // 卡内紧凑按钮：形状取主题 medium/small 档（M3E 默认是 CornerFull 全圆），
            // 尺寸/图标用 Small 档 token；busy 时禁用，避免并发触发引擎重复绑定
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onReplace,
                    enabled = enabled,
                    shapes = appButtonShapes(),
                    contentPadding = ButtonDefaults.SmallContentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.SmallIconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("替换")
                }
                FilledTonalButton(
                    onClick = onRestore,
                    enabled = enabled,
                    shapes = appButtonShapes(),
                    contentPadding = ButtonDefaults.SmallContentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.SmallIconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("还原")
                }
            }
        }
    }
}

@Composable
private fun ConfigSection(
    busy: Boolean,
    importedFileName: String?,
    targetPath: String,
    backupPath: String,
    targetLastModified: Long?,
    renameToTarget: Boolean,
    dateFormat: SimpleDateFormat,
    onBrowse: () -> Unit,
    onUseSystemPicker: () -> Unit,
    onTargetChange: (String) -> Unit,
    onBackupChange: (String) -> Unit,
    onRenameToggle: (Boolean) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "配置",
                style = MaterialTheme.typography.titleMediumEmphasized,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(12.dp))
            // M3 层级：主 CTA（替换目标文件）是全屏唯一 filled primary，此入口降为 tonal
            FilledTonalButton(
                onClick = onBrowse,
                enabled = !busy,
                shapes = appButtonShapes(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    importedFileName ?: "浏览并选择要替换进目标的文件",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 提示行出现/消失走 AnimatedVisibility（表单推移由外层 animateContentSize 兜住）
            AnimatedVisibility(
                visible = importedFileName != null,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                Text(
                    "已导入到 App 私有目录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            // contentPadding 去掉起始内边距，让文字与上方按钮、下方输入框左对齐
            TextButton(
                onClick = onUseSystemPicker,
                enabled = !busy,
                contentPadding = PaddingValues(start = 4.dp, end = 12.dp)
            ) {
                Text("改用系统文件选择器", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(4.dp))
            PathTextField(
                value = targetPath,
                onValueChange = onTargetChange,
                label = "目标文件绝对路径",
                placeholder = "/sdcard/Download/xxx.json",
                // 说明文字放进输入框自己的 supportingText，不再悬在两个控件之间造成归属歧义
                supportingText = targetLastModified
                    ?.let { "目标当前修改时间：${dateFormat.format(Date(it))}" }
                    ?: "替换时自动读取并保持原修改时间",
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(8.dp))
            PathTextField(
                value = backupPath,
                onValueChange = onBackupChange,
                label = "备份路径",
                supportingText = "留空则备份为「目标.bak」",
                imeAction = ImeAction.Done
            )
            Spacer(Modifier.height(8.dp))
            // 导入文件强制改名同目标（开关持久化）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "导入文件改名同目标",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "导入时若已填目标路径，私有目录副本改用与目标相同的文件名",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = renameToTarget,
                    onCheckedChange = onRenameToggle
                )
            }
        }
    }
}

@Composable
private fun LogSection(
    log: List<LogEntry>,
    timeFormat: SimpleDateFormat,
    canCopy: Boolean,
    onCopy: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "日志",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                // 出问题一键复制完整日志（含版本 / 设备 / 引擎上下文），无需截图
                TextButton(
                    onClick = onCopy,
                    enabled = canCopy,
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("复制", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (log.isEmpty()) {
                // 空状态给一个图标 + 行动指引，而不只是一行灰字
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "还没有操作。导入文件、填好目标路径后即可一键替换",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 传入的是「最新在前」的列表；时间线：圆点 + 竖线，圆点颜色表示结果
                log.forEachIndexed { i, entry -> LogRow(entry, timeFormat, isLast = i == log.lastIndex) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, timeFormat: SimpleDateFormat, isLast: Boolean) {
    val cs = MaterialTheme.colorScheme
    val isDebug = entry.level == LogLevel.DEBUG
    val isError = entry.message.startsWith("❌")
    val dot = when {
        isDebug -> cs.outlineVariant
        isError -> cs.error
        entry.message.startsWith("⚠") -> cs.tertiary
        entry.message.startsWith("✅") || entry.message.startsWith("🔁") -> cs.primary
        else -> cs.outline
    }
    val time = remember(entry.time) { timeFormat.format(Date(entry.time)) }
    Row(Modifier.height(IntrinsicSize.Min)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(16.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(cs.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            // 时间戳弱化、DEBUG 行整体弱化、正文按结果着色，比整行单色更好扫读
            buildAnnotatedString {
                withStyle(SpanStyle(color = cs.onSurfaceVariant)) { append("$time  ") }
                withStyle(
                    SpanStyle(
                        color = when {
                            isDebug -> cs.onSurfaceVariant
                            isError -> cs.error
                            else -> cs.onSurface
                        }
                    )
                ) {
                    append(entry.message)
                }
            },
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            ),
            modifier = Modifier.padding(bottom = 10.dp)
        )
    }
}

/**
 * 构建用于问题上报的完整日志文本：
 * 头部带版本 / 设备 / 系统 / 引擎与三项权限状态，正文为全部日志（旧 → 新，含时间戳）。
 * 复制后直接粘贴给开发者即可定位问题，无需截图。
 */
private fun buildLogExport(state: UiState, timeFormat: SimpleDateFormat): String {
    val full = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val shizuku = if (state.shizukuGranted) {
        val v = state.shizukuServerVersion
        val app = state.shizukuAppVersion.ifBlank { "未检测到" }
        "已授权(uid=${state.shizukuUid}${if (v > 0) ", 协议v$v" else ""}, 应用$app)"
    } else {
        "未授权(应用${state.shizukuAppVersion.ifBlank { "未安装" }})"
    }
    val root = when (state.rootReady) {
        true -> "就绪"
        false -> "不可用"
        null -> "未探测"
    }
    val header = buildString {
        appendLine("FileSwap v${BuildConfig.VERSION_NAME} 日志导出")
        appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        appendLine("引擎：${state.engineMode.label}｜Shizuku=$shizuku｜Root=$root｜本地存储=${if (state.localReady == true) "已授权" else "未授权"}")
        appendLine("导出时间：${full.format(Date())}")
    }
    val body = if (state.log.isEmpty()) {
        "（无日志）"
    } else {
        // 全量导出（含 DEBUG 过程细节与诊断行），级别前缀方便过滤
        state.log.joinToString("\n") { e ->
            "${timeFormat.format(Date(e.time))} [${e.level}] ${e.message}"
        }
    }
    return "$header\n—— 最近 ${state.log.size} 条（旧 → 新，[DEBUG]=过程细节）————\n$body"
}

/**
 * 路径输入框：M3 Filled 样式（去掉底部指示线，圆角走主题档位），
 * Uri 键盘 + IME 动作，非空时出现清除按钮。
 */
@Composable
private fun PathTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        supportingText = if (supportingText != null) { { Text(supportingText) } } else null,
        trailingIcon = {
            AnimatedVisibility(
                visible = value.isNotEmpty(),
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
            ) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "清除")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 吸底操作栏：忙碌进度 + 一句话状态 + 「替换｜还原」连接按钮组。
 * 连接按钮组：相邻的一侧小圆角、外侧全圆角，按下时该按钮形变成全圆胶囊。
 */
@Composable
private fun ActionBar(
    state: UiState,
    engineUnavailable: Boolean,
    onReplace: () -> Unit,
    onRestore: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            AnimatedVisibility(
                visible = state.busy,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                Column(Modifier.fillMaxWidth()) {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        state.busyLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
            }
            // 提示文案切换走 AnimatedContent（spec 须在 transitionSpec 外取出）
            val hintFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            val actionHint = if (engineUnavailable) {
                "Android 10 的本地存储模式不可用，请切换到 Root 或 Shizuku"
            } else {
                state.actionHint
            }
            AnimatedContent(
                targetState = actionHint,
                transitionSpec = { fadeIn(hintFade) togetherWith fadeOut(hintFade) },
                label = "actionHint",
                modifier = Modifier.fillMaxWidth()
            ) { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.canReplace && !engineUnavailable) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Button(
                    onClick = onReplace,
                    enabled = state.canReplace && !engineUnavailable,
                    shapes = connectedButtonShapes(isStart = true),
                    modifier = Modifier
                        .weight(2f)
                        .height(ButtonDefaults.MediumContainerHeight)
                ) {
                    // 忙碌时图标位换成 M3E 形变加载指示器（多边形连续变形）
                    if (state.busy) {
                        LoadingIndicator(modifier = Modifier.size(ButtonDefaults.IconSize))
                    } else {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("替换")
                }
                FilledTonalButton(
                    onClick = onRestore,
                    enabled = state.canRestore && !engineUnavailable,
                    shapes = connectedButtonShapes(isStart = false),
                    modifier = Modifier
                        .weight(1f)
                        .height(ButtonDefaults.MediumContainerHeight)
                ) {
                    Icon(
                        Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("还原")
                }
            }
        }
    }
}

/** 连接按钮组的形状：静息时内侧 8dp、外侧全圆；按下变形为全圆胶囊 */
@Composable
private fun connectedButtonShapes(isStart: Boolean): ButtonShapes {
    return remember(isStart) {
        val full = CornerSize(50)
        val inner = CornerSize(8.dp)
        val rest = if (isStart) {
            RoundedCornerShape(topStart = full, topEnd = inner, bottomEnd = inner, bottomStart = full)
        } else {
            RoundedCornerShape(topStart = inner, topEnd = full, bottomEnd = full, bottomStart = inner)
        }
        ButtonShapes(shape = rest, pressedShape = RoundedCornerShape(50))
    }
}

// ---------------------------------------------------------------------------
// 应用内文件选择器
// ---------------------------------------------------------------------------

@Immutable
private sealed interface BrowserBranch {
    data object Loading : BrowserBranch
    data class Error(val message: String) : BrowserBranch
    data object Empty : BrowserBranch
    data class Loaded(val entries: List<DirEntry>) : BrowserBranch
}

/**
 * M3E 形状 Morph：把 graphics-shapes 的 [Morph] 在给定进度下的轮廓转成 Compose [Shape]。
 * graphics-shapes 只提供 forEachCubic 逐段回调，没有现成的 Morph→Shape 扩展，
 * 这里手写路径：逐段画三次贝塞尔，并把归一化坐标等比映射进目标尺寸居中。
 */
private fun Morph.toMorphShape(progress: Float): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val bounds = calculateBounds()
        val w = bounds[2] - bounds[0]
        val h = bounds[3] - bounds[1]
        // 边界退化时给出实心矩形兜底，避免 Surface 用空轮廓把内容整块剪掉
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        val s = minOf(size.width / w, size.height / h)
        val ox = (size.width - w * s) / 2f - bounds[0] * s
        val oy = (size.height - h * s) / 2f - bounds[1] * s
        fun mx(x: Float) = x * s + ox
        fun my(y: Float) = y * s + oy
        // forEachCubic 给出的是首尾相连的连续曲线段（每段 anchor0 = 上一段 anchor1）：
        // 只能 moveTo 一次，之后每段 cubicTo，最后 close。
        // 若每段都 moveTo，会得到互不相连的单段子路径——单条曲线不围合面积，
        // 填充为空 → 形状不可见且 Surface 会按轮廓把子内容（图标）一起剪掉。
        var started = false
        forEachCubic(progress) { c ->
            if (!started) {
                path.moveTo(mx(c.anchor0X), my(c.anchor0Y))
                started = true
            }
            path.cubicTo(
                mx(c.control0X), my(c.control0Y),
                mx(c.control1X), my(c.control1Y),
                mx(c.anchor1X), my(c.anchor1Y)
            )
        }
        if (!started) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * M3E 按压反馈：按下时轻微缩小（spatial 快档），松手回弹。
 * 用于整卡/整行可点组件，提供除 ripple 外的空间反馈。
 */
@Composable
private fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 动作按钮形状：静息 medium(12dp) / 按下 small(8dp)。
 *
 * 不要依赖 `ButtonDefaults.shape`：在 M3E 里它是
 * `ButtonSmallTokens.ContainerShapeRound` = `ShapeKeyTokens.CornerFull`，
 * 也就是**全圆胶囊**，与早先写死的 CircleShape 视觉完全一致——所以只删
 * `shape = CircleShape` 是看不出任何变化的。这里显式取主题形状档位，
 * 让圆角真正落在 4/8/12/16/28 这套 M3 形状尺度上，同时保留按下形变。
 */
@Composable
private fun appButtonShapes(): ButtonShapes {
    val shapes = MaterialTheme.shapes
    return remember(shapes) { ButtonShapes(shape = shapes.medium, pressedShape = shapes.small) }
}

/**
 * 选择器面板内容的弹簧入场：仅两个选择器（权限 / 文件）打开时使用。
 *
 * 两个面板此前用 `rememberBottomSheetState(SheetValue.Expanded)`，状态初始即为
 * 展开态——第一帧就在终点，于是完全没有入场动画（该初始值也不等价于
 * skipPartiallyExpanded，后者只影响锚点集合）。现在改为 Hidden 起始，
 * 面板本身有入场位移，再由这里叠加 bouncy 弹簧，做出「弹跳」的手感。
 */
@Composable
private fun Modifier.springEnter(): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    return this.graphicsLayer {
        // 过冲到 >1 时 translationY 变负，形成轻微上弹
        translationY = (1f - progress.value) * 48f
        alpha = progress.value.coerceIn(0f, 1f)
    }
}

private val quickLocations = listOf(
    "下载" to "/storage/emulated/0/Download",
    "QQ 接收" to "/storage/emulated/0/Android/data/com.tencent.mobileqq/Tencent/QQfile_recv",
    "微信下载" to "/storage/emulated/0/Android/data/com.tencent.mm/MicroMsg/Download",
    "文档" to "/storage/emulated/0/Documents",
    "DCIM" to "/storage/emulated/0/DCIM",
    "根目录" to "/storage/emulated/0"
)

private fun joinPath(dir: String, name: String): String =
    if (dir.endsWith("/")) dir + name else "$dir/$name"

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / 1073741824f)
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / 1048576f)
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

@Composable
private fun FileBrowserSheet(
    browser: FileBrowserState,
    dateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onUp: () -> Unit,
    onImport: (String) -> Unit,
    onUseSystemPicker: () -> Unit,
    onSortBy: (SortBy) -> Unit,
    onToggleSortDir: () -> Unit
) {
    // 初始值必须是 Hidden：状态第一帧若已在 Expanded，面板没有任何入场位移可播
    // （这正是之前「打开选择器没有弹簧动效」的原因——旧代码传的是 Expanded，
    //  它也不等价于 skipPartiallyExpanded，后者只是锚点集合）。
    // 显式限定锚点集合为 {Hidden, Expanded}：既保留入场动画，又避免停在半展开。
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    // 目录内过滤：换目录自动清空
    var query by remember(browser.dir) { mutableStateOf("") }
    // animateItem 的规格在容器级只取一次：items 复用同一实例，
    // 避免 每次 item 执行新建 Modifier+spec 导致行级重组无法跳过。
    // （MaterialTheme.motionScheme 是 composable 读取，不能放进 remember 块内）
    val motionScheme = MaterialTheme.motionScheme
    val itemFade = remember(motionScheme) { motionScheme.defaultEffectsSpec<Float>() }
    val itemPlacement = remember(motionScheme) { motionScheme.defaultSpatialSpec<IntOffset>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        // 会话内返回：有浏览历史时系统返回键先回上一级，栈空才关闭面板
        BackHandler(enabled = browser.stack.isNotEmpty()) { onUp() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .springEnter()
                .padding(horizontal = 16.dp)
        ) {
            // 头行：上一级 + 路径 + 排序 + 关闭
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onUp, enabled = browser.stack.isNotEmpty()) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "上一级")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        browser.dir.substringAfterLast('/').ifEmpty { "/" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 可点击面包屑：一步跳到任意上级，不必逐级点「上一级」
                    PathBreadcrumbs(browser.dir, onNavigate)
                }
                // 排序方式（文件夹始终排在文件前）
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序方式")
                    }
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        SortBy.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                trailingIcon = {
                                    if (browser.sortBy == mode) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    sortMenuOpen = false
                                    onSortBy(mode)
                                }
                            )
                        }
                    }
                }
                // 排序方向（图标 180° 旋转过渡，spatial 档）
                IconButton(onClick = onToggleSortDir) {
                    val rotation by animateFloatAsState(
                        targetValue = if (browser.sortAsc) 0f else 180f,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "sortDirRotation"
                    )
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = if (browser.sortAsc) "升序" else "降序",
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(4.dp))
            // 快速位置
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickLocations, key = { it.first }) { (label, path) ->
                    val icon = when (label) {
                        "下载" -> Icons.Filled.DownloadForOffline
                        "根目录" -> Icons.Filled.Folder
                        "DCIM" -> Icons.Filled.CameraAlt
                        else -> Icons.Filled.Folder
                    }
                    AssistChip(
                        onClick = { onNavigate(path) },
                        label = { Text(label) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }
            // 最近导入（记忆功能）：一键重新导入最近用过的替换源
            AnimatedVisibility(
                visible = browser.recentImports.isNotEmpty() && !browser.loading,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "最近导入",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(browser.recentImports, key = { it }) { path ->
                            AssistChip(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = itemFade,
                                    placementSpec = itemPlacement,
                                    fadeOutSpec = itemFade
                                ),
                                onClick = { pendingImport = path },
                                label = {
                                    Text(
                                        path.substringAfterLast('/'),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 四分支切换以 AnimatedContent 过渡（fadeIn/fadeOut default 档 + 自带 SizeTransform 兜高度）。
            // 分支用 sealed 载荷快照：过渡期间旧内容用的是它自己的数据，
            // 不会读到已变化的新状态（error 文本等）
            val branch = when {
                browser.loading -> BrowserBranch.Loading
                browser.error != null -> BrowserBranch.Error(browser.error)
                browser.entries.isEmpty() -> BrowserBranch.Empty
                else -> BrowserBranch.Loaded(browser.entries)
            }
            val branchEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = branch,
                transitionSpec = {
                    fadeIn(branchEffects) togetherWith
                        fadeOut(branchEffects)
                },
                label = "browserBranch",
                modifier = Modifier.weight(1f)
            ) { branchContent ->
                when (branchContent) {
                    BrowserBranch.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            // M3E 包裹式加载指示器：自带容器底，比裸的更有存在感
                            ContainedLoadingIndicator()
                        }
                    }
                    is BrowserBranch.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // 错误态图标装进圆形 errorContainer，与状态卡未授权态同语义
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(88.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                branchContent.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { onNavigate(browser.dir) }) { Text("重试") }
                        }
                    }
                    BrowserBranch.Empty -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // 空态图标装进 Ghostish 容器：与状态卡未授权态同形，
                            // 「空的」语义也刚好和 ghost 吻合
                            Surface(
                                shape = MaterialShapes.Ghostish.toShape(),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(88.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "此文件夹为空",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "换一个位置，或用上方快速位置跳转",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is BrowserBranch.Loaded -> {
                        // AnimatedContent 内容块是叠加布局，必须用 Column 顺序排列：
                        // 项数提示在上，列表占其余空间（Column 顺序测量自然给出剩余高度）
                        Column(modifier = Modifier.fillMaxSize()) {
                            val all = branchContent.entries
                            val shown = remember(all, query) {
                                if (query.isBlank()) all
                                else all.filter { it.name.contains(query, ignoreCase = true) }
                            }
                            // 条目多时才出现过滤框，少量文件不占空间
                            if (all.size > 8) {
                                BrowserSearchField(query = query, onChange = { query = it })
                                Spacer(Modifier.height(8.dp))
                            }
                            // 项数提示（大目录感知）
                            Text(
                                if (query.isBlank()) "共 ${all.size} 项" else "匹配 ${shown.size} / ${all.size} 项",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(
                                    shown,
                                    key = { "${it.isDir}:${it.name}" },
                                    contentType = { if (it.isDir) "dir" else "file" }
                                ) { entry ->
                                    BrowserEntryRow(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = itemFade,
                                            placementSpec = itemPlacement,
                                            fadeOutSpec = itemFade
                                        ),
                                        entry = entry,
                                        dateFormat = dateFormat,
                                        onClick = {
                                            if (entry.isDir) {
                                                onNavigate(joinPath(browser.dir, entry.name))
                                            } else {
                                                pendingImport = joinPath(browser.dir, entry.name)
                                            }
                                        }
                                    )
                                }
                                item {
                                    // SAF 兜底入口
                                    TextButton(onClick = onUseSystemPicker) {
                                        Text("找不到文件？使用系统文件选择器", style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    pendingImport?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入此文件？") },
            text = {
                Text("将复制到 App 私有目录作为替换源：\n$path")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    onImport(path)
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            }
        )
    }
}

/** 路径面包屑：末级高亮不可点，其余点击跳转；路径变化时自动滚到末尾 */
@Composable
private fun PathBreadcrumbs(dir: String, onNavigate: (String) -> Unit) {
    val parts = remember(dir) { dir.split('/').filter { it.isNotEmpty() } }
    val scroll = rememberScrollState()
    LaunchedEffect(dir, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier.horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (parts.isEmpty()) {
            Text(
                "/",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        parts.forEachIndexed { i, name ->
            if (i > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp)
                )
            }
            val last = i == parts.lastIndex
            val target = "/" + parts.take(i + 1).joinToString("/")
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                color = if (last) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = !last) { onNavigate(target) }
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            )
        }
    }
}

/** 目录内过滤框：全圆胶囊，与主表单同一套无指示线 Filled 风格 */
@Composable
private fun BrowserSearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("在当前文件夹中过滤") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
            ) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "清除过滤")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth()
    )
}

/** 文件类型 → (图标, 容器色, 图标色)，全部取自主题色板，随动态取色 / 深浅色自适应 */
@Composable
private fun entryStyle(entry: DirEntry): Triple<ImageVector, Color, Color> {
    val cs = MaterialTheme.colorScheme
    if (entry.isDir) return Triple(Icons.Filled.Folder, cs.primaryContainer, cs.onPrimaryContainer)
    return when (entry.name.substringAfterLast('.', "").lowercase()) {
        "unity3d", "bundle", "assetbundle", "obb" ->
            Triple(Icons.Filled.ViewInAr, cs.tertiaryContainer, cs.onTertiaryContainer)
        "apk", "xapk", "apks" ->
            Triple(Icons.Filled.Android, cs.tertiaryContainer, cs.onTertiaryContainer)
        "png", "jpg", "jpeg", "webp", "gif", "bmp" ->
            Triple(Icons.Filled.Photo, cs.secondaryContainer, cs.onSecondaryContainer)
        "zip", "rar", "7z", "tar", "gz" ->
            Triple(Icons.Filled.FolderZip, cs.secondaryContainer, cs.onSecondaryContainer)
        "json", "xml", "lua", "js", "ini", "cfg", "yml", "yaml" ->
            Triple(Icons.Filled.Code, cs.surfaceContainerHigh, cs.primary)
        "mp3", "ogg", "wav", "flac", "m4a" ->
            Triple(Icons.Filled.AudioFile, cs.secondaryContainer, cs.onSecondaryContainer)
        "mp4", "mkv", "webm", "mov" ->
            Triple(Icons.Filled.VideoFile, cs.secondaryContainer, cs.onSecondaryContainer)
        else -> Triple(Icons.Filled.Description, cs.surfaceContainerHigh, cs.secondary)
    }
}

@Composable
private fun BrowserEntryRow(
    modifier: Modifier = Modifier,
    entry: DirEntry,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
    ) {
        // 副标题（大小·日期）格式化随 entry 记忆，避免滚动/重组时逐行重复格式化
        val subtitle = remember(entry) {
            if (entry.isDir) "文件夹"
            else "${formatSize(entry.size)} · ${dateFormat.format(Date(entry.mtime))}"
        }
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 按文件类型区分图标与容器色：游戏资源(.unity3d)一眼可辨
            val (kindIcon, kindContainer, kindTint) = entryStyle(entry)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = kindContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = kindIcon,
                        contentDescription = null,
                        tint = kindTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.isDir) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 
 * 通用设置项（仿 aShellYou 风格）：圆形图标容器 + 标题/描述 + trailing 组件。
 * 用于卡片组内的统一视觉——背景透明，继承外层卡片色。
 */
@Composable
private fun SettingRow(
    title: String,
    description: String? = null,
    icon: ImageVector,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        interactionSource = interactionSource,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 圆形图标容器（仿 aShellYou）
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
                shadowElevation = 1.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing()
        }
    }
}

/** 外观设置：动态取色（壁纸取色）开关。关闭时使用品牌 M3 色板。 */
@Composable
private fun AppearanceSetting(dynamicColor: Boolean, onToggle: (Boolean) -> Unit) {
    SettingRow(
        title = "跟随壁纸取色",
        description = if (dynamicColor) "使用系统动态颜色（Material You）"
        else "使用品牌绿色板（#006C4C 种子）",
        icon = Icons.Rounded.Palette,
        trailing = {
            Switch(checked = dynamicColor, onCheckedChange = onToggle)
        }
    )
}

/** 作者标识：点击打开 Telegram 主页（触摸目标 ≥48dp 无障碍标准） */
@Composable
private fun AuthorFooter() {
    val context = LocalContext.current
    val url = stringResource(R.string.author_url)
    Text(
        text = stringResource(R.string.author_line),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            .padding(vertical = 16.dp)
    )
}

/** 权限选择抽屉：整宽单选卡列表 + 选中项详情卡（对标 KSU 激活页） */
@Composable
private fun PermissionSheet(
    engineMode: EngineMode,
    shizukuGranted: Boolean,
    rootReady: Boolean?,
    rootProbing: Boolean,
    localReady: Boolean?,
    android10LegacyUnavailable: Boolean,
    onDismiss: () -> Unit,
    onSelect: (EngineMode) -> Unit,
    onRequestShizuku: () -> Unit,
    onProbeRoot: () -> Unit,
    onRequestLocal: () -> Unit
) {
    // 初始值必须是 Hidden：状态第一帧若已在 Expanded，面板没有任何入场位移可播
    // （旧代码传 Expanded，等于完全没有入场动画；它也不等价于 skipPartiallyExpanded）。
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .springEnter()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "选择权限来源",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Root 与 Shizuku 可随时切换，二者授权其一即可使用",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            EngineMode.entries.forEach { mode ->
                EngineRadioCard(
                    label = mode.label,
                    selected = engineMode == mode,
                    enabled = mode != EngineMode.LOCAL || !android10LegacyUnavailable,
                    onClick = { onSelect(mode) }
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            // 选中项详情卡：状态 + 行内动作（分支切换以 AnimatedContent 过渡，高度 spring 自适应）
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
            ) {
                val engineDetailEffects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(
                    targetState = engineMode,
                    transitionSpec = {
                        fadeIn(engineDetailEffects) togetherWith
                            fadeOut(engineDetailEffects)
                    },
                    label = "engineDetail"
                ) { selected ->
                    Column(Modifier.padding(16.dp)) {
                        when (selected) {
                            EngineMode.AUTO -> {
                                DetailTitle("自动")
                                DetailText("Root 可用时优先使用 Root，否则自动回退 Shizuku。")
                            }
                            EngineMode.ROOT -> {
                                DetailTitle("Root（KSU / Magisk）")
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        val status = when (rootReady) {
                                            null -> "检测中…" to MaterialTheme.colorScheme.onSurfaceVariant
                                            true -> "已就绪" to MaterialTheme.colorScheme.primary
                                            false -> "未授权或 su 不可用" to MaterialTheme.colorScheme.error
                                        }
                                        DetailText(status.first, status.second)
                                    }
                                    AnimatedVisibility(
                                        visible = rootReady != true,
                                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                            expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                                        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                            shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec())
                                    ) {
                                        TextButton(onClick = onProbeRoot, enabled = !rootProbing) {
                                            Text(if (rootProbing) "检测中…" else "授权")
                                        }
                                    }
                                }
                            }
                            EngineMode.SHIZUKU -> {
                                DetailTitle("Shizuku")
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        if (shizukuGranted) {
                                            DetailText("已授权", MaterialTheme.colorScheme.primary)
                                        } else {
                                            DetailText("未授权", MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible = !shizukuGranted,
                                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                            expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                                        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                            shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec())
                                    ) {
                                        TextButton(onClick = onRequestShizuku) { Text("去授权") }
                                    }
                                }
                            }
                            // 本地存储：免 Shizuku/Root，应用自身权限直接操作共享存储。
                            // 能力边界随系统版本差异极大，明说而不是让用户试错
                            EngineMode.LOCAL -> {
                                DetailTitle("本地存储（免 Shizuku）")
                                DetailText(
                                    when {
                                        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && android10LegacyUnavailable ->
                                            "安卓 10（API 29）：本应用目标版本为 36，传统存储模式不可用。申请 WRITE 权限也无法开启全盘访问；请改用 Root 或 Shizuku。"
                                        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
                                            "安卓 10（API 29）：设备当前处于传统存储模式；仍需授予存储权限，且访问范围受系统和文件系统限制。"
                                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                                            "安卓 9 及以下：授予存储权限后可访问共享存储；具体范围仍取决于系统和文件系统。"
                                        else ->
                                            "安卓 11+：全文件访问可操作大部分共享存储，但 Android/data 与 Android/obb 受系统限制；SAF 也不能授权这两个目录。"
                                    }
                                )
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        val status = when {
                                            android10LegacyUnavailable ->
                                                "不可用：无法启用传统存储模式" to MaterialTheme.colorScheme.error
                                            localReady == true -> "已授权" to MaterialTheme.colorScheme.primary
                                            localReady == false -> "未授权" to MaterialTheme.colorScheme.error
                                            else -> "检测中…" to MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        DetailText(status.first, status.second)
                                    }
                                    AnimatedVisibility(
                                        visible = localReady != true && !android10LegacyUnavailable,
                                        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                            expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()),
                                        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                                            shrinkHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec())
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (!android10LegacyUnavailable) onRequestLocal()
                                            }
                                        ) { Text("去授权") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DetailText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun EngineRadioCard(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // 颜色属 effects 档：弹簧是用在「打开选择器」上的（见 Modifier.springEnter），
    // 颜色过渡用 bouncy 弹簧既不可见也不符合 M3E 规范
    val cardColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "engineRadioCard"
    )
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        color = cardColor,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SavePresetDialog(
    defaultName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存快捷方式") },
        text = {
            // 与主表单同一套 Filled 输入框（主题圆角 + Uri 键盘 + 清除按钮），
            // 不再对话框用 Outlined、主表单用 Filled 两套皮
            PathTextField(
                value = name,
                onValueChange = { name = it },
                label = "名称",
                imeAction = ImeAction.Done
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
