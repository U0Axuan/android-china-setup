package io.github.u0axuan.androidchinasetup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.io.File

data class CatalogApp(
    val name: String,
    val category: String,
    val selectedByDefault: Boolean,
    val notice: String,
    val homepage: String,
    val repository: String
)

class MainActivity : ComponentActivity() {
    private fun openWebsite(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("preferences", MODE_PRIVATE)
        val catalog = runCatching {
            val data = JSONArray(assets.open("apps.json").bufferedReader().use { it.readText() })
            (0 until data.length()).map { index ->
                val app = data.getJSONObject(index)
                CatalogApp(app.getString("name"), app.getString("category"),
                    app.optBoolean("selected", false), app.optString("notice", ""),
                    app.optString("homepage", ""), app.optString("repository", ""))
            }
        }.getOrDefault(emptyList())
        val downloader = ApkDownloader(this)

        setContent {
            MaterialTheme {
                var selected by remember {
                    mutableStateOf(prefs.getStringSet("selected", null)?.toSet()
                        ?: catalog.filter { it.selectedByDefault }.map { it.name }.toSet())
                }
                var proxyEnabled by remember { mutableStateOf(prefs.getBoolean("proxyEnabled", false)) }
                var proxyType by remember { mutableStateOf(prefs.getString("proxyType", "HTTP") ?: "HTTP") }
                var proxyHost by remember { mutableStateOf(prefs.getString("proxyHost", "") ?: "") }
                var proxyPort by remember { mutableStateOf(prefs.getString("proxyPort", "") ?: "") }
                var sourceId by remember {
                    mutableStateOf(prefs.getString("sourceId", DownloadSources.AUTO) ?: DownloadSources.AUTO)
                }
                var customSources by remember {
                    mutableStateOf(DownloadSources.fromJson(prefs.getString("customSources", "[]") ?: "[]"))
                }
                var newSourceName by remember { mutableStateOf("") }
                var newSourcePrefix by remember { mutableStateOf("") }
                var showMirrorSettings by remember { mutableStateOf(false) }
                var mirrorNotice by remember { mutableStateOf("") }
                var active by remember { mutableStateOf(false) }
                var progress by remember { mutableIntStateOf(0) }
                var status by remember { mutableStateOf("请选择应用，逐项下载并由系统确认安装") }
                var downloadedFile by remember { mutableStateOf<File?>(null) }

                fun networkOptions() = NetworkOptions(
                    proxyEnabled, proxyType, proxyHost, proxyPort.toIntOrNull() ?: 0)
                fun selectSource(id: String) {
                    sourceId = id
                    prefs.edit().putString("sourceId", id).apply()
                }
                fun persistCustoms(sources: List<MirrorSource>) {
                    customSources = DownloadSources.uniqueCustomSources(sources)
                    prefs.edit().putString("customSources", DownloadSources.toJson(customSources)).apply()
                }
                fun beginDownload(app: CatalogApp) {
                    if (active) return
                    if (app.repository.isBlank()) {
                        openWebsite(app.homepage)
                        return
                    }
                    active = true
                    progress = 0
                    downloadedFile = null
                    status = "正在获取 " + app.name + " 的 GitHub Release 信息"
                    val options = networkOptions()
                    val sources = SourceSelection(sourceId, customSources.toList())
                    Thread {
                        try {
                            val asset = downloader.findLatestApk(app.repository, options)
                            runOnUiThread { status = DownloadSources.explain(asset, sources) }
                            val file = downloader.download(asset, options, sources,
                                { value -> runOnUiThread { progress = value } },
                                { message -> runOnUiThread { status = message } }
                            )
                            runOnUiThread {
                                downloadedFile = file
                                status = "下载成功：请授权安装来源，并在系统安装器中确认"
                                active = false
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                active = false
                                status = "下载失败：" + (e.message ?: "未知错误") + "；可访问项目官网"
                            }
                        }
                    }.start()
                }

                Scaffold { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            Column {
                                Text("Android China Setup",
                                    style = MaterialTheme.typography.headlineSmall)
                                Text("Android 11+ · 手机端应用中心 · V0.3 开发版",
                                    style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                Text("设备：" + Build.MANUFACTURER + " " + Build.MODEL +
                                    " · Android " + Build.VERSION.RELEASE)
                                Spacer(Modifier.height(6.dp))
                                Text("下载源：" + when (sourceId) {
                                    DownloadSources.AUTO -> "智能优选"
                                    DownloadSources.OFFICIAL -> "GitHub 官方"
                                    else -> (DownloadSources.presets + customSources)
                                        .find { it.id == sourceId }?.name ?: "未找到"
                                }, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { showMirrorSettings = !showMirrorSettings }) {
                                    Text(if (showMirrorSettings) "收起下载源设置"
                                         else "配置下载加速镜像（可选）")
                                }
                                if (showMirrorSettings) {
                                    Text("选择下载源", style = MaterialTheme.typography.titleMedium)
                                    Text("可切换下列社区加速节点，或自行添加 HTTPS 前缀。",
                                        style = MaterialTheme.typography.bodySmall)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = sourceId == DownloadSources.AUTO,
                                            onClick = { selectSource(DownloadSources.AUTO) })
                                        Text("智能优选（测速后选择可用源）")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = sourceId == DownloadSources.OFFICIAL,
                                            onClick = { selectSource(DownloadSources.OFFICIAL) })
                                        Text("GitHub 官方直连")
                                    }
                                    (DownloadSources.presets + customSources).forEach { mirror ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = sourceId == mirror.id,
                                                onClick = { selectSource(mirror.id) })
                                            Column(Modifier.weight(1f)) {
                                                Text(mirror.name)
                                                Text(mirror.prefix, style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (mirror.id.startsWith("custom-")) {
                                                TextButton(onClick = {
                                                    persistCustoms(customSources.filterNot { it.id == mirror.id })
                                                    if (sourceId == mirror.id) selectSource(DownloadSources.AUTO)
                                                }) { Text("删除") }
                                            }
                                        }
                                    }
                                    TextButton(onClick = {
                                        openWebsite(DownloadSources.CATALOG_PAGE)
                                    }) { Text("打开 hiwiki 加速源参考列表") }
                                    OutlinedTextField(
                                        value = newSourceName,
                                        onValueChange = { newSourceName = it.take(40) },
                                        label = { Text("自定义源名称") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = newSourcePrefix,
                                        onValueChange = { newSourcePrefix = it.trim().take(250) },
                                        label = { Text("自定义 HTTPS 镜像前缀") },
                                        placeholder = { Text("https://mirror.example.com") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(onClick = {
                                        val prefix = DownloadSources.validPrefix(newSourcePrefix)
                                        mirrorNotice = when {
                                            prefix == null -> "只接受 HTTPS 域名地址，不允许账号、端口或查询参数"
                                            (DownloadSources.presets + customSources).any {
                                                DownloadSources.validPrefix(it.prefix) == prefix
                                            } -> "该下载源已存在"
                                            customSources.size >= 12 -> "最多保存 12 个自定义下载源"
                                            else -> {
                                                val entry = MirrorSource(
                                                    "custom-" + System.currentTimeMillis(),
                                                    newSourceName.ifBlank { "自定义下载源" },
                                                    prefix
                                                )
                                                persistCustoms(customSources + entry)
                                                selectSource(entry.id)
                                                newSourceName = ""
                                                newSourcePrefix = ""
                                                "已添加并选择：" + entry.name
                                            }
                                        }
                                    }) { Text("添加加速源") }
                                    if (mirrorNotice.isNotBlank()) {
                                        Text(mirrorNotice, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("第三方镜像并非 GitHub 官方服务；只有官方 Release 提供 SHA-256 时才允许镜像下载，完成后必须校验。镜像有权获知所下载的公开文件。",
                                        style = MaterialTheme.typography.bodySmall)
                                    HorizontalDivider()
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("自定义代理（默认关闭）", modifier = Modifier.weight(1f))
                                    Switch(checked = proxyEnabled, onCheckedChange = {
                                        proxyEnabled = it
                                        prefs.edit().putBoolean("proxyEnabled", it).apply()
                                    })
                                }
                                if (proxyEnabled) {
                                    Row {
                                        FilterChip(selected = proxyType == "HTTP",
                                            onClick = {
                                                proxyType = "HTTP"
                                                prefs.edit().putString("proxyType", proxyType).apply()
                                            }, label = { Text("HTTP") })
                                        Spacer(Modifier.width(8.dp))
                                        FilterChip(selected = proxyType == "SOCKS5",
                                            onClick = {
                                                proxyType = "SOCKS5"
                                                prefs.edit().putString("proxyType", proxyType).apply()
                                            }, label = { Text("SOCKS5") })
                                    }
                                    OutlinedTextField(value = proxyHost,
                                        onValueChange = {
                                            proxyHost = it
                                            prefs.edit().putString("proxyHost", it).apply()
                                        }, label = { Text("代理主机/IP") }, singleLine = true)
                                    OutlinedTextField(value = proxyPort,
                                        onValueChange = {
                                            proxyPort = it.filter { char -> char.isDigit() }
                                            prefs.edit().putString("proxyPort", proxyPort).apply()
                                        }, label = { Text("端口") }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                }
                                Text(status, style = MaterialTheme.typography.bodySmall)
                                if (active) LinearProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                downloadedFile?.let { file ->
                                    Button(onClick = {
                                        runCatching {
                                            if (!packageManager.canRequestPackageInstalls()) {
                                                downloader.openInstallSettings()
                                                status = "请允许本应用安装未知应用，然后返回点击安装"
                                            } else {
                                                downloader.requestInstall(file)
                                            }
                                        }.onFailure { status = "安装器启动失败：" + it.message }
                                    }) { Text("安装已下载 APK（系统确认）") }
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("应用清单（已选 " + selected.size + " 项）")
                                Text("单个应用按需下载，不执行自动批量安装",
                                    style = MaterialTheme.typography.bodySmall)
                                HorizontalDivider()
                            }
                        }
                        items(catalog) { app ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(app.name, style = MaterialTheme.typography.titleMedium)
                                        Text(app.category +
                                            if (app.notice.isNotEmpty()) " · " + app.notice else "",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Checkbox(checked = app.name in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + app.name else selected - app.name
                                            prefs.edit().putStringSet("selected", selected).apply()
                                        })
                                }
                                Row {
                                    TextButton(onClick = { openWebsite(app.homepage) }) {
                                        Text("项目官网")
                                    }
                                    if (app.repository.isNotBlank() && app.name != "GhostLock") {
                                        TextButton(onClick = { beginDownload(app) },
                                            enabled = !active) { Text("下载 APK") }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
