# Android China Setup v0.4.0

**公开测试版，最低 Android 11（API 30）。**

## 本次更新
- 全新 Material 3 界面：四个底部导航页面、动态配色、暗色模式及极简自适应 Logo。
- 应用列表改为独立 LazyColumn，减少混合设置布局造成的滚动负担；真机流畅性还需进一步测量。
- 独立的下载源测速界面：显示连接延迟，以及最多 128 KiB 下载样本得到的估算速率。
- 增加「访问原项目」入口；下载源选择、HTTPS 自定义镜像保留。
- 新增应用内检查更新、下载更新和签名兼容检查，安装仍须 Android 系统确认。

## 下载
请下载本页面附件 **Android-China-Setup.apk**。另外提供 **SHA256SUMS.txt** 供完整性验证。

## 验证
- GitHub Actions Gradle 编译和 **9 项 JVM 单元测试通过**。
- Android 11（API 30）、Android 15（API 35）云端模拟器的 **32/32 项 UI 检查分别通过**（同版本源代码的测试）。
- **尚未完整验收**：中国大陆运营商与镜像的实际 APK 下载、ARM64 真机卡顿/帧率、所有第三方应用安装、HTTP/SOCKS5 代理端到端测试。

## 必须知道的签名限制
本版本 APK 为 **CI Debug 签名的公开测试包**，不是使用长期固定证书签名的正式生产包。与 v0.3.0 等旧版本证书可能不相容，因此 Android 可能拒绝覆盖升级；需要卸载旧版再安装（旧版配置可能丢失）。应用内更新会拒绝不兼容的签名，不会绕过 Android 安全策略。后续稳定升级需要维护者使用同一个受保护的发布签名密钥。

## 致谢与反馈
感谢 Xime、Via、BiliPai、FlClash、Shizuku、GhostLock、知乎++、Obtainium、F-Droid、Material Files 和 LocalSend 的开发者与社区。欢迎通过 [Issues](https://github.com/U0Axuan/android-china-setup/issues/new) 推荐其他优秀 Android 项目。

[项目说明](https://github.com/U0Axuan/android-china-setup#readme) · [测试报告](https://github.com/U0Axuan/android-china-setup/blob/main/docs/V0.4_UI_TEST_REPORT.md)
