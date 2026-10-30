package io.github.u0axuan.androidchinasetup

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {
    private fun openWebsite(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bundled = runCatching { Catalog.read(this) }.getOrDefault(emptyList())
        val apps = CatalogRepository.load(this, bundled)
        val prefs = getSharedPreferences("preferences", MODE_PRIVATE)
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.6.0-dev"
        setContent {
            AndroidChinaTheme {
                AppRoot(apps, prefs, version)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppRoot(initialApps: List<CatalogApp>, prefs: SharedPreferences, version: String) {
        var apps by remember { mutableStateOf(initialApps) }
        val downloader = remember { ApkDownloader(this) }
        val benchmark = remember { SourceBenchmarker() }
        var settingsOpen by remember { mutableStateOf(false) }
        var detail by remember { mutableStateOf<CatalogApp?>(null) }
        var downloadBusy by remember { mutableStateOf(false) }
        var downloadPercent by remember { mutableIntStateOf(0) }
        var downloadText by remember { mutableStateOf("") }
        var downloadedFile by remember { mutableStateOf<File?>(null) }
        var updateDownload by remember { mutableStateOf(false) }

        var sourceId by remember {
            mutableStateOf(prefs.getString("sourceId", DownloadSources.AUTO) ?: DownloadSources.AUTO)
        }
        var customs by remember {
            mutableStateOf(DownloadSources.fromJson(prefs.getString("customSources", "[]") ?: "[]"))
        }
        var proxy by remember { mutableStateOf(prefs.getBoolean("proxyEnabled", false)) }
        var proxyType by remember { mutableStateOf(prefs.getString("proxyType", "HTTP") ?: "HTTP") }
        var host by remember { mutableStateOf(prefs.getString("proxyHost", "") ?: "") }
        var port by remember { mutableStateOf(prefs.getString("proxyPort", "") ?: "") }

        var speedBusy by remember { mutableStateOf(false) }
        var speedText by remember { mutableStateOf("点击开始测速，检测下载源延迟和采样速率") }
        var probes by remember { mutableStateOf(mapOf<String, ProbeResult>()) }
        var updateBusy by remember { mutableStateOf(false) }
        var updateStatus by remember { mutableStateOf("尚未检查更新") }
        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        var catalogBusy by remember { mutableStateOf(false) }
        var catalogStatus by remember {
            mutableStateOf("已加载 ${apps.size} 个项目 · 可手动更新在线目录")
        }

        fun network() = NetworkOptions(proxy, proxyType, host.trim(), port.toIntOrNull() ?: 0)
        fun sources() = SourceSelection(sourceId, customs.toList())
        fun chooseSource(id: String) {
            sourceId = id
            prefs.edit().putString("sourceId", id).apply()
        }
        fun saveCustom(sources: List<MirrorSource>) {
            customs = DownloadSources.uniqueCustomSources(sources)
            prefs.edit().putString("customSources", DownloadSources.toJson(customs)).apply()
        }
        fun addCustom(source: MirrorSource): Boolean {
            val valid = DownloadSources.validPrefix(source.prefix) ?: return false
            if (customs.size >= 12 ||
                (DownloadSources.presets + customs).any {
                    DownloadSources.validPrefix(it.prefix) == valid
                }) return false
            saveCustom(customs + source.copy(prefix = valid))
            chooseSource(source.id)
            return true
        }
        fun removeCustom(id: String) {
            saveCustom(customs.filterNot { it.id == id })
            if (sourceId == id) chooseSource(DownloadSources.AUTO)
        }

        fun startDownload(app: CatalogApp?, updating: Boolean) {
            if (downloadBusy) return
            val repository = if (updating) SelfUpdater.REPOSITORY
                             else app?.repository.orEmpty()
            if (repository.isBlank()) return
            downloadBusy = true
            downloadPercent = 0
            downloadText = "正在获取官方发布信息…"
            downloadedFile = null
            updateDownload = updating
            val options = network()
            val chosen = sources()
            val preferred = if (updating) "" else app?.preferredApkRegex.orEmpty()
            detail = null
            if (updating) settingsOpen = true
            Thread {
                try {
                    val asset = downloader.findLatestApk(repository, options, preferred)
                    runOnUiThread { downloadText = DownloadSources.explain(asset, chosen) }
                    var previous = -1
                    val file = downloader.download(asset, options, chosen,
                        { value ->
                            if (value != previous) {
                                previous = value
                                runOnUiThread { downloadPercent = value }
                            }
                        },
                        { text -> runOnUiThread { downloadText = text } }
                    )
                    if (updating && !SelfUpdater.matchingSigner(this, file)) {
                        file.delete()
                        runOnUiThread {
                            downloadText = "更新包签名与当前安装版本不一致，已拒绝覆盖安装。"
                            downloadBusy = false
                        }
                    } else {
                        runOnUiThread {
                            downloadedFile = file
                            downloadText = "APK 下载完成，等待 Android 系统安装确认"
                            downloadBusy = false
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        downloadText = "下载失败：" + (e.message ?: "网络不可用")
                        downloadBusy = false
                    }
                }
            }.start()
        }

        fun installDownloaded() {
            val file = downloadedFile ?: return
            runCatching {
                if (!packageManager.canRequestPackageInstalls()) {
                    downloader.openInstallSettings()
                    downloadText = "请允许安装未知应用，然后返回点击安装"
                } else {
                    downloader.requestInstall(file)
                }
            }.onFailure { downloadText = "无法打开系统安装器：" + it.message }
        }

        fun startBenchmark() {
            if (speedBusy) return
            speedBusy = true
            probes = emptyMap()
            speedText = "准备测速样本…"
            val net = network()
            val source = sources()
            Thread {
                try {
                    val asset = downloader.findLatestApk("zhanghai/MaterialFiles", net)
                    if (!DownloadSources.validatedDigest(asset)) {
                        runOnUiThread {
                            speedText = "官方发布信息缺少 SHA-256，无法安全测试镜像"
                            speedBusy = false
                        }
                        return@Thread
                    }
                    runOnUiThread { speedText = "正在测试各节点…" }
                    var passed = 0
                    benchmark.test(asset, net, source) { result ->
                        if (result.latencyMs != null) passed++
                        runOnUiThread { probes = probes + (result.id to result) }
                    }
                    runOnUiThread {
                        speedText = "测速完成：$passed 个节点可用；速率为短时样本估算"
                        speedBusy = false
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        speedText = "测速失败：" + (e.message ?: "需要连接 GitHub API")
                        speedBusy = false
                    }
                }
            }.start()
        }

        fun checkUpdate() {
            if (updateBusy) return
            updateBusy = true
            updateStatus = "正在检查最新发布版本…"
            val net = network()
            Thread {
                try {
                    val info = SelfUpdater.check(net)
                    runOnUiThread {
                        updateInfo = info
                        updateStatus = when {
                            SelfUpdater.isNewer(info.version, version) ->
                                "发现新版本 " + info.version
                            version.contains("-dev") ->
                                "当前为开发版 " + version + "；公开版 " + info.version
                            else -> "已是最新版本（" + version + "）"
                        }
                        updateBusy = false
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        updateStatus = "检查更新失败：" + (e.message ?: "网络错误")
                        updateBusy = false
                    }
                }
            }.start()
        }

        fun refreshCatalog(manual: Boolean) {
            if (catalogBusy) return
            if (!manual && !CatalogRepository.shouldAutoRefresh(this)) return
            catalogBusy = true
            catalogStatus = "正在从 GitHub 更新目录…"
            val net = network()
            Thread {
                try {
                    val result = CatalogRepository.refresh(this, net)
                    runOnUiThread {
                        apps = result.apps
                        catalogStatus = result.message
                        catalogBusy = false
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        catalogStatus = e.message ?: "目录更新失败，已保留离线内容"
                        catalogBusy = false
                    }
                }
            }.start()
        }

        LaunchedEffect(Unit) {
            checkUpdate()
            refreshCatalog(false)
        }
        BackHandler(settingsOpen) { settingsOpen = false }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!settingsOpen) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = "Android China Setup Logo",
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (settingsOpen) "设置" else "Android China Setup",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    navigationIcon = {
                        if (settingsOpen) {
                            IconButton(onClick = { settingsOpen = false }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回应用列表")
                            }
                        }
                    },
                    actions = {
                        if (!settingsOpen) {
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "设置")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (!settingsOpen) {
                    CatalogHome(
                        apps = apps, busy = downloadBusy, status = downloadText,
                        percent = downloadPercent,
                        ready = downloadedFile != null && !updateDownload,
                        onInstall = ::installDownloaded,
                        onDetails = { detail = it },
                        onDownload = { startDownload(it, false) }
                    )
                } else {
                    SettingsPage(
                        model = SettingsUiState(
                            catalogCount = apps.size,
                            catalogStatus = catalogStatus,
                            catalogBusy = catalogBusy,
                            selectedSource = sourceId, customSources = customs,
                            probes = probes, speedText = speedText, speedBusy = speedBusy,
                            proxyEnabled = proxy, proxyType = proxyType,
                            proxyHost = host, proxyPort = port, version = version,
                            updateText = updateStatus, updateBusy = updateBusy,
                            newVersion = updateInfo,
                            downloadBusy = downloadBusy && updateDownload,
                            downloadStatus = if (updateDownload) downloadText else "",
                            downloadPercent = downloadPercent,
                            updateReady = updateDownload && downloadedFile != null
                        ),
                        refreshCatalog = { refreshCatalog(true) },
                        chooseSource = ::chooseSource,
                        addSource = ::addCustom, removeSource = ::removeCustom,
                        startSpeedTest = ::startBenchmark,
                        setProxy = {
                            proxy = it
                            prefs.edit().putBoolean("proxyEnabled", it).apply()
                        },
                        setProxyType = {
                            proxyType = it
                            prefs.edit().putString("proxyType", it).apply()
                        },
                        setProxyHost = {
                            host = it
                            prefs.edit().putString("proxyHost", it).apply()
                        },
                        setProxyPort = {
                            port = it.filter(Char::isDigit)
                            prefs.edit().putString("proxyPort", port).apply()
                        },
                        checkUpdate = ::checkUpdate,
                        downloadUpdate = { startDownload(null, true) },
                        installUpdate = ::installDownloaded,
                        openUrl = ::openWebsite
                    )
                }
                val app = detail
                if (app != null && !settingsOpen) {
                    CatalogDetail(
                        app = app, busy = downloadBusy,
                        onDismiss = { detail = null },
                        onDownload = { startDownload(it, false) },
                        openUrl = ::openWebsite
                    )
                }
            }
        }
    }
}
