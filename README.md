# Android China Setup

[![GitHub 最新版本](https://img.shields.io/github/v/release/U0Axuan/android-china-setup?label=GitHub%20Release)](https://github.com/U0Axuan/android-china-setup/releases/latest)
[![下载最新 APK](https://img.shields.io/badge/%E4%B8%8B%E8%BD%BD-%E6%9C%80%E6%96%B0APK-blue)](https://github.com/U0Axuan/android-china-setup/releases/latest/download/Android-China-Setup.apk)

## 📥 下载最新版本

**[⬇️ 下载最新版 Android APK（动态直链）](https://github.com/U0Axuan/android-china-setup/releases/latest/download/Android-China-Setup.apk)**

**[查看最新版本发布说明](https://github.com/U0Axuan/android-china-setup/releases/latest)** · [查看所有历史版本](https://github.com/U0Axuan/android-china-setup/releases)

> 下载链接自动指向 GitHub 最新正式 Release 的同名 `Android-China-Setup.apk` 附件，后续发布新版本时无须修改 README。首版为 **v0.3.0 公开测试版**，最低 Android 11；目前使用 Debug 测试签名，后续发布签名如有变化可能需卸载后重装。中国大陆网络访问 GitHub 受限时可进入发布页面手动选择下载方式。

手机端独立运行的 Android 中国大陆环境初始化与应用部署工具。

## 设计目标
- **手机独立运行**：无需电脑、ADB、Root 或 Shizuku 才能启动本工具。
- **按需安装**：内置分类应用清单，可勾选和取消；无强制捆绑。
- **下载安全**：智能选择可信下载源；自定义 HTTP/SOCKS5 代理保留，但默认关闭。
- **系统权限透明**：普通应用无法静默安装 APK、设置系统输入法或更改受保护设置，涉及这些操作时交由 Android 系统授权或引导。
- **高级工具可选**：GhostLock 等具备高风险的工具不自动执行。

## 默认应用清单
默认选择：**曦码输入法、Via 浏览器、Obtainium、Material Files**。  
可选：BiliPai、FlClash、Shizuku、GhostLock、知乎++、F-Droid、LocalSend。  
已移除 PCAPdroid、KDE Connect、Aegis、Uptime Kuma。

请以仓库中的 `app/src/main/assets/apps.json` 为准；尚未核实的项目包名不会自动下载安装。

## 推荐 Android 项目与致谢 🙏

Android China Setup 希望帮助用户发现优秀的 Android 应用，并以更便捷、透明的方式完成初始化。特别感谢以下项目的作者、维护者和贡献者，是他们持续的投入丰富了 Android 生态。

> **说明**：以下为本工具计划收录的第三方应用/项目及其官方来源，不代表这些项目已为本工具背书，也不意味着本仓库复制、修改或内置其源代码。应用版权、商标和许可证均归各自权利人所有，下载安装请遵守相应项目条款。项目的“收录”不代表现阶段已经实现自动安装功能。

| 分类 | 项目 | 项目 / 官方地址 | 简介 | 默认选择 |
| --- | --- | --- | --- | --- |
| 输入法 | **曦码输入法（Xime）** | [GitHub](https://github.com/ximeiorg/Xime) · [官网](https://ime.ximei.me/) | 基于 Rime 的中文输入法 | ✅ |
| 浏览器 | **Via 浏览器** | [官方 GitHub（含本地化资源）](https://github.com/tuyafeng/Via) · [官网](https://viayoo.com/) | 轻量 Android 浏览器 | ✅ |
| 影视 | **BiliPai** | [GitHub](https://github.com/jay3-yy/BiliPai) | 第三方 B 站客户端 | ⬜ |
| 网络 | **FlClash** | [GitHub](https://github.com/chen08209/FlClash) | 网络代理客户端，使用需遵守当地法律法规 | ⬜ |
| 系统 | **Shizuku** | [GitHub](https://github.com/RikkaApps/Shizuku) | 为受支持应用提供系统 API 访问机制 | ⬜ |
| 高级系统工具 | **GhostLock** | [GitHub](https://github.com/YuKongA/ghostlock-app) | 涉及内核提权的高风险工具，仅展示项目入口，不自动运行 | ⬜ |
| 资讯 | **知乎++（Zhihu++）** | [GitHub](https://github.com/zhihu-plus/android) | 第三方知乎客户端 | ⬜ |
| 应用管理 | **Obtainium** | [GitHub](https://github.com/ImranR98/Obtainium) | 从项目发布源获取应用更新 | ✅ |
| 应用商店 | **F-Droid** | [官网](https://f-droid.org/) · [GitLab](https://gitlab.com/fdroid/fdroidclient) | 自由开源 Android 应用生态 | ⬜ |
| 文件管理 | **Material Files** | [GitHub](https://github.com/zhanghai/MaterialFiles) | Material 风格文件管理器 | ✅ |
| 文件传输 | **LocalSend** | [GitHub](https://github.com/localsend/localsend) · [官网](https://localsend.org/) | 局域网跨设备文件传输 | ⬜ |

再次向这些项目的开发者和社区致谢！**如果你喜欢其中某个项目，欢迎前往原仓库 Star、反馈问题、参与翻译、贡献代码或支持作者。**

### 🌟 欢迎推荐更多优秀的 Android 项目！

好用的软件不该被埋没。我们希望 Android China Setup 的应用清单由社区一起维护，尤其欢迎推荐适合中国大陆网络与使用习惯的**开源、轻量、注重隐私、持续维护**的 Android 应用。

如果你发现值得推荐的项目，请：

1. [提交 GitHub Issue 推荐应用](https://github.com/U0Axuan/android-china-setup/issues/new)，标题建议为 `[应用推荐] 项目名称`。
2. 尽量附上**项目名称、官方主页或源码仓库、主要功能、下载地址、开源许可证、推荐理由**，以及在中国大陆网络环境下的可访问性。
3. 如果愿意，可以直接提交 Pull Request，完善应用清单、中文介绍或适配建议。

我们会优先考虑来源明确、维护活跃、安全可信、可由用户自行选择安装的项目。推荐并不保证立即收录；涉及高风险权限、修改版客户端、软件授权或版权争议时，将单独评估。

**欢迎每一位 Android 用户、独立开发者和开源维护者一起建设这个清单，让更多优秀 Android 项目被发现、被使用、被尊重。** ❤️

## 当前状态（V0.3 开发版）

**最低支持 Android 11（API 30）**，无需电脑、Root、Shizuku 或 ADB 即可在手机上操作。

已实现：

- 内置 11 项应用清单、按需选择、逐项下载与安装确认（不进行批量自动安装）。
- 从 GitHub 官方最新 Release 获取 APK 元数据，按设备 ABI 选择匹配构建。
- **镜像源选择**：智能优选、GitHub 官方直连、手动选择预置社区节点、用户自行添加或删除 HTTPS 镜像地址。
- **自动测速**：在开始下载时并发探测已配置源的 HTTP 响应时间，按可访问性及延迟选择候选源；失败后尝试其他已配置源。
- **用户自定义**：手机内输入名称和 HTTPS 前缀，保存到本地并允许切换或删除；最多 12 个自定义源。
- HTTP / SOCKS5 代理可选，默认关闭；不依赖 FlClash。
- APK 下载进度、最大文件大小限制、官方 SHA-256 校验及 Android 系统安装确认。

### 加速源列表

下列节点为公开资料中出现的常见 **GitHub Release URL 前缀代理**，不是 GitHub 官方服务，也不保证持续可用。它们属于示例预设，**不声称与 [HiWiki 加速源页面](https://git.hiwiki.org/zh-CN/mirrors) 的实时完整列表完全一致**。应用中可直接打开该页面，自行添加其新源。

| 名称 | 地址 |
| --- | --- |
| GHFast | https://ghfast.top |
| GH-Proxy | https://gh-proxy.com |
| LLKK | https://gh.llkk.cc |
| GHProxy.net | https://ghproxy.net |

默认选择**智能优选**；也可以明确选择某一个镜像或官方直连。GitHub 官方 Release 的 SHA-256 是信任依据，**缺少可验证官方摘要时只允许官方下载**。镜像与用户自定义源仅加速 APK 文件下载，GitHub Release 元数据目前仍需访问官方 GitHub API；在完全无法连接 GitHub API 的网络上，本版本尚不能独立完成自动下载。

**不开发**：应用清单导入/导出、自动批量安装与安装失败自动重试。

**仍待验证或开发**：不同中国大陆运营商的实际可达性、各加速源真实下载与安全回退、ARM64 真机测试、完整应用级签名信任链及系统初始化引导。自动测速根据网络响应延迟排序，**不等于持续测速带宽或保证最快下载速度**。

## 云端实测记录

- [Android 11 + Android 15 云端兼容测试工作流](https://github.com/U0Axuan/android-china-setup/actions/workflows/emulator-test.yml)
- [2026-10-29 Android 15 模拟器测试报告：33/33 检查通过](docs/CLOUD_TEST_REPORT_2026-10-29.md)
- [测试执行、APK 及界面截图](https://github.com/U0Axuan/android-china-setup/actions/runs/40848698121)

**注意：模拟器测试通过不等于 ARM64 真机、国内运营商网络或所有第三方应用都已经验证。**

## 构建
Android Studio 打开仓库；使用 JDK 17 / Android SDK 35。或通过 GitHub Actions 的 Build APK 工作流构建调试版。请查看 Actions 构建结果，不能将未经运行验证的骨架视为正式版本。

## 安全原则
仅信任开发者发布的 APK 及可信元数据；下载镜像必须与原始安装包的摘要/签名一致。第三方应用由其作者提供，用户自行核对授权、隐私和适用范围。严禁静默执行任何内核提权或漏洞利用流程。

## License
MIT。
