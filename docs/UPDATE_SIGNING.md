# 稳定签名与应用内自更新

## 为什么需要固定签名

Android 安全机制要求**同一包名的后续版本由兼容签名证书签名**。v0.3.0 首次 GitHub Release 采用 CI 自动生成的 Debug 测试签名，后续不同 GitHub Runner 上生成的 Debug APK 通常不能覆盖安装。若改用新的正式签名，v0.3.0 用户仍需先卸载测试包再安装一次正式包，之后同签名的版本才可顺利更新。

应用内“关于 → 检查更新 → 下载并安装更新”已经提供 GitHub Release 检查、HTTPS APK 下载、SHA-256 校验与系统安装确认。**安装前检查签名不相容时拒绝覆盖**；不会绕过 Android 的安全限制或静默安装。

## 启用稳定签名（管理员操作）

1. 在安全的离线或本地环境中生成 Android JKS 签名密钥，妥善备份，不要提交到公开 GitHub 仓库。
2. 在 GitHub 仓库 Settings → Secrets and variables → Actions 中配置以下四个 Secrets：
   - `ANDROID_RELEASE_KEYSTORE_B64`：JKS 文件内容的 Base64 编码；
   - `ANDROID_RELEASE_STORE_PASSWORD`：keystore 密码；
   - `ANDROID_RELEASE_KEY_ALIAS`：key alias；
   - `ANDROID_RELEASE_KEY_PASSWORD`：key 密码。
3. 将 `app/build.gradle.kts` 的 `versionName`、`versionCode` 更新为待发布版本，例如 `0.4.0`。
4. 在 Actions 中手动执行 **Signed Android Release**，输入匹配的标签 `v0.4.0`。
5. 工作流会运行单元测试、构建并验证正式签名的 APK、校验最低 Android 11、上传 `Android-China-Setup.apk` 和 `SHA256SUMS.txt`，发布新的 Latest Release。
6. 后续所有版本复用**同一私钥**。如果丢失私钥或更换签名体系，旧版本不能保证原地升级。

**严禁**把签名密钥、密码、Base64 密钥材料、个人 token 写入源代码、日志或 Issues。

## 当前局限

- 没有配置以上 Secrets 时，签名工作流会拒绝发布。普通 Debug 构建不受影响。
- 自动检查需要访问 GitHub API；下载 APK 可以使用已验证镜像加速，但镜像无法代替 GitHub Release 元数据。
- 不能在后台静默安装；必须由用户确认系统安装界面。
- 不支持从 Debug 签名强制覆盖成不相容的正式签名。
