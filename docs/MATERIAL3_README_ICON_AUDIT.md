# Android China Setup · Material 3 UI 审查与原作者素材策略

## 审查依据

- Android 开发者官方 [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Compose accessibility](https://developer.android.com/codelabs/jetpack-compose-accessibility)
- 本项目最低 Android 11 / API 30；应用目录源仅存储经过校验的元数据，不能远程下发可执行代码

## 本次审查结论

| 维度 | V0.7.0 问题 | 修复及验收点 |
| --- | --- | --- |
| Logo 真实性 | README 第一张图片可能是徽章、截图、赞助图 | 仅使用核实过的 `logoPath`；路径来源限定官方 GitHub 原仓库，GET Contents API 验证文件身份、大小；CI 验证图片格式与基本方形长宽比 |
| Logo 缺失 | 错误图片或字母占位容易造成混淆 | 未核实或加载失败则完全不显示 Logo；清空旧版错误图标缓存 |
| README 可读性 | Markdown、HTML 原文直接显示，段落、代码、标题混乱 | CommonMark 分析标题、段落、列表、引用、代码，jsoup 清理 HTML；徽章/截图不在正文出现；保留原作者完整 README 链接 |
| 长文阅读 | 滚动内容过大且下载按钮位于底部 | ModalBottomSheet 内 LazyColumn 分块加载，主要操作位于 README 之前 |
| 色彩 | 自定义组件部分使用临时颜色 | 以 `MaterialTheme.colorScheme` 的背景、表面、错误和正文 token 为准；动态色与深色模式保留 |
| 排版 | 信息层级和状态信息间距不统一 | 标题使用 title/headline 系列，摘要使用 body 系列，元数据使用 label 系列；统一按 4dp 网格及 12/16/20dp 常规留白 |
| 点击区域 | 图标/按键尺寸与层级需复查 | 点击按钮至少 48dp；Logo 无单独操作行为；RadioButton 行仅有一个主操作区，避免重复点击语义 |
| 导航 | 下载二级菜单使用局部状态，顶部返回直达首页 | 状态提升到主页面；导航栏返回和系统 Back 均先回设置，再回目录 |
| 安全区域 | 底部 420dp 人工填充会产生明显空白 | 移除固定占位；代理设置移至二级页靠前区域；遵循 Scaffold 内边距与自然滚动 |
| 离线/失败 | 原项目 README、Star、图标都依赖网络 | 缓存 README/Star，原文失败显示内置摘要，图标失败直接不显示，不误报已验证 |

## Logo 收录规则

1. 必须来自原作者/官方 GitHub 仓库中可明确判断为应用图标的文件，优先使用 Android Launcher `mipmap`、项目明确标注的 app icon。
2. 目录中仅保留仓库相对路径 `logoPath`，而不是抓取任意 README 图片、第三方商店图、搜索图片或仓库头像。
3. 只接受已核实的 PNG、WebP、JPEG 栅格图；路径需要和原仓库对应。横幅、徽章、截图、Sponsor、SVG/矢量未转换时一律不猜测。
4. CI 的 `tools/logo_audit.py` 负责核查仓库实际文件、GitHub 原站 URL、格式、容量、基本长宽比；断网/限流为待核验，不伪称已通过。手动核验仍是图标身份正确性的必要条件。
5. 图片加载失败或项目无可信图标时，不显示图标，也不生成字母/随机占位。

本次核实的 11 个图标：曦码输入法、MarcoMovie、MeiloX、FuoEvolve、LocalSend、云析、Obtainium、Material Files、Shizuku、Canta、遥测组件清理。其余项目暂不显示图标，待提供可核验的官方栅格资产再补充。

## README 内容策略

- 展示 GitHub 原作者 README 的结构化文本，而不运行 HTML、JavaScript 或嵌入式脚本。
- 保留正文、标题、列表、引用、代码块；安全移除冗余图片、自动状态徽章与 HTML 布局标签。
- 源代码中的 Markdown 链接不会转换为 App 内可执行行为；通过“阅读原项目 README 全文”打开原作者发布页，避免展示成不可信的第三方链接。
- 对超长内容进行长度与区块数限制，保证滚动性能和内存占用；网络不可用时仍保留人工校对的 README 简介。

## 自动化与尚需人工核查的项目

- 运行 `python3 tools/catalog.py validate` 与 `python3 tools/logo_audit.py`，检查输入及源素材。
- Gradle 单元测试覆盖 HTML 标签清理、图片/徽章剔除、标题与列表、代码块、非法图标路径；Android 11 / Android 15 模拟器覆盖启动、详情、设置、测速与代理操作。
- 对字体缩放 200%、屏幕阅读器 TalkBack、部分定制 ROM 的窗口栏及横屏/平板布局，模拟器冒烟测试不能取代人工辅助功能验收；未测试前不得宣称“完全无障碍合规”。

