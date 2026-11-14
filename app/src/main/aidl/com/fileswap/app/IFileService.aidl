package com.fileswap.app;

import android.os.ParcelFileDescriptor;

// 运行在 Shizuku 提供的 shell UID（2000）进程中，拥有比普通 App 更高的文件访问权限。
//
// 关键约束（AOSP SELinux 取证结论，安卓 12~16 一致）：
// shell 域**从未**获得 app_data_file 的访问权限，即本服务无权写
// /data/user/0/<pkg>/ 这类应用私有目录。因此「把文件复制进应用私有目录」
// 不能交给本服务完成，必须由 openRead() 把源文件 fd 交回应用进程，
// 由应用用自己的 UID 落盘。
interface IFileService {

    // 判断路径是否存在
    boolean exists(String path);

    // 获取文件最后修改时间（毫秒时间戳），失败返回 -1
    long getLastModified(String path);

    // 设置文件最后修改时间（毫秒时间戳）
    boolean setLastModified(String path, long timeMillis);

    // 将 srcPath 复制为 dstPath（覆盖）。
    // 成功返回 null；失败返回可读原因（异常类型 + 系统错误文本），便于真机定位。
    @nullable String copyFile(String srcPath, String dstPath);

    // 以只读方式打开文件，把 fd 交回应用进程。
    // 打开动作发生在本服务进程（权限按 shell/root 身份检查），之后的读写
    // 由应用通过 fd 完成 —— 即便目标是应用私有目录也合法。
    @nullable ParcelFileDescriptor openRead(String path);

    // 列出目录内容（应用内文件选择器用）。
    // 返回编码条目："d|名称"（子目录）或 "f|名称|大小字节|修改时间毫秒"（文件）。
    // 目录不存在、不是目录或 shell 无权读取时返回 null。调用方按「最后两段是
    // 大小和时间」解析，文件名里即使含 | 也不会解析错位。
    String[] listDir(String path);

    // 结束该远程 UserService 进程
    void destroy();

    // 由应用进程打开源文件并把 fd 传进来，服务只负责写入目标路径。
    // 替换源通常在应用私有目录，shell(2000) 读不了，只能走 fd 传递。
    // 成功返回 null；失败返回可读原因（含服务 uid）。
    @nullable String copyFromFd(in ParcelFileDescriptor src, String dstPath);

    // 诊断：以服务身份尝试读取 path，返回人类可读结果（含 uid / 各级权限探测）。
    String diagnoseRead(String path);
}
