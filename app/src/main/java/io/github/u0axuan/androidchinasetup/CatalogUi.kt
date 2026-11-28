package io.github.u0axuan.androidchinasetup

import android.os.Build
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val lightTheme = lightColorScheme(
    primary = Color(0xFF087C75), secondary = Color(0xFF435C78),
    background = Color(0xFFF6F8FA), surface = Color(0xFFFAFCFE))
private val darkTheme = darkColorScheme(
    primary = Color(0xFF75E6C7), secondary = Color(0xFF9CC6E8),
    background = Color(0xFF0B121A), surface = Color(0xFF101923))

@Composable
fun AndroidChinaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkTheme
        else -> lightTheme
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)),
        content = content
    )
}

/** One keyed LazyColumn. No network or README parsing occurs during scrolling. */
@Composable
fun CatalogHome(
    apps: List<CatalogApp>, busy: Boolean, status: String, percent: Int,
    ready: Boolean, metadata: Map<String, UpstreamInfo>, onInstall: () -> Unit,
    onDetails: (CatalogApp) -> Unit, onDownload: (CatalogApp) -> Unit
) {
    val groups = remember(apps) { Catalog.grouped(apps) }
    LazyColumn(
        modifier = Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "intro", contentType = "intro") {
            Column(Modifier.padding(bottom = 6.dp)) {
                Text("精选 Android 开源应用", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text("${apps.size} 个项目 · ${groups.size} 个分类 · Android 11+",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy || ready || status.contains("失败") || status.contains("签名")) {
            item(key = "task", contentType = "status") {
                Surface(color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("下载任务", fontWeight = FontWeight.SemiBold)
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        if (busy) {
                            LinearProgressIndicator(
                                progress = { percent.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth())
                            Text("$percent%", style = MaterialTheme.typography.labelSmall)
                        }
                        if (ready && !busy) {
                            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                                Text("安装已下载 APK（系统确认）")
                            }
                        }
                    }
                }
            }
        }
        groups.forEach { (category, entries) ->
            item(key = "cat:$category", contentType = "category") {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(category, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(entries.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(entries, key = { it.name }, contentType = { "app" }) { app ->
                CatalogRow(app, busy, metadata[app.repository], onDetails, onDownload)
            }
        }
    }
}

@Composable
private fun CatalogRow(
    app: CatalogApp, busy: Boolean, upstream: UpstreamInfo?,
    onDetails: (CatalogApp) -> Unit, onDownload: (CatalogApp) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val verifiedIcon = upstream?.takeIf {
                app.logoPath.isNotBlank() && it.logoPath == app.logoPath
            }?.logo
            var iconFailed by remember(verifiedIcon) { mutableStateOf(false) }
            if (!verifiedIcon.isNullOrBlank() && !iconFailed) {
                AsyncImage(
                    model = verifiedIcon,
                    contentDescription = null,
                    onError = { iconFailed = true },
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(
                Modifier.weight(1f)
                    .sizeIn(minHeight = 48.dp)
                    .clickable(onClickLabel = "查看" + app.name + "详情") { onDetails(app) },
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(app.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                upstream?.stars?.let { Text("★ " + it.toString() + " GitHub Stars", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary) }
                Text(app.summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (app.notice.isNotBlank()) {
                    Text(app.notice, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (app.risk == "high") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.tertiary)
                }
            }
            Spacer(Modifier.width(4.dp))
            if (app.canDownload) {
                TextButton(onClick = { onDownload(app) }, enabled = !busy,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("下载") }
            } else {
                TextButton(onClick = { onDetails(app) },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("查看") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDetail(
    app: CatalogApp, busy: Boolean, upstream: UpstreamInfo?,
    onDismiss: () -> Unit, onDownload: (CatalogApp) -> Unit,
    openUrl: (String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repo = remember(app.repository, app.readmeUrl) {
        app.repository.ifBlank {
            val uri = Uri.parse(app.readmeUrl)
            if (uri.host == "github.com") uri.pathSegments.take(2).joinToString("/") else ""
        }
    }
    val reader = remember(repo) { ReadmeReader(context, repo) }
    var index by remember(reader) { mutableStateOf(reader.cachedIndex()) }
    var choice by rememberSaveable(repo) { mutableStateOf(reader.choice()) }
    var retry by remember(reader) { mutableStateOf(0) }
    var discoveryFailed by remember(reader) { mutableStateOf(false) }
    LaunchedEffect(reader, retry) {
        if (repo.isNotBlank()) {
            discoveryFailed = false
            val result = withContext(Dispatchers.IO) { runCatching { reader.discover() } }
            result.onSuccess { index = it }.onFailure { discoveryFailed = true }
        }
    }
    val current = index
    val path = current?.let { choice?.takeIf(it.paths::contains) ?: ReadmeLanguages.preferred(it.paths, it.original, it.originalTag) }
    var body by remember(reader, path) { mutableStateOf(path?.let(reader::cached).orEmpty()) }
    var failed by remember(reader, path) { mutableStateOf(false) }
    var loading by remember(reader, path) { mutableStateOf(false) }
    LaunchedEffect(reader, current, path, retry) {
        if (current != null && path != null) {
            loading = true
            failed = false
            val result = withContext(Dispatchers.IO) { runCatching { reader.read(current, path) } }
            result.onSuccess { body = it }.onFailure { failed = true }
            loading = false
        }
    }
    val markdown = if (body.isNotBlank()) body else if (path == current?.original) upstream?.readme.orEmpty() else ""
    val parts = remember(markdown) { ReadmeDocument.parse(markdown) }
    val fullUrl = if (path == null || current == null) app.readmeUrl else
        "https://github.com/$repo/blob/${Uri.encode(current.ref, "/")}/${Uri.encode(path, "/")}"
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "project-summary") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(app.category, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(app.name, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(app.summary, style = MaterialTheme.typography.bodyLarge)
                    upstream?.stars?.let {
                        Text("★ " + it + " GitHub Stars",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (app.notice.isNotBlank()) {
                        Text(app.notice, style = MaterialTheme.typography.bodyMedium,
                            color = if (app.risk == "high") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (app.canDownload) {
                        Button(onClick = { onDownload(app) }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("下载官方 APK")
                        }
                    } else if (app.risk == "high") {
                        Text("高风险系统工具仅展示原项目，不在本工具内自动下载或运行提权程序。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = { openUrl(fullUrl) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("阅读原项目 README 全文")
                    }
                    OutlinedButton(onClick = { openUrl(app.homepage) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("访问原项目")
                    }
                    HorizontalDivider()
                    Text(if (parts.isEmpty()) "README 快照" else "原作者 README",
                        style = MaterialTheme.typography.titleMedium)
                    if (current != null && path != null && current.paths.size > 1) {
                        ReadmeLanguagePicker(current, path) { choice = it; reader.select(it) }
                    }
                    if (loading && body.isBlank()) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (discoveryFailed && current == null) TextButton(onClick = { retry++ }) { Text("获取 README 语言列表 · 重试") }
                    if (failed) TextButton(onClick = { retry++ }) {
                        Text(if (body.isBlank()) "此语言暂时无法加载 · 重试" else "正在显示缓存 · 重新获取")
                    }
                    Text(if (parts.isEmpty()) "离线摘要" else "原文已排版 · 已省略网页徽章与图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (parts.isEmpty()) {
                item(key = "offline-readme") {
                    Surface(shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        SelectionContainer {
                            Text(app.readmeSnapshot, Modifier.fillMaxWidth().padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                items(parts.size, key = { "readme-" + it }) { index ->
                    ReadmePartItem(parts[index])
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadmeLanguagePicker(index: ReadmeIndex, active: String, choose: (String) -> Unit) {
    val paths = index.paths
    fun label(path: String) = ReadmeLanguages.label(path, index.originalTag.takeIf { path == index.original })
    var expanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        if (paths.size <= 3 && maxWidth >= (paths.size * 108 * fontScale).dp) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                paths.forEachIndexed { index, path ->
                    SegmentedButton(selected = path == active, onClick = { choose(path) },
                        shape = SegmentedButtonDefaults.itemShape(index, paths.size),
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(label(path)) }
                }
            }
        } else {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("README 语言 · " + label(active))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                paths.forEach { path ->
                    DropdownMenuItem(text = { Text(label(path)) },
                        modifier = Modifier.semantics { selected = path == active },
                        trailingIcon = { if (path == active) Text("✓") },
                        onClick = { expanded = false; choose(path) })
                }
            }
        }
    }
}

@Composable
private fun ReadmePartItem(part: ReadmePart) {
    when (part.type) {
        ReadmePart.Type.HEADING -> {
            val typography = MaterialTheme.typography
            Text(part.text,
                modifier = Modifier.semantics { heading() },
                style = when (part.depth) {
                    1 -> typography.titleLarge
                    2 -> typography.titleMedium
                    else -> typography.titleSmall
                },
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
        }
        ReadmePart.Type.CODE -> {
            Surface(shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                SelectionContainer {
                    Text(part.text,
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState()).padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
        ReadmePart.Type.QUOTE -> {
            Surface(shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                SelectionContainer {
                    Text(part.text, Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ReadmePart.Type.RULE -> HorizontalDivider()
        ReadmePart.Type.BULLET -> SelectionContainer {
            Text(part.text,
                Modifier.fillMaxWidth().padding(start = ((part.depth - 1).coerceIn(0, 3) * 12).dp),
                style = MaterialTheme.typography.bodyMedium)
        }
        ReadmePart.Type.PARAGRAPH -> SelectionContainer {
            Text(part.text, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
