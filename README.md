# Android China Setup

<img src="assets/logo.svg" width="82" align="right" alt="Android China Setup 极客 Logo">

专为中国大陆网络环境准备的 Android 开源应用目录和安装助手。**支持 Android 11+**，无需 Root、电脑或 ADB。

[![Latest](https://img.shields.io/github/v/release/U0Axuan/android-china-setup?label=Latest)](https://github.com/U0Axuan/android-china-setup/releases/latest)
[![APK](https://img.shields.io/badge/下载-最新APK-12A89D)](https://github.com/U0Axuan/android-china-setup/releases/latest/download/Android-China-Setup.apk)

**[下载最新版 APK](https://github.com/U0Axuan/android-china-setup/releases/latest/download/Android-China-Setup.apk)** · [发布记录](https://github.com/U0Axuan/android-china-setup/releases) · [问题反馈](https://github.com/U0Axuan/android-china-setup/issues)

> 下载地址永远指向 GitHub Latest Release。主分支的新版分类界面正在开发验证，**不等于已经发布到 Latest**。

## 功能

- **分类发现**：15 款精选 Android 应用，按六类分组展示，一句话简介和可离线查看的 README 摘要；每项都能访问原项目。
- **按需下载安装**：没有复选框或自动批量安装；普通项目可以从 GitHub 官方 Release 获取 APK 并交给系统确认安装。
- **多下载源**：官方直连、4 个预置 HTTPS 镜像及自定义源；支持响应测速和小样本速率估算。
- **统一设置**：右上角设置入口包含测速、代理（默认关闭）、关于和应用自更新。
- **Material 3**：动态配色、暗色模式、极简开源终端风格 Logo，单 LazyColumn 与离线目录以减少滑动负担。

## 收录与致谢

特别感谢以下项目的作者和维护者。本项目提供的是目录入口和 README 摘要，**不会重新打包第三方软件**。

| 类别 | 项目 | 原项目 |
| --- | --- | --- |
| 日常与效率 | 曦码输入法 | [ximeiorg/Xime](https://github.com/ximeiorg/Xime) |
| 日常与效率 | Via 浏览器 | [Via 官网](https://viayoo.com/) |
| 日常与效率 | 知乎++ | [zhihu-plus/android](https://github.com/zhihu-plus/android) |
| 游戏与影音 | BiliPai | [jay3-yy/BiliPai](https://github.com/jay3-yy/BiliPai) |
| 游戏与影音 | **osu! (lazer)** | [ppy/osu](https://github.com/ppy/osu) |
| 游戏与影音 | **DroidDeck** | [Droid-Deck/DroidDeck](https://github.com/Droid-Deck/DroidDeck) |
| 网络与连接 | FlClash | [chen08209/FlClash](https://github.com/chen08209/FlClash) |
| 网络与连接 | LocalSend | [localsend/localsend](https://github.com/localsend/localsend) |
| 应用与文件 | Obtainium | [ImranR98/Obtainium](https://github.com/ImranR98/Obtainium) |
| 应用与文件 | F-Droid | [f-droid.org](https://f-droid.org/) |
| 应用与文件 | Material Files | [zhanghai/MaterialFiles](https://github.com/zhanghai/MaterialFiles) |
| 系统与调试 | Shizuku | [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) |
| 高级与 Root | GhostLock | [YuKongA/ghostlock-app](https://github.com/YuKongA/ghostlock-app) |
| 高级与 Root | **DirtyFrag** | [mitschud/DirtyFrag](https://github.com/mitschud/DirtyFrag) |
| 高级与 Root | **KSuRoot** | [hmascs/KSuRoot](https://github.com/hmascs/KSuRoot) |

**欢迎推荐更多优秀 Android 项目！** 在 [Issues](https://github.com/U0Axuan/android-china-setup/issues/new) 附上项目地址、用途、许可证及推荐理由，也欢迎 Star 原作者或贡献 Pull Request。

## 兼容性与安全

- 本应用最低 **Android 11（API 30）**。DroidDeck 原项目要求 Adreno 730+ 或 8xx，**MTK/Mali 设备不在其支持范围**；osu! 官方 APK 体积较大，请预留空间。
- DirtyFrag、KSuRoot、GhostLock 涉及内核漏洞或提权，**只提供链接与 README 摘要，不自动下载或执行**。使用前须评估设备固件兼容性和风险。
- 镜像仅在 GitHub 提供 SHA-256 时使用并校验；更新安装必须兼容签名。旧版 CI Debug 签名可能无法覆盖升级。
- Android 11/15 自动化测试由 [GitHub Actions](https://github.com/U0Axuan/android-china-setup/actions) 执行，ARM64 真机与国内网络效果仍需验证。

[查看 V0.5 Android 11 / 15 测试报告](docs/V0.5_TEST_REPORT.md)。

项目代码使用 MIT License。第三方应用各自许可及商标归原权利人所有。
