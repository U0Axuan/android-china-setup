# V0.7.2 视觉与无障碍核对记录

本报告按 Android Jetpack Compose / Material 3 与 Android Accessibility 官方建议检查当前应用的代码。**代码审查不等于视觉/真机认证**，每次发布仍需要 Android 11/15 模拟器截图以及大字体、TalkBack、深浅色、横屏和窄屏核验。

## Logo 身份判断

1. 不再从 README 中使用第一张 `<img>`、徽章、截图、宣传图或任意 `logo.png` 猜测应用图标。
2. 只接受目录中人工登记的**原作者 GitHub 仓库 Android launcher 资源**，名称限定 `ic_launcher` / `launcher_icon`，且放在 `mipmap-*` 或 `drawable-*` 目录，格式为 PNG/WebP。
3. 加载前需经 GitHub Contents API 核对仓库、路径、对象类型和文件大小；图片真正加载失败时隐藏，不显示替代图片。
4. 对 Compose 动态合成、Android adaptive icon 前景/背景图、Flutter 平台资源、仓库品牌图等，不能仅从文件名确认完整启动图标；在确认原始应用启动图标前**一律留空**。
5. 对任何新增 Logo，人工核对原作者仓库的 AndroidManifest `android:icon` 或应用 icon 配置、资源路径及实际缩略图，记录证据后再填写 `logoPath`。此流程不能靠“路径存在”代替身份验证。

本次主动取消不确定的 `MarcoMovie`、`MeiloX`、`LocalSend`、`Obtainium`、`云析（YunX）`、`Shizuku`、`遥测组件清理` logoPath；并在 Android 解析器及目录 CLI 中施加相同的路径约束。**无图标就不显示图标区**。

Manifest 核对记录：Xime、FuoEvolve、Material Files、Canta 的应用图标声明对应当前收录的 mipmap 图标资源；YunX 使用 `@drawable/icon` 或 `@drawable/ic_launcher_new`，Shizuku 的 Manifest 图标声明出现 `@drawable/ic_launcher` 等活动级资源，因此撤销这两项不确定映射。今后新增图标需核验声明位置（application/launcher activity）、资源引用和实际图案，不能只看文件名。

## README

- 保留 Markdown 的标题、段落、列表、引用与代码块层级，并以 Compose 原生 Text/Surface 展示。
- HTML 中的 `h1`—`h6`、`p`、`summary/details`、`table/tr/th/td`、`ul/ol/li`、`pre`、`blockquote` 转为文本结构，而非把原始标签打印出来。
- 不执行 HTML / JavaScript，不引入 WebView；排除 script、iframe、badge 图像、嵌入内容。图片省略，保留“在 GitHub 阅读原文”操作。
- 48 KB / 180 个区块的截断是移动端阅读与渲染保护，原仓库 README 原页仍是权威完整版本。

## Material 3 / UI 审核

| 核对项 | 当前处理 | 后续验证 |
|---|---|---|
| 动态深浅色与颜色语义 | 优先 Android 12+ 动态 Material 3 colorScheme，其他系统提供浅色和深色主题 | 深浅色截图和对比度自动检测 |
| 导航与返回 | TopAppBar 提供当前层级标题、返回/设置 IconButton；系统返回先回上一级 | 系统手势导航与屏幕旋转 |
| 可点击区域 | 应用查看/下载拆分；镜像选择/删除拆分；关键操作使用 >=48dp 区域 | 无障碍 Scanner / TalkBack 聚焦顺序 |
| 标题与信息层级 | MaterialTheme typography 的 headline/title/body/label，不用固定 px 字号 | 系统字体 1.3x / 2x 下的溢出 |
| 列表与滚动 | keyed LazyColumn、独立详情 ModalBottomSheet、README 段落分块 | 大量目录项、长文和小屏幕 |
| 状态与反馈 | 下载显示进度、操作禁用和文本状态；错误/风险有文本说明 | 离线、超时、错误和恢复 |
| 图标缺失 | 完全隐藏 Logo，占位不显示，不影响文本排列 | 样式与左右间距的视觉检查 |

参考： [Android accessibility](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)、[Compose accessibility codelab](https://developer.android.com/codelabs/jetpack-compose-accessibility)。

## 发布门槛

需要目录校验、自测、Android 单元测试与 APK 编译通过，并通过 Android 11/15 冒烟及截图复核后再考虑发布；不能以此报告代替实际测试。

## 2026-11-17 补充复核

- 遥测组件清理的 `mipmap-xxxhdpi/ic_launcher.webp` 是旧的 Android 默认机器人。Android 11+ 实际选用 `mipmap-anydpi/ic_launcher.xml`，前景为清理条纹与星形，不能使用旧 WebP 代替，已从在线与离线目录删除此映射。
- 曦码、Material Files、Canta 已对照 adaptive-icon 引用与旧完整图形确认主体一致；FuoEvolve 使用原完整栅格启动图。当前保留 4 个原图，其余留空。
- 自定义镜像的键盘避让、Next/Done 和保存状态已补齐；测试在输入前验证真正的 EditText 焦点，保存后检查 XML 中的 JSON 精确地址。
- Lint 已运行，未发现错误；现有警告主要为 SDK/依赖版本更新建议及备份配置提示，本补丁不进行框架大版本迁移。
