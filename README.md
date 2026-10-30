# Android China Setup

<img src="assets/logo.svg" width="82" align="right" alt="Android China Setup 极客 Logo">

为 Android 11+ 提供精简、可信的开源应用目录和官方 APK 下载入口。无需 Root、电脑或 ADB。

[![Latest](https://img.shields.io/github/v/release/U0Axuan/android-china-setup?label=Latest)](https://github.com/U0Axuan/android-china-setup/releases/latest)

**[下载正式版 APK](https://github.com/U0Axuan/android-china-setup/releases/latest/download/Android-China-Setup.apk)** · [发布记录](https://github.com/U0Axuan/android-china-setup/releases) · [反馈 / 推荐项目](https://github.com/U0Axuan/android-china-setup/issues/new)

> **当前 GitHub Latest：v0.6.0 固定签名正式版。** 该版本起支持在线应用目录刷新；以后只更新 [catalog/apps.json](catalog/apps.json) 即可增删应用，用户不需要重新下载安装 APK。

## 功能

- **分类索引**：一句话应用简介、离线 README 快照、原项目链接和风险提示。
- **数据驱动目录**：新版本从 GitHub 安全更新 [在线应用清单](catalog/apps.json)，保留离线及已缓存目录；日后增删应用**无需重建 APK**。
- **下载与安装**：用户逐项选择，支持 GitHub 官方 Release、镜像测速、自定义 HTTPS 下载源与代理；安装需系统确认。
- **统一设置**：右上角进入测速、代理、应用目录刷新和自更新。
- **Material 3**：动态配色、暗色模式与简洁的开源终端风格图标。

## 收录项目

目录数量以[在线清单](catalog/apps.json)为准；此次新增 [Canta](https://github.com/samolego/Canta)（配合 Shizuku 管理预装应用）和 [Spotube](https://github.com/team-spotube/spotube)（插件式开源音乐播放器）。

**[查看全部应用及其原项目地址](catalog/apps.json)**。感谢每个开源项目的开发者和社区贡献者；本项目只提供目录与原站安装包入口，不重新打包第三方应用。

欢迎通过 [Issues](https://github.com/U0Axuan/android-china-setup/issues/new) 推荐更多优秀项目，并附上官网、许可证及推荐理由。

## 应用入选要求

为保证目录的开放性、安全性和可维护性，推荐收录的应用必须同时满足以下条件：

1. **必须开放源代码**：提供公开可访问的完整源代码仓库，具有明确的开源许可证，用户能够查阅、审计和自行构建；仅公开部分代码或仅免费提供安装包不符合要求。
2. **必须通过基础安全扫描**：拟收录版本应完成基础的恶意软件检测和已知风险检查，未发现尚未处置的高危或严重风险；可提供公开的扫描报告、CI 安全检查结果或其他可核验的检测记录。扫描通过不代表绝对安全。
3. **版本发布位置必须包含开源平台**：正式版本及其安装包必须可在 GitHub、GitLab、Codeberg 等公开的开源代码托管平台的 Releases、Tags 或对应发布页面追溯；仅提供网盘、聊天群或不透明的第三方下载链接不符合要求。
4. **近半年保持活跃**：最近 **6 个月内**应有可核验的维护活动，例如代码提交、版本发布、安全修复或对 Issue / PR 的实质性处理；仅有自动化机器人更新不视为有效活跃。

推荐时请附上**源代码及许可证链接、安全扫描依据、开源平台发布地址、最近一次维护活动的日期与链接**。对不满足条件的应用暂不收录；已收录项目如不再满足要求，将重新评估并视情况标注风险或下架。

## 维护应用目录：无需 Gradle 或模拟器

```powershell
python tools/catalog.py add --name "Example" --category "网络与连接" --repo "owner/repo" --summary "一句话简介" --snapshot "README 内容摘要"
python tools/catalog.py validate
```

以后只需提交 **`catalog/apps.json`**。GitHub Actions 自动检查清单安全与格式，不再为目录增删运行 Android 构建；App 下次自动同步或由用户点击**设置 → 应用目录 → 刷新应用目录**获取更新。

详细操作见 [快速维护指南](docs/CATALOG.md)。只有首次引入目录功能或后续修改程序代码时才需要构建、签名和发布 APK。

## 安全与兼容性

最低 Android **11（API 30）**。Canta 需要 Shizuku，误删关键系统组件可能引起开机故障；DirtyFrag、KSuRoot、GhostLock 仅提供外部入口。DroidDeck 需要特定 Adreno GPU，MTK/Mali 不在其支持范围。

在线目录必须通过 HTTPS 获取并验证字段，获取失败自动回退到缓存或内置数据。GitHub API 与镜像在中国大陆的可用性取决于网络环境。稳定版本使用固定签名，旧版 Debug 安装包可能无法直接覆盖升级。

[GitHub Actions](https://github.com/U0Axuan/android-china-setup/actions) · [签名与升级说明](docs/UPDATE_SIGNING.md) · [MIT License](LICENSE)
