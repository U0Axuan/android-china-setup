package io.github.u0axuan.androidchinasetup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
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
private enum class AppTab(val title: String) {
    APPS("应用"), SPEED("测速"), SETTINGS("设置"), ABOUT("关于")
}

private val lightColors = lightColorScheme(
    primary = Color(0xFF195BC2),
    secondary = Color(0xFF426493),
    tertiary = Color(0xFF067C78),
    background = Color(0xFFF7F9FE)
)
private val darkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    secondary = Color(0xFFB1C7ED),
    tertiary = Color(0xFF80D8CF)
)

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColors
        else -> lightColors
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

class MainActivity : ComponentActivity() {
    private fun openWebsite(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("preferences", MODE_PRIVATE)
        val catalog = runCatching {
            val data = JSONArray(assets.open("apps.json").bufferedReader().use { it.readText() })
            (0 until data.length()).map { index ->
                val app = data.getJSONObject(index)
                CatalogApp(
                    app.getString("name"), app.getString("category"),
                    app.optBoolean("selected", false), app.optString("notice", ""),
                    app.optString("homepage", ""), app.optString("repository", "")
                )
            }
        }.getOrDefault(emptyList())
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.4.0-dev"
        setContent {
            AppTheme { Screen(catalog, prefs, version) }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen(catalog: List<CatalogApp>, prefs: SharedPreferences, version: String) {
        val downloader = remember { ApkDownloader(this) }
        val benchmarker = remember { SourceBenchmarker() }
        var tab by remember { mutableStateOf(AppTab.APPS) }
        var selected by remember {
            mutableStateOf(
                prefs.getStringSet("selected", null)?.toSet()
                    ?: catalog.filter { it.selectedByDefault }.map { it.name }.toSet()
            )
        }
        var sourceId by remember {
            mutableStateOf(prefs.getString("sourceId", DownloadSources.AUTO) ?: DownloadSources.AUTO)
        }
        var customSources by remember {
            mutableStateOf(DownloadSources.fromJson(prefs.getString("customSources", "[]") ?: "[]"))
        }
        var proxyEnabled by remember { mutableStateOf(prefs.getBoolean("proxyEnabled", false)) }
        var proxyType by remember { mutableStateOf(prefs.getString("proxyType", "HTTP") ?: "HTTP") }
        var proxyHost by remember { mutableStateOf(prefs.getString("proxyHost", "") ?: "") }
        var proxyPort by remember { mutableStateOf(prefs.getString("proxyPort", "") ?: "") }
        var active by remember { mutableStateOf(false) }
        var progress by remember { mutableIntStateOf(0) }
        var status by remember { mutableStateOf("请选择应用，点击下载并确认系统安装") }
        var downloadedFile by remember { mutableStateOf<File?>(null) }
        var updateFile by remember { mutableStateOf(false) }
        var speedTesting by remember { mutableStateOf(false) }
        var speedStatus by remember { mutableStateOf("点击“开始测速”检查官方下载与镜像响应延迟") }
        var probeResults by remember { mutableStateOf(mapOf<String, ProbeResult>()) }
        var checkingUpdate by remember { mutableStateOf(false) }
        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        var updateStatus by remember { mutableStateOf("尚未检查版本") }

        fun options() = NetworkOptions(proxyEnabled, proxyType,
            proxyHost.trim(), proxyPort.toIntOrNull() ?: 0)
        fun selection() = SourceSelection(sourceId, customSources.toList())
        fun selectSource(id: String) {
            sourceId = id
            prefs.edit().putString("sourceId", id).apply()
        }
        fun persistCustom(sources: List<MirrorSource>) {
            customSources = DownloadSources.uniqueCustomSources(sources)
            prefs.edit().putString("customSources",
                DownloadSources.toJson(customSources)).apply()
        }
        fun startDownload(repository: String, label: String, updating: Boolean = false) {
            if (active) return
            active = true
            progress = 0
            downloadedFile = null
            updateFile = false
            status = "获取 " + label + " 最新版本…"
            tab = if (updating) AppTab.ABOUT else AppTab.APPS
            val net = options()
            val sources = selection()
            Thread {
                try {
                    val asset = downloader.findLatestApk(repository, net)
                    runOnUiThread { status = DownloadSources.explain(asset, sources) }
                    var lastProgress = -1
                    val file = downloader.download(asset, net, sources,
                        { amount ->
                            if (amount != lastProgress) {
                                lastProgress = amount
                                runOnUiThread { progress = amount }
                            }
                        },
                        { text -> runOnUiThread { status = text } }
                    )
                    if (updating && !SelfUpdater.matchingSigner(this, file)) {
                        file.delete()
                        runOnUiThread {
                            status = "新版签名与当前安装包不一致，系统禁止覆盖升级。请等待同一签名的版本，或查看发布说明。"
                            active = false
                        }
                    } else {
                        runOnUiThread {
                            downloadedFile = file
                            updateFile = updating
                            status = if (updating) "更新包校验完成，请在系统安装器中确认更新"
                                     else "APK 已下载，请在系统安装器中确认安装"
                            active = false
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        status = "下载失败：" + (e.message ?: "网络异常")
                        active = false
                    }
                }
            }.start()
        }
        fun beginBenchmark() {
            if (speedTesting) return
            speedTesting = true
            probeResults = emptyMap()
            speedStatus = "获取官方测速样本…"
            val net = options()
            val sources = selection()
            Thread {
                try {
                    val asset = downloader.findLatestApk("zhanghai/MaterialFiles", net)
                    if (!DownloadSources.validatedDigest(asset)) {
                        runOnUiThread {
                            speedStatus = "测速样本没有官方 SHA-256；不能测试第三方镜像"
                            speedTesting = false
                        }
                        return@Thread
                    }
                    runOnUiThread { speedStatus = "正在测速，请查看下方结果…" }
                    var availableCount = 0
                    benchmarker.test(asset, net, sources) { result ->
                        if (result.latencyMs != null) availableCount++
                        runOnUiThread { probeResults = probeResults + (result.id to result) }
                    }
                    runOnUiThread {
                        speedStatus = "测速完成。可用 " + availableCount + " 个节点；速率为短时采样估算"
                        speedTesting = false
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        speedStatus = "测速失败：" + (e.message ?: "网络不可用") +
                            "；获取样本需要访问 GitHub API"
                        speedTesting = false
                    }
                }
            }.start()
        }
        fun checkUpdates() {
            if (checkingUpdate) return
            checkingUpdate = true
            updateStatus = "正在检查 GitHub 最新版本…"
            val net = options()
            Thread {
                try {
                    val latest = SelfUpdater.check(net)
                    runOnUiThread {
                        updateInfo = latest
                        updateStatus = if (SelfUpdater.isNewer(latest.version, version))
                            "发现新版本 " + latest.version
                        else "已是最新版本（" + version + "）"
                        checkingUpdate = false
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        updateStatus = "检查失败：" + (e.message ?: "无法连接 GitHub")
                        checkingUpdate = false
                    }
                }
            }.start()
        }
        LaunchedEffect(Unit) { checkUpdates() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = "Android China Setup Logo",
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Android China Setup",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    AppTab.values().forEach { item ->
                        val symbol = when (item) {
                            AppTab.APPS -> Icons.Default.Home
                            AppTab.SPEED -> Icons.Default.Refresh
                            AppTab.SETTINGS -> Icons.Default.Settings
                            AppTab.ABOUT -> Icons.Default.Info
                        }
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(symbol, contentDescription = item.title) },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    AppTab.APPS -> CatalogScreen(
                        catalog, selected, { name, chosen ->
                            selected = if (chosen) selected + name else selected - name
                            prefs.edit().putStringSet("selected", selected).apply()
                        },
                        { app -> startDownload(app.repository, app.name) },
                        { openWebsite(it) },
                        { tab = AppTab.SPEED },
                        status, active, progress, downloadedFile,
                        {
                            val file = downloadedFile
                            if (file != null) {
                                runCatching {
                                    if (!packageManager.canRequestPackageInstalls()) {
                                        downloader.openInstallSettings()
                                        status = "请允许“安装未知应用”，然后返回点击安装"
                                    } else downloader.requestInstall(file)
                                }.onFailure { status = "无法打开安装器：" + it.message }
                            }
                        }
                    )
                    AppTab.SPEED -> SourcesScreen(
                        sourceId, customSources, probeResults,
                        speedTesting, speedStatus, ::selectSource, ::persistCustom,
                        ::beginBenchmark, { openWebsite(it) }
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        proxyEnabled, { proxyEnabled = it; prefs.edit().putBoolean("proxyEnabled", it).apply() },
                        proxyType, { proxyType = it; prefs.edit().putString("proxyType", it).apply() },
                        proxyHost, { proxyHost = it; prefs.edit().putString("proxyHost", it).apply() },
                        proxyPort, { proxyPort = it.filter(Char::isDigit); prefs.edit().putString("proxyPort", proxyPort).apply() },
                        sourceId, { tab = AppTab.SPEED }
                    )
                    AppTab.ABOUT -> AboutScreen(
                        version, updateStatus, checkingUpdate,
                        updateInfo?.takeIf { SelfUpdater.isNewer(it.version, version) },
                        ::checkUpdates,
                        { startDownload(SelfUpdater.REPOSITORY, "Android China Setup", updating = true) },
                        { openWebsite(it) }, status, active, progress,
                        downloadedFile?.takeIf { updateFile },
                        {
                            val file = downloadedFile
                            if (file != null) {
                                runCatching {
                                    if (!packageManager.canRequestPackageInstalls()) {
                                        downloader.openInstallSettings()
                                        status = "请允许此应用安装更新，然后返回点击安装"
                                    } else downloader.requestInstall(file)
                                }.onFailure { status = "无法启动更新安装器：" + it.message }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogScreen(
    apps: List<CatalogApp>,
    selected: Set<String>,
    onChecked: (String, Boolean) -> Unit,
    onDownload: (CatalogApp) -> Unit,
    openUrl: (String) -> Unit,
    onSpeed: () -> Unit,
    status: String,
    busy: Boolean,
    progress: Int,
    ready: File?,
    install: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("为 Android 11+ 准备好你常用的工具",
                    style = MaterialTheme.typography.titleMedium)
                Text("已收藏 " + selected.size + " 项 · 仅逐项下载安装",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSpeed) { Text("下载源测速") }
                    Text(" · " + Build.MANUFACTURER + " " + Build.MODEL,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall)
                }
                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(progress.toString() + "%", style = MaterialTheme.typography.labelSmall)
                }
                if (ready != null && !busy) {
                    Button(onClick = install, modifier = Modifier.fillMaxWidth()) {
                        Text("安装已下载 APK（系统确认）")
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps, key = { it.name }) { app ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                                Text(app.category +
                                    if (app.notice.isNotEmpty()) " · " + app.notice else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(
                                checked = app.name in selected,
                                onCheckedChange = { onChecked(app.name, it) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { openUrl(app.homepage) }) {
                                Text("访问原项目")
                            }
                            if (app.repository.isNotBlank() && app.name != "GhostLock") {
                                Button(onClick = { onDownload(app) }, enabled = !busy) {
                                    Text("下载 APK")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesScreen(
    sourceId: String,
    customs: List<MirrorSource>,
    tests: Map<String, ProbeResult>,
    testing: Boolean,
    status: String,
    choose: (String) -> Unit,
    saveCustom: (List<MirrorSource>) -> Unit,
    test: () -> Unit,
    openUrl: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    val all = DownloadSources.presets + customs
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("下载源测速", style = MaterialTheme.typography.headlineSmall)
            Text("实时采样节点响应延迟与约 128KB 下载速率，支持手动选择",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = test, enabled = !testing, modifier = Modifier.fillMaxWidth()) {
                Text(if (testing) "正在测速…" else "开始测速")
            }
            Text(status, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "auto") {
                SourceRow(
                    "智能优选", "下载时自动选择可用节点",
                    sourceId == DownloadSources.AUTO, null, { choose(DownloadSources.AUTO) }
                )
            }
            item(key = "official") {
                SourceRow(
                    "GitHub 官方直连", "不经过第三方镜像",
                    sourceId == DownloadSources.OFFICIAL, tests[DownloadSources.OFFICIAL],
                    { choose(DownloadSources.OFFICIAL) }
                )
            }
            items(all, key = { it.id }) { mirror ->
                SourceRow(
                    mirror.name, mirror.prefix, sourceId == mirror.id,
                    tests[mirror.id], { choose(mirror.id) },
                    if (mirror.id.startsWith("custom-")) {
                        {
                            saveCustom(customs.filterNot { it.id == mirror.id })
                            if (sourceId == mirror.id) choose(DownloadSources.AUTO)
                        }
                    } else null
                )
            }
            item(key = "add-source") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("添加自定义加速源", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(name, { name = it.take(40) },
                            label = { Text("源名称") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(prefix, { prefix = it.trim().take(250) },
                            label = { Text("HTTPS 镜像前缀") },
                            placeholder = { Text("https://mirror.example.com") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            val validated = DownloadSources.validPrefix(prefix)
                            notice = when {
                                validated == null -> "请输入合法 HTTPS 域名（不允许账号、端口或查询参数）"
                                (all).any { DownloadSources.validPrefix(it.prefix) == validated } ->
                                    "该镜像地址已存在"
                                customs.size >= 12 -> "自定义源最多 12 个"
                                else -> {
                                    val item = MirrorSource(
                                        "custom-" + System.currentTimeMillis(),
                                        name.ifBlank { "自定义下载源" }, validated)
                                    saveCustom(customs + item)
                                    choose(item.id)
                                    name = ""
                                    prefix = ""
                                    "已添加：" + item.name
                                }
                            }
                        }) { Text("添加加速源") }
                        if (notice.isNotBlank()) Text(notice,
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { openUrl(DownloadSources.CATALOG_PAGE) }) {
                            Text("查看 HiWiki 加速源参考列表")
                        }
                        Text("测速通过 128KB 数据采样估算速率，不代表长期下载性能。第三方镜像仅在 GitHub 提供 SHA-256 时使用，下载后会强制校验。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    title: String,
    description: String,
    chosen: Boolean,
    result: ProbeResult?,
    select: () -> Unit,
    remove: (() -> Unit)? = null
) {
    Surface(color = if (chosen) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().clickable(onClick = select)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = chosen, onClick = select)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (result != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (result.latencyMs == null) "不可用"
                         else result.latencyMs.toString() + " ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (result.latencyMs == null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary)
                    if (result.throughputKBps != null) {
                        Text(String.format(java.util.Locale.US, "%.1f MB/s",
                                result.throughputKBps / 1024.0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (remove != null) TextButton(onClick = remove) { Text("删除") }
        }
    }
}

@Composable
private fun SettingsScreen(
    proxy: Boolean, setProxy: (Boolean) -> Unit,
    type: String, setType: (String) -> Unit,
    host: String, setHost: (String) -> Unit,
    port: String, setPort: (String) -> Unit,
    source: String, openSources: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Surface(shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(16.dp)) {
                Text("下载设置", style = MaterialTheme.typography.titleMedium)
                Text("当前源：" + source, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = openSources) { Text("配置下载加速镜像") }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自定义代理（默认关闭）",
                            style = MaterialTheme.typography.titleSmall)
                        Text("仅本应用的网络请求使用，不修改系统 VPN",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = proxy, onCheckedChange = setProxy)
                }
                if (proxy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = type == "HTTP",
                            onClick = { setType("HTTP") }, label = { Text("HTTP") })
                        FilterChip(selected = type == "SOCKS5",
                            onClick = { setType("SOCKS5") }, label = { Text("SOCKS5") })
                    }
                    OutlinedTextField(host, setHost, label = { Text("代理主机/IP") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(port, setPort, label = { Text("代理端口") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
        }
        Text("安全说明", style = MaterialTheme.typography.titleMedium)
        Text("不自动批量安装，不更改受保护系统设置；APK 安装和升级都需用户在系统安装器中确认。Android 11 起兼容。",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutScreen(
    version: String,
    updateStatus: String,
    checking: Boolean,
    available: UpdateInfo?,
    check: () -> Unit,
    download: () -> Unit,
    openUrl: (String) -> Unit,
    downloadStatus: String,
    downloading: Boolean,
    downloadProgress: Int,
    updateReady: File?,
    installUpdate: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(12.dp))
        Image(painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Android China Setup Logo",
            modifier = Modifier.size(104.dp))
        Text("Android China Setup", style = MaterialTheme.typography.headlineSmall)
        Text("v" + version + " · Android 11+",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("简洁、安全、开放的 Android 应用初始化助手",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("应用自更新", style = MaterialTheme.typography.titleMedium)
                Text(updateStatus, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = check, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                    Text(if (checking) "正在检查…" else "检查更新")
                }
                if (available != null) {
                    Text("新版：" + available.version + " · 下载后需系统确认安装")
                    Button(onClick = download, enabled = !downloading,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (downloading) "正在获取更新…" else "下载并安装更新")
                    }
                    if (downloading || updateReady != null || downloadStatus.contains("签名与当前")) {
                        Text(downloadStatus, style = MaterialTheme.typography.bodySmall)
                    }
                    if (downloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth())
                    }
                    if (updateReady != null && !downloading) {
                        Button(onClick = installUpdate, modifier = Modifier.fillMaxWidth()) {
                            Text("安装已验证的更新（系统确认）")
                        }
                    }
                }
                Text("启动时自动检查。升级必须使用与已安装包一致的签名；测试版采用临时 Debug 签名，不能保证跨构建覆盖安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedButton(
            onClick = { openUrl(SelfUpdater.PROJECT_URL) },
            modifier = Modifier.fillMaxWidth()) { Text("访问原项目（GitHub 源码）") }
        OutlinedButton(
            onClick = { openUrl(SelfUpdater.RELEASE_URL) },
            modifier = Modifier.fillMaxWidth()) { Text("查看发布版本与更新日志") }
        TextButton(onClick = {
            openUrl(SelfUpdater.PROJECT_URL + "/issues/new")
        }) { Text("反馈问题 / 推荐应用") }
    }
}
