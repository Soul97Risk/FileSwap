package com.fileswap.app;

import android.os.ParcelFileDescriptor;

// Root 引擎的远程文件操作服务，由 libsu 以 root UID (0) 在独立进程中运行。
// 与 Shizuku 引擎的 IFileService 方法一致（去掉 destroy：libsu 自管生命周期），
// 两边共享 FileOpsImpl 的同一份实现，保证行为一致。
interface IRootFileService {

    // 判断路径是否存在
    boolean exists(String path);

    // 获取文件最后修改时间（毫秒时间戳），失败返回 -1
    long getLastModified(String path);

    // 设置文件最后修改时间（毫秒时间戳）
    boolean setLastModified(String path, long timeMillis);

    // 将 srcPath 复制为 dstPath（覆盖）。
    // 成功返回 null；失败返回可读原因（异常类型 + 系统错误文本）
    @nullable String copyFile(String srcPath, String dstPath);

    // 以只读方式打开文件，把 fd 交回应用进程（用途同 IFileService.openRead）
    @nullable ParcelFileDescriptor openRead(String path);

    // 列出目录内容：编码规则同 IFileService.listDir
    String[] listDir(String path);

    // 由应用进程打开源文件并把 fd 传进来，服务只负责写入目标路径。
    // 替换源通常在应用私有目录，shell(2000) 读不了，只能走 fd 传递。
    // 成功返回 null；失败返回可读原因（含服务 uid）。
    @nullable String copyFromFd(in ParcelFileDescriptor src, String dstPath);

    // 诊断：以服务身份尝试读取 path，返回人类可读结果（含 uid / 各级权限探测）。
    String diagnoseRead(String path);
}
