## 这个 PR 做了什么

<!-- 一句话。为什么改，比改了什么更重要 -->

## 关联

<!-- Closes #123 / 无 -->

## 改动类型

- [ ] 修复缺陷
- [ ] 新功能
- [ ] 重构（不改变行为）
- [ ] 文档 / 构建
- [ ] 改动 AIDL 契约

## 验证

- [ ] `./gradlew :app:assembleDebug` 通过
- [ ] 在真机上验证过（写明机型 / ROM / Android 版本 / 引擎）

真机验证记录：

```
机型:
ROM:
Android:
引擎:
```

## 检查

- [ ] 改过 AIDL → 已递增 `ShizukuManager` 里的 `version(n)`，并同步了 `FileOpsImpl` / `FileOps` 三处适配器
- [ ] 新文件带 SPDX 头（`SPDX-License-Identifier: Apache-2.0`）
- [ ] 没有提交 `local.properties`、签名材料、密钥、`*.apk`
- [ ] 面对用户的错误信息里带得出执行身份 `uid` 与最近可见上级目录
- [ ] 改过依赖 → 已重跑 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 并更新 `THIRD-PARTY-NOTICES.md` §5
- [ ] 面向用户的字符串进了 `strings.xml`

## 备注

<!-- 未验证的部分、留待后续的、已知的取舍，写在这里而不是藏在代码注释里 -->
