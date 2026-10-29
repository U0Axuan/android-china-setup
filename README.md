# Android China Setup

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

## 当前状态
**V0.1 项目骨架**：Kotlin / Jetpack Compose Android 工程、应用清单及选择界面、下载规范和 GitHub Actions 构建流程。当前不提供 APK 下载、代理连接或系统设置修改的可用实现。后续按 Issue / roadmap 开发并通过 CI 校验。

## 构建
Android Studio 打开仓库；使用 JDK 17 / Android SDK 35。或通过 GitHub Actions 的 Build APK 工作流构建调试版。请查看 Actions 构建结果，不能将未经运行验证的骨架视为正式版本。

## 安全原则
仅信任开发者发布的 APK 及可信元数据；下载镜像必须与原始安装包的摘要/签名一致。第三方应用由其作者提供，用户自行核对授权、隐私和适用范围。严禁静默执行任何内核提权或漏洞利用流程。

## License
MIT。
