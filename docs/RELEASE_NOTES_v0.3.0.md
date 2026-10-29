# Android China Setup v0.3.0 — 首个公开测试版

**这是首个公开发布的 Android APK，用于社区体验、反馈与兼容性测试，并非经过全面验收的稳定版。**

## 新功能
- 最低支持 Android 11（API 30），无需电脑、Root、ADB 或 Shizuku。
- 内置 11 个精选 Android 项目与官方地址，逐项选择、逐项下载和安装。
- 下载源选择：自动检测、GitHub 官方直连、4 个预设社区加速源，以及手机端自定义 HTTPS 镜像。
- 镜像下载仅在 GitHub Release 可提供可信 SHA-256 时启用，并在下载后校验。
- 可选 HTTP/SOCKS5 代理，默认关闭；下载进度、错误提示及 Android 系统安装确认。
- 内置 HiWiki 加速源参考入口，方便自行查找并添加更多源。
- 欢迎通过 Issues 推荐优秀 Android 项目。

## 下载和安装
下载附件 **Android-China-Setup.apk**，在 Android 11 或更高版本的手机上打开安装。
首次运行后可以通过「配置下载加速镜像」选择源或添加自定义 HTTPS 源。

## 当前验证状态与已知限制
- GitHub Actions **Gradle 编译、7 项下载源单元测试**：通过。
- Android 11 / Android 15 云端模拟器：应用安装启动、下载源选择、自定义源保存、代理设置界面、应用清单等检查通过。
- **最新云端测试中，GitHub 官方 APK 下载超时，完整下载到安装流程尚未在 V0.3 上通过复测**。此前 V0.2 的 Material Files 下载与安装曾通过，但不能代替 V0.3 验证。
- 当前尚未验证中国大陆运营商网络环境、各镜像真实转发、HTTP/SOCKS5 代理端到端链路或 ARM64 真机。
- 如果 GitHub 官方 API 完全无法访问，镜像本身仍不足以获取软件最新 Release 元数据。
- 部分项目只有官网入口；GhostLock 仅作高风险工具链接，不执行 Root 或漏洞利用。
- 不包含清单导入/导出、静默批量安装、安装失败自动重试。
- **本安装包由 CI 使用 Android Debug 测试签名构建**。后续签名不同的 APK 可能无法直接覆盖升级，请妥善保管应用配置，必要时卸载旧包后重装。请勿把它视为经过正式发布签名的生产版本。

## 开源项目与反馈
第三方应用各自版权、许可证与商标归原作者所有。本项目不重新打包这些第三方应用。感谢每一位维护者，并欢迎通过 [Issues](https://github.com/U0Axuan/android-china-setup/issues/new) 推荐优质 Android 项目。

**完整版本变化请查看 [README](https://github.com/U0Axuan/android-china-setup#readme) 和 [云端测试说明](https://github.com/U0Axuan/android-china-setup/actions/workflows/emulator-test.yml)。**
