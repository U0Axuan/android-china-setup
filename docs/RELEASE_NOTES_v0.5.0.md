# Android China Setup v0.5.0 — 首个固定签名正式发布版

适用于 **Android 11（API 30）及以上**。本版本使用维护者提供的**长期固定 JKS 密钥**进行正式 APK 签名。后续版本只要继续使用相同签名证书且递增 versionCode，即可在系统安装器确认后覆盖升级。

## 主要更新
- 收录 15 个 Android 项目，按 6 类分组展示，一句话简介与离线 README 摘要
- 新增 [DroidDeck](https://github.com/Droid-Deck/DroidDeck)、[osu!](https://github.com/ppy/osu)、[DirtyFrag](https://github.com/mitschud/DirtyFrag)、[KSuRoot](https://github.com/hmascs/KSuRoot)
- 移除应用复选框与底部导航；右上角设置统一容纳测速、代理和关于/自更新
- 重新设计开源终端风格自适应 Logo，并优化列表渲染结构，减少滑动期间多余操作
- 下载源支持官方 GitHub、社区镜像及自定义 HTTPS 地址；可查看响应延迟与采样速率
- 选择性下载和安全安装，使用 GitHub 官方 SHA-256 验证具备该摘要的 APK

## 发布验证
- 经过 12 项 JVM 单元测试及 APK 编译验证
- 同一应用源码的 Android 11 / 15 云端模拟器各 **48/48 项 UI 检查通过**
- 正式签名的 APK 另外执行签名验证、系统最低版本检查、Android 11 / 15 模拟器启动与设置页验收
- 提供 `SHA256SUMS.txt` 以校验本页面附件的 APK 文件

## 安装和升级说明
**v0.3.0 和 v0.4.0 使用临时 Debug 签名，证书不同，不能保证覆盖安装 v0.5.0。** 若系统提示“安装失败/签名不一致”，需先卸载旧版再安装本版，本地配置可能丢失；**请勿卸载需要保存数据的其他应用**。从 v0.5.0 起，应保留这套正式签名，后续版本才能可靠升级。

DirtyFrag、KSuRoot 和 GhostLock 涉及设备内核提权，仅提供原项目及 README 摘要入口，不提供自动提权。DroidDeck 主要支持特定 Adreno GPU，**不支持 MTK/Mali**；osu! APK 体积较大，需要充足磁盘空间。

## 已知限制
- 云端 x86_64 软件 GPU 的掉帧数据不能代表真实 ARM64 手机，滑动卡顿仍需真机性能测试
- 尚未完成所有国内运营商与镜像的真实下载、全量第三方 APK 安装和代理端到端专项测试
- 软件检查更新依赖 GitHub API，应用更新安装需用户通过 Android 系统确认

欢迎在 [Issues](https://github.com/U0Axuan/android-china-setup/issues/new) 提交问题和推荐开源 Android 应用。

[项目说明](https://github.com/U0Axuan/android-china-setup#readme) · [V0.5 测试与性能报告](https://github.com/U0Axuan/android-china-setup/blob/main/docs/V0.5_TEST_REPORT.md)
