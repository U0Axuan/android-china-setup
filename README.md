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
