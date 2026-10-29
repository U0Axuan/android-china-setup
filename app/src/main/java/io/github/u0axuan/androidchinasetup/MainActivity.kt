package io.github.u0axuan.androidchinasetup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("preferences", MODE_PRIVATE)
        val catalog = runCatching {
            val raw = assets.open("apps.json").bufferedReader().use { it.readText() }
            val data = JSONArray(raw)
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
                var selected by remember { mutableStateOf(
                    prefs.getStringSet("selected", null)?.toSet()
                        ?: catalog.filter { it.selectedByDefault }.map { it.name }.toSet()
                ) }
                var proxyEnabled by remember { mutableStateOf(prefs.getBoolean("proxyEnabled", false)) }
                var proxyType by remember { mutableStateOf(prefs.getString("proxyType", "HTTP") ?: "HTTP") }
                var proxyHost by remember { mutableStateOf(prefs.getString("proxyHost", "") ?: "") }
                var proxyPort by remember { mutableStateOf(prefs.getString("proxyPort", "") ?: "") }
                var mirrorPrefix by remember { mutableStateOf(prefs.getString("mirrorPrefix", "") ?: "") }
                var showMirrorSettings by remember { mutableStateOf(false) }
                var active by remember { mutableStateOf(false) }
                var progress by remember { mutableIntStateOf(0) }
                var status by remember { mutableStateOf("请选择应用，点击下载或查看项目官网") }
                var downloadedFile by remember { mutableStateOf<File?>(null) }
                fun networkOptions() = NetworkOptions(
                    proxyEnabled, proxyType, proxyHost, proxyPort.toIntOrNull() ?: 0
                )
                fun beginDownload(app: CatalogApp) {
                    if (active) return
                    if (app.repository.isBlank()) {
                        openWebsite(app.homepage)
                        return
                    }
                    active = true
                    progress = 0
                    downloadedFile = null
                    status = "正在解析 " + app.name + " 的 GitHub 发布信息"
                    val options = networkOptions()
                    val mirror = mirrorPrefix
                    Thread {
                        try {
                            val asset = downloader.findLatestApk(app.repository, options)
                            runOnUiThread { status = "正在下载 " + asset.name }
                            runOnUiThread { status = DownloadSources.explain(asset, mirror) }
                            val file = downloader.download(asset, options, mirror,
                                { value -> runOnUiThread { progress = value } },
                                { message -> runOnUiThread { status = message } }
                            )
                            runOnUiThread {
                                downloadedFile = file
                                status = "下载完成：请确认安装来源权限，点击安装"
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
                    Column(Modifier.padding(padding).padding(16.dp)) {
                        Text("Android China Setup", style = MaterialTheme.typography.headlineSmall)
                        Text("手机端应用中心 · V0.2 开发版")
                        Spacer(Modifier.height(8.dp))
                        Text("设备：" + Build.MANUFACTURER + " " + Build.MODEL +
                            " · Android " + Build.VERSION.RELEASE)
                        Text("官方下载优先；可信 SHA-256 验证后允许镜像故障切换",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { showMirrorSettings = !showMirrorSettings }) {
                            Text(if (showMirrorSettings) "收起下载源设置" else "配置下载加速镜像（可选）")
                        }
                        if (showMirrorSettings) {
                            OutlinedTextField(
                                value = mirrorPrefix,
                                onValueChange = {
                                    mirrorPrefix = it.trim()
                                    prefs.edit().putString("mirrorPrefix", mirrorPrefix).apply()
                                },
                                label = { Text("HTTPS 镜像前缀") },
                                placeholder = { Text("https://mirror.example.com") },
                                singleLine = true
                            )
                            Text("仅在 GitHub 提供 SHA-256 摘要时允许镜像切换。无可信校验信息时只使用官方源。",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            Text("自定义代理（默认关闭）", modifier = Modifier.weight(1f))
                            Switch(checked = proxyEnabled, onCheckedChange = {
                                proxyEnabled = it
                                prefs.edit().putBoolean("proxyEnabled", it).apply()
                            })
                        }
                        if (proxyEnabled) {
                            Row {
                                FilterChip(selected = proxyType == "HTTP",
                                    onClick = { proxyType = "HTTP"; prefs.edit().putString("proxyType",proxyType).apply() },
                                    label = { Text("HTTP") })
                                Spacer(Modifier.width(8.dp))
                                FilterChip(selected = proxyType == "SOCKS5",
                                    onClick = { proxyType = "SOCKS5"; prefs.edit().putString("proxyType",proxyType).apply() },
                                    label = { Text("SOCKS5") })
                            }
                            OutlinedTextField(value = proxyHost, onValueChange = {
                                proxyHost = it; prefs.edit().putString("proxyHost", it).apply()
                            }, label = { Text("代理主机/IP") }, singleLine = true)
                            OutlinedTextField(value = proxyPort, onValueChange = {
                                proxyPort = it.filter { c -> c.isDigit() }
                                prefs.edit().putString("proxyPort", proxyPort).apply()
                            }, label = { Text("端口") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        if (active) LinearProgressIndicator(
                            progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                        downloadedFile?.let { file ->
                            Button(onClick = {
                                runCatching {
                                    if (Build.VERSION.SDK_INT >= 26 &&
                                        !packageManager.canRequestPackageInstalls()) {
                                        downloader.openInstallSettings()
                                        status = "请允许本应用安装未知应用，然后返回点击安装"
                                    } else {
                                        downloader.requestInstall(file)
                                    }
                                }.onFailure { status = "安装器启动失败：" + it.message }
                            }) { Text("安装已下载 APK（系统确认）") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("应用清单（已选 " + selected.size + " 项）")
                        HorizontalDivider()
                        LazyColumn {
                            items(catalog) { app ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Row {
                                        Column(Modifier.weight(1f)) {
                                            Text(app.name, style = MaterialTheme.typography.titleMedium)
                                            Text(app.category +
                                                if (app.notice.isNotEmpty()) " · " + app.notice else "",
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                        Checkbox(checked = app.name in selected, onCheckedChange = { checked ->
                                            selected = if (checked) selected + app.name else selected - app.name
                                            prefs.edit().putStringSet("selected", selected).apply()
                                        })
                                    }
                                    Row {
                                        TextButton(onClick = { openWebsite(app.homepage) }) { Text("项目官网") }
                                        if (app.repository.isNotBlank() && app.name != "GhostLock") {
                                            TextButton(onClick = { beginDownload(app) }, enabled = !active) {
                                                Text("下载 APK")
                                            }
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
}
