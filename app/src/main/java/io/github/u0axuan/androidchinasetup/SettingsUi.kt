package io.github.u0axuan.androidchinasetup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SettingsUiState(
    val selectedSource: String,
    val customSources: List<MirrorSource>,
    val probes: Map<String, ProbeResult>,
    val speedText: String,
    val speedBusy: Boolean,
    val proxyEnabled: Boolean,
    val proxyType: String,
    val proxyHost: String,
    val proxyPort: String,
    val version: String,
    val updateText: String,
    val updateBusy: Boolean,
    val newVersion: UpdateInfo?,
    val downloadBusy: Boolean,
    val downloadStatus: String,
    val downloadPercent: Int,
    val updateReady: Boolean
)

@Composable
fun SettingsPage(
    model: SettingsUiState,
    chooseSource: (String) -> Unit,
    addSource: (MirrorSource) -> Boolean,
    removeSource: (String) -> Unit,
    startSpeedTest: () -> Unit,
    setProxy: (Boolean) -> Unit,
    setProxyType: (String) -> Unit,
    setProxyHost: (String) -> Unit,
    setProxyPort: (String) -> Unit,
    checkUpdate: () -> Unit,
    downloadUpdate: () -> Unit,
    installUpdate: () -> Unit,
    openUrl: (String) -> Unit
) {
    var customName by remember { mutableStateOf("") }
    var customPrefix by remember { mutableStateOf("") }
    var addNotice by remember { mutableStateOf("") }
    val available = DownloadSources.presets + model.customSources
    val newer = model.newVersion?.takeIf {
        SelfUpdater.isNewer(it.version, model.version)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "settings-title") {
            Column(Modifier.padding(bottom = 6.dp)) {
                Text("设置", style = MaterialTheme.typography.headlineSmall)
                Text("下载源、测速、代理与版本更新",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "speed-card") {
            SettingsPanel("下载加速与测速") {
                Text("测速从 GitHub Release 读取样本，显示连接延迟及约 128 KB 采样速率。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = startSpeedTest, enabled = !model.speedBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (model.speedBusy) "正在测速…" else "开始测速")
                }
                Text(model.speedText,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        item(key = "source-auto") {
            MirrorItem("智能优选", "按节点可用性自动择优",
                model.selectedSource == DownloadSources.AUTO, null,
                { chooseSource(DownloadSources.AUTO) })
        }
        item(key = "source-official") {
            MirrorItem("GitHub 官方直连", "不使用第三方下载镜像",
                model.selectedSource == DownloadSources.OFFICIAL,
                model.probes[DownloadSources.OFFICIAL],
                { chooseSource(DownloadSources.OFFICIAL) })
        }
        items(available, key = { "mirror:" + it.id }) { mirror ->
            MirrorItem(mirror.name, mirror.prefix,
                model.selectedSource == mirror.id, model.probes[mirror.id],
                { chooseSource(mirror.id) },
                if (mirror.id.startsWith("custom-")) { { removeSource(mirror.id) } }
                else null
            )
        }
        item(key = "add-source") {
            SettingsPanel("添加自定义加速源") {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it.take(40) },
                    label = { Text("源名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customPrefix,
                    onValueChange = { customPrefix = it.trim().take(250) },
                    label = { Text("HTTPS 镜像前缀") },
                    placeholder = { Text("https://mirror.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    val valid = DownloadSources.validPrefix(customPrefix)
                    addNotice = when {
                        valid == null -> "请输入合法 HTTPS 源地址"
                        available.any { DownloadSources.validPrefix(it.prefix) == valid } ->
                            "该源已经存在"
                        model.customSources.size >= 12 -> "最多保留 12 个自定义源"
                        else -> {
                            val newSource = MirrorSource(
                                "custom-" + System.currentTimeMillis(),
                                customName.ifBlank { "自定义下载源" }, valid
                            )
                            if (addSource(newSource)) {
                                customName = ""
                                customPrefix = ""
                                "已添加并选中 " + newSource.name
                            } else "添加失败"
                        }
                    }
                }) { Text("添加加速源") }
                if (addNotice.isNotBlank()) {
                    Text(addNotice, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { openUrl(DownloadSources.CATALOG_PAGE) }) {
                    Text("HiWiki 加速源参考列表")
                }
                Text("第三方镜像仅在有官方 SHA-256 的情况下启用；测速是短时估算，不能保证长期速度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "proxy") {
            SettingsPanel("网络代理") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自定义代理（默认关闭）")
                        Text("仅本应用使用，不修改系统 VPN",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = model.proxyEnabled, onCheckedChange = setProxy)
                }
                if (model.proxyEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = model.proxyType == "HTTP",
                            onClick = { setProxyType("HTTP") }, label = { Text("HTTP") })
                        FilterChip(selected = model.proxyType == "SOCKS5",
                            onClick = { setProxyType("SOCKS5") }, label = { Text("SOCKS5") })
                    }
                    OutlinedTextField(model.proxyHost, setProxyHost,
                        label = { Text("代理主机/IP") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(model.proxyPort, setProxyPort,
                        label = { Text("代理端口") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "about") {
            SettingsPanel("关于与自更新") {
                Text("Android China Setup v" + model.version)
                Text("Android 11+ · Material 3 · 开源项目索引",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(model.updateText, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = checkUpdate, enabled = !model.updateBusy,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (model.updateBusy) "正在检查…" else "检查更新")
                }
                if (newer != null) {
                    Button(onClick = downloadUpdate, enabled = !model.downloadBusy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (model.downloadBusy) "正在下载更新…" else
                            "下载更新 " + newer.version)
                    }
                }
                if (model.downloadBusy || model.updateReady ||
                    model.downloadStatus.contains("签名")) {
                    Text(model.downloadStatus, style = MaterialTheme.typography.bodySmall)
                }
                if (model.downloadBusy) {
                    LinearProgressIndicator(
                        progress = { model.downloadPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (model.updateReady) {
                    Button(onClick = installUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text("安装已校验更新（系统确认）")
                    }
                }
                Text("跨版本覆盖安装必须使用兼容签名；CI Debug 签名可能变化。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = { openUrl(SelfUpdater.PROJECT_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("访问原项目（GitHub）") }
                TextButton(onClick = { openUrl(SelfUpdater.RELEASE_URL) }) {
                    Text("查看 Release 与更新日志")
                }
                TextButton(onClick = {
                    openUrl(SelfUpdater.PROJECT_URL + "/issues/new")
                }) { Text("反馈问题 / 推荐应用") }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            }
        )
    }
}

@Composable
private fun MirrorItem(
    name: String, address: String, selected: Boolean,
    result: ProbeResult?, onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(address, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (result != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (result.latencyMs == null) "不可用"
                         else result.latencyMs.toString() + " ms",
                        style = MaterialTheme.typography.labelMedium)
                    if (result.throughputKBps != null) {
                        Text(String.format(java.util.Locale.US, "%.1f MB/s",
                            result.throughputKBps / 1024.0),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}
