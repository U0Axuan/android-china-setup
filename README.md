# Android China Setup

<img src="assets/logo.svg" alt="Android China Setup Logo" width="84" align="right" />

专为中国大陆网络环境设计的 Android 应用初始化助手。**Android 11+**，无需电脑、Root 或 ADB。

[![Latest Release](https://img.shields.io/github/v/release/U0Axuan/android-china-setup?label=Latest)](https://github.com/U0Axuan/android-china-setup/releases/latest)
[![Download APK](https://img.shields.io/badge/%E4%B8%8B%E8%BD%BD-Android%20APK-1467CF)](https://github.com/U0Axuan/android-china-setup/releases/latest/download/Android-China-Setup.apk)

**[⬇️ 下载最新 APK](https://github.com/U0Axuan/android-china-setup/releases/latest/download/Android-China-Setup.apk)** · [最新版本说明](https://github.com/U0Axuan/android-china-setup/releases/latest) · [历史版本](https://github.com/U0Axuan/android-china-setup/releases)

> 下载直链始终指向 GitHub Latest Release 中的 `Android-China-Setup.apk`，无需随版本修改。当前仍是公开测试阶段，建议先查看发布说明。

## 主要功能

- **精选应用**：内置 11 个项目，单个应用按需下载，提供「访问原项目」入口。
- **下载加速**：GitHub 官方源、GHFast / GH-Proxy / LLKK / GHProxy.net，以及手动添加的 HTTPS 镜像。
- **可见测速**：「测速」页面显示响应延迟和约 128 KB 样本估算速率，支持手动选源。
- **安全安装**：官方 SHA-256 可用时校验下载文件，交由 Android 系统安装器确认；不静默安装。
- **自更新**：「关于」页面检查 GitHub 最新版本、下载更新并校验 APK 签名；HTTP / SOCKS5 代理可选，默认关闭。
- **Material 3**：动态配色、明暗主题、底部导航和自适应极简图标。

## 收录项目与致谢

感谢以下项目的开发者与社区贡献者。链接均指向原项目；本仓库不重新分发它们的安装包或代码。

| 类别 | 项目 | 官方来源 |
| --- | --- | --- |
| 输入法 | 曦码输入法 Xime | [GitHub](https://github.com/ximeiorg/Xime) · [官网](https://ime.ximei.me/) |
| 浏览器 | Via | [官网](https://viayoo.com/) |
| 视频 | BiliPai | [GitHub](https://github.com/jay3-yy/BiliPai) |
| 网络 | FlClash | [GitHub](https://github.com/chen08209/FlClash) |
| 系统 | Shizuku | [GitHub](https://github.com/RikkaApps/Shizuku) |
| 高级工具 | GhostLock | [GitHub](https://github.com/YuKongA/ghostlock-app) |
| 资讯 | 知乎++ | [GitHub](https://github.com/zhihu-plus/android) |
| 应用更新 | Obtainium | [GitHub](https://github.com/ImranR98/Obtainium) |
| 应用商店 | F-Droid | [官网](https://f-droid.org/) |
| 文件管理 | Material Files | [GitHub](https://github.com/zhanghai/MaterialFiles) |
| 文件传输 | LocalSend | [GitHub](https://github.com/localsend/localsend) |

🌟 **欢迎推荐更多优秀 Android 项目！** 请通过 [GitHub Issues](https://github.com/U0Axuan/android-china-setup/issues/new) 提交项目地址、功能、许可证和推荐理由，也欢迎通过 Pull Request 贡献改进。喜欢这些项目，也请为原作者 Star 或提供支持。

## 兼容性与注意事项

- **最低 Android 11 / API 30**；Android 11、15 云端模拟器 UI 检查通过：[测试报告](docs/V0.4_UI_TEST_REPORT.md)。
- 镜像只加速 APK 文件，获取 Release 元数据仍需访问 GitHub API；国内运营商网络、ARM64 真机和滑动掉帧尚需专项验证。
- **签名注意**：首版 v0.3.0 和当前测试版使用 CI Debug 签名，不保证相互覆盖升级；Android 不允许使用不同证书直接覆盖安装。发布方配置固定签名后，才能保证后续同签名升级。详见[签名与自更新说明](docs/UPDATE_SIGNING.md)。
- GhostLock 为高风险项目，仅提供外部链接，不自动运行或提权；第三方项目的版权与许可证归原作者所有。

## 构建

使用 Android Studio、JDK 17 和 Android SDK 35；或运行 [GitHub Actions](https://github.com/U0Axuan/android-china-setup/actions)。项目采用 Kotlin、Jetpack Compose 与 Material 3，按 MIT License 开源。
