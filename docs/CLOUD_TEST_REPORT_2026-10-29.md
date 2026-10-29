# Android China Setup 云端模拟器测试报告

- 测试日期：2026-10-29（UTC+8）
- 测试对象：Android China Setup V0.2 开发版（GitHub Actions 构建 APK）
- 测试代码：[`0a196282`](https://github.com/U0Axuan/android-china-setup/commit/0a19628260ab5d5d756d79bde957b85cc0b4db4c)
- 环境：GitHub Actions Ubuntu hosted runner / Google APIs Android 15（API 35）x86_64 模拟器
- 自动化脚本：[`tests/cloud_smoke.py`](../tests/cloud_smoke.py)
- CI 工作流：[`.github/workflows/emulator-test.yml`](../.github/workflows/emulator-test.yml)
- [完成的测试运行 #40848698121](https://github.com/U0Axuan/android-china-setup/actions/runs/40848698121)

## 结果：33 / 33 条自动检查通过

| 项目 | 结论 |
|---|---|
| APK 实际安装、启动、进程存活 | 通过 |
| 设备信息展示 | 通过 |
| 内置 11 项应用清单逐项展示 | 通过 |
| 自定义代理默认关闭 | 通过 |
| HTTP / SOCKS5 配置控件可操作 | 通过（仅 UI 和设置持久化） |
| HTTPS 镜像配置控件及设置持久化 | 通过（仅 UI 和设置持久化） |
| 关闭、重新启动后界面正常 | 通过 |
| Material Files 官方 GitHub Release APK 真实下载 | 通过 |
| 下载的 APK SHA-256 与 GitHub Release 官方摘要一致 | 通过：`f2e7f028fbddf51d7ee90ace27a1ae4b247511e12514d1def6c9e52435feac40` |
| Android 系统安装程序弹窗 | 通过 |
| 点击系统 Install 之后完成安装 | 通过（界面显示 App installed） |
| Android 包管理器确认 `me.zhanghai.android.files` 存在 | 通过 |
| 完成安装后返回本应用 | 通过 |

详细的 33 条检查结果，以及原始 UI 层级、截图和 logcat，参见上述 GitHub Actions 运行页面的 **Artifacts / android-emulator-test-results**。该运行还提供 **android-emulator-tested-apk**，确保可以下载本轮准确测试的 APK。

## 测试边界与剩余风险

这些结果**不能证明所有功能、所有第三方项目和所有 Android 设备均可用**：

1. 模拟器是 Android 15 / x86_64，**未覆盖 ARM64 真机**，也未覆盖 HyperOS、LineageOS 等 ROM 差异。
2. 代理配置的开关、选项与持久化已测试，但**实际 HTTP/SOCKS5 代理链路转发未做端到端测试**。
3. HTTPS 镜像编辑和保存已测试，但**镜像下载、官方源故障切换、镜像 SHA 拒绝策略尚未做网络端到端测试**；尚未内置可信国内测速镜像。
4. 真正下载安装验证的是 **Material Files**；其余项目的安装、应用内使用和兼容性不能由此推断。Via、F-Droid 为官网入口；GhostLock 故意禁止自动运行。
5. 当前版本尚未实现完整的受保护系统设置引导或设定默认输入法，这些不属于通过项。
6. 未模拟中国大陆运营商网络、代理网络受限情况，也未覆盖弱网、断流和长时间后台运行。
7. 测试 APK 属于 Debug 构建，不是经过正式发行签名、灰度验证和外部安全审查的 Release。

## 自动复测

仓库的 Android 模拟器工作流随 `app/**`、测试脚本或工作流修改自动运行，也可在 GitHub Actions 页面手动执行。正式发布前建议增加 API 29/34 和 ARM64 真机测试，复测实际代理转发、可信镜像回退及签名一致性。
