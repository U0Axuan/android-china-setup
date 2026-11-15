# Android China Setup v0.7.0

## 主要更新
- 应用详情展示原作者 GitHub README 原文（缓存读取；离线自动回退摘要）、GitHub Star 数与从原项目 README 提取的应用 Logo（无法识别时回退首字母图标）。
- 下载、测速和代理统一迁移至「设置 → 下载与测速」二级页面。
- GitHub 下载镜像源列表从本仓库 `catalog/mirrors.json` 更新，按本地日期每日检查一次，无需发新版 APK；网络不可用时继续使用本地缓存和内置源。
- 智能择优保留测速排序缓存；已有缓存时不再自动重复测速，可手动重新测试并刷新。
- 应用目录每天首次启动在线检查一次，异常时继续展示经过验证的缓存或内置目录。
- 设置新增突出显示的「推荐开源应用」入口。用户需登录 GitHub，通过专用 Issue Form 提交原作者 GitHub 地址，并提供许可证、发布记录、维护活动和安全扫描依据。

## 应用目录
- 新增：MarcoMovie、TagMacro、XGesture、遥测组件清理（TelemetryCleaner）、云析（YunX）、MeiloX、FuoEvolve。
- 移除：Spotube。
- 目录总计 23 项；以上新增应用的 APK 文件名匹配原作者 GitHub Release。

## 发布与安全
- 最低 Android 11（API 30），版本号 0.7.0（versionCode 7）。
- 继续使用 V0.6 的固定签名证书，签名 SHA-256 指纹应为 `aea1a8e0a9ea81585425e17cbc5c433a2eb334206e4f9a8cbdeefbd74d0e3c57`。
- 高风险 Root 项目仍不允许一键执行；原作者 APK 下载仅从 GitHub Release 获取，若使用第三方镜像必须获得来自官方的 SHA-256 摘要并校验。
- 本版本的线上 README、Logo、Star 和镜像列表都依赖 GitHub 连通性；离线或限流时将优先展示本地缓存、内置摘要及原始默认源。

