# Android China Setup v0.6.0 — 固定签名正式版

此次更新让应用目录与 APK 版本**解耦**：以后新增、调整、下架应用，只需提交在线 JSON 清单并经过自动校验，不必为了应用名单变化重新编译、签名和安装 APK。

## 本次更新

- 新增 [Canta](https://github.com/samolego/Canta)（配合 Shizuku 管理预装应用）和 [Spotube](https://github.com/team-spotube/spotube)（开源音乐播放器），当前收录 **17 个项目**。
- **设置 → 应用目录 → 刷新应用目录**：立即获取最新目录；启动时按约 12 小时间隔尝试后台同步。
- **离线兜底与校验**：GitHub 在线目录不可达或内容无效时，继续使用已验证的本地缓存或内置目录；只接受合法 HTTPS 来源与受控下载字段。
- 新增 `tools/catalog.py add / validate` 快捷维护工具。仅修改 `catalog/apps.json` 时，CI 只执行快速目录校验，**不启动 Gradle 或 Android 模拟器**。
- 保留 Material 3 分类应用展示、离线 README 快照、下载源测速、HTTP/SOCKS5 代理和应用内版本检查。

## 安装与更新

- **最低 Android 11（API 30）**。
- 使用与 v0.5.0 相同的**固定签名证书**，使现有 v0.5.0 用户能够在系统安装确认后覆盖升级。
- 官方 APK 文件为 `Android-China-Setup.apk`，同时提供 `SHA256SUMS.txt` 校验文件。
- Android 11 与 Android 15 自动化回归测试各 **53 项检查通过**；发布工作流另外验证正式签名 APK 的身份与模拟器安装/核心页面操作。
- v0.3 / v0.4 采用过临时 Debug 签名，可能无法直接覆盖 v0.6.0，需先按提示处理旧版。

## 风险与限制

- Canta 删除错误的系统组件可能造成系统异常，建议先备份并确认 Shizuku 授权。
- DirtyFrag、KSuRoot、GhostLock 均为高风险项目，只显示原项目链接与 README 摘要，不自动运行提权程序。
- 在线目录依赖 GitHub 连通性；在中国大陆无法获取时使用离线缓存，不影响本地目录浏览。
- 尚未完成中国大陆所有运营商/镜像的长期可用性、ARM64 真机掉帧和全部第三方安装包逐项验证。

感谢所有被收录项目的开发者。欢迎通过 [Issues](https://github.com/U0Axuan/android-china-setup/issues/new) 推荐新的 Android 开源应用。

[在线目录](https://github.com/U0Axuan/android-china-setup/blob/main/catalog/apps.json) · [快速维护文档](https://github.com/U0Axuan/android-china-setup/blob/main/docs/CATALOG.md) · [GitHub Actions](https://github.com/U0Axuan/android-china-setup/actions)
