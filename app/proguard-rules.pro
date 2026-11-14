# ============================================================================
# R8 只做「压缩」：不混淆、不优化
# ============================================================================
# 两条全局指令，作用域是整个 App（含所有依赖库）：
#
#   -dontobfuscate  不改类名 / 方法名 / 字段名
#   -dontoptimize   不做方法内联、类合并、常量折叠等改写
#
# 为什么两条都要：
#   只加 -dontobfuscate 时，R8 的优化阶段仍会「打乱」结构——实测发现它会
#   把 MainViewModel 的业务方法（readTargetLastModified / replaceTargetFile /
#   backupTargetFile 等 12 个）全部内联进调用点并从类里删掉，还会合并类。
#   结果就是反查 APK 时这些功能方法在包里"消失"了，与源码结构对不上。
#
# 关掉后仍然生效：删除无引用代码（shrinking）+ 资源压缩（isShrinkResources）。
# 代价：体积比开优化时大一些，但结构保持与源码一一对应，可读可核对。
-dontobfuscate
-dontoptimize

# 保留行号调试信息（实测有效，stack trace 里能看到正确的行号）。
# 说明：SourceFile 属性本身会被 R8 替换成 "r8-map-id-<hash>" 而不是真实文件名，
# 这是 R8 的既定设计——用该 id 校验堆栈与 mapping 文件是否配套，无标准规则可关闭。
# 因已关闭重命名，类名/方法名都是真实名字，配合行号足以定位问题。
-keepattributes SourceFile,LineNumberTable

# ============================================================================
# 远程服务契约（Shizuku UserService + libsu RootService）—— 必须保留
# ============================================================================
# 注意：上面关掉的是「重命名」和「优化改写」，不是「删除」。
# 下面这些规则防的是 shrinking：FileService / RootFileService 的实例化
# 都发生在我们自己代码之外（反射创建）：
#   - FileService：Shizuku 服务端在 shell 进程里 Class.forName + 无参构造
#   - RootFileService：libsu 在 root 进程里反射实例化
# R8 看不到这些引用，会把构造函数当无用成员删掉，运行时抛
# NoSuchMethodException / InstantiationException。
#
# 库方防护情况：dev.rikka.shizuku 的 api / aidl / shared 三个 AAR 的
# proguard.txt 均为 0 字节；libsu 自带 consumer 规则，但管不到我们自己的服务类。
-keep class com.fileswap.app.FileService {
    public <init>();
}
-keep class com.fileswap.app.RootFileService {
    public <init>();
}

# AIDL 生成的 Binder 接口跨进程（App 进程 ↔ 远程服务进程）使用，
# 保留接口与 Stub/Proxy 的全部成员，避免 Binder 分发链路被改写。
-keep interface com.fileswap.app.IFileService { *; }
-keep class com.fileswap.app.IFileService$* { *; }
-keep interface com.fileswap.app.IRootFileService { *; }
-keep class com.fileswap.app.IRootFileService$* { *; }
