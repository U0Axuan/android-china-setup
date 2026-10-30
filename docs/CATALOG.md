# 无需重新构建 APK 的应用目录维护

Android China Setup 使用 `catalog/apps.json` 作为**唯一需要维护的在线应用目录**。首次支持该机制的 APK 安装后，用户启动时最多每 12 小时自动检查一次目录，或进入**右上角设置 → 应用目录 → 刷新应用目录**立即获取更新。网络受限时仍可查看本机上次成功缓存的数据，首次安装离线时回退 APK 内置的清单。

## 新增应用：只提交一份 JSON

可以直接编辑 `catalog/apps.json`，或者在仓库根目录运行以下命令（Windows PowerShell、macOS、Linux 均需要 Python 3）：

```powershell
python tools/catalog.py add --name "Example App" --category "网络与连接" --repo "owner/repository" --summary "一句话介绍" --snapshot "人工整理的 README 摘要。" --apk-regex "^example-universal\\.apk$"
python tools/catalog.py validate
git add catalog/apps.json
git commit -m "catalog: add Example App"
git push
```

`--repo` 为 GitHub 官方仓库 `owner/repository`；会自动补全 `homepage` 和 `readmeUrl`。通过 `--homepage` 与 `--readme-url` 可以覆盖。没有 GitHub 发行文件时，用 `--mode external` 仅展示原项目入口。

其他可选参数：`--notice`（兼容性/风险说明）、`--risk caution`（风险警告）、`--risk high --mode external`（高风险项目仅链接）、`--apk-regex`（项目 Release 有多个 APK 时，指定官方资源的**完整文件名**）。

**不要为每次新增应用改动 MainActivity、Gradle、README 中的应用表或 APK 版本号。** 只有修改程序逻辑、主题或希望更新离线内置清单时才需要修改 APK 源码并重新签名发布。

## CI 节省

- 仅修改 `catalog/apps.json`：GitHub Actions 只运行 **Validate Online Catalog**，不调用 Gradle、不创建模拟器、不签名或发布 APK。
- 编辑 Kotlin、资源、AndroidManifest 或 APK 内置离线快照：触发原本 Android 构建与模拟器测试。
- CI 使用 Python 标准库校验重复名称、必填简介和 README 快照、HTTPS、官方仓库名、允许的安装模式以及高风险项目限制。
- App 下载在线目录后还会进行本地校验，成功后才原子替换缓存，遇到网络错误/无效 JSON 不覆盖上次有效目录。

## 应用入选要求自动核验

每次修改 `catalog/apps.json` 或审核脚本时，**Validate Online Catalog** 将运行 `python3 tools/catalog_audit.py`，逐项查询 GitHub 公开仓库信息，检查源码和许可证、公开版本发布页、近六个月维护情况以及安全扫描证据。结果在 GitHub Actions 的 **Job Summary** 中按应用列出，并通过 Warning / Error 注解提示。

- **FAIL**：已能确认仓库私有、已归档或不可用等不符合事项；仅显示警告，不阻止 CI 通过。
- **REVIEW**：缺少公开证据、接口不可用、许可证无法识别、近期缺乏有效维护证据或安全扫描结果尚不能证明无高危风险；给出警告，需人工复核，不冒充“已通过”。
- 公共接口的 Code Scanning 结果不等于完整安全报告；必须结合恶意软件检测、已知漏洞和人工核验。GitHub 以外的 GitLab / Codeberg 项目目前标记待核验。
- 本检查只依赖 Python 标准库和 GitHub API，**不会触发 Android 构建或 APK 发布**。

本地可以运行 `python3 tools/catalog_audit.py` 查看提示。自动审核只提示问题，不影响 CI 成功状态；仅在手动明确添加 `--strict` 时，已确认不符合的项目才会返回非零退出码。仓库 API 限流或网络故障只会生成待核验提示，不作为安全失败结论。

## 在线来源、权限与安全边界

目录只来自本项目 GitHub 的 `main/catalog/apps.json`，优先使用 `raw.githubusercontent.com`，失败时尝试 GitHub Contents API。HTTPS 及字段校验阻止异常地址和误配置；**网络访问 GitHub 仍可能在中国大陆受限**，因此必须保留离线兜底。

在线目录仅可修改**展示元数据**，不会修改 APK 可执行代码、系统权限或静默安装行为。直接下载安装仅适用于设置 `downloadMode: auto` 且拥有官方 GitHub Release 的普通项目；高风险提权项目只能保留外部链接。APK 下载继续遵循独立的官方 SHA-256 校验/镜像策略。

## 当前收录

新增 [Canta](https://github.com/samolego/Canta) 与 [Spotube](https://github.com/team-spotube/spotube)，连同既有条目共 **17 个应用**。Canta 需要 Shizuku 且误删系统组件有风险；Spotube 的官方 GitHub 仓库已迁移到 `team-spotube/spotube`，发行 APK 使用 `Spotube-android-all-arch.apk`。

[在线目录 JSON](../catalog/apps.json) · [提交目录修改](https://github.com/U0Axuan/android-china-setup/edit/main/catalog/apps.json)
