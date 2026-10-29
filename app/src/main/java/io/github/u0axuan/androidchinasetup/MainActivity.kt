package io.github.u0axuan.androidchinasetup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray

data class CatalogApp(
    val name: String,
    val category: String,
    val selectedByDefault: Boolean,
    val notice: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catalog = runCatching {
            val raw = assets.open("apps.json").bufferedReader().use { it.readText() }
            val data = JSONArray(raw)
            (0 until data.length()).map { index ->
                val app = data.getJSONObject(index)
                CatalogApp(
                    name = app.getString("name"),
                    category = app.getString("category"),
                    selectedByDefault = app.optBoolean("selected", false),
                    notice = app.optString("notice", "")
                )
            }
        }.getOrDefault(emptyList())

        setContent {
            MaterialTheme {
                var selected by remember {
                    mutableStateOf(catalog.filter { it.selectedByDefault }.map { it.name }.toSet())
                }
                var proxyEnabled by remember { mutableStateOf(false) }
                Scaffold(topBar = {}) { padding ->
                    Column(Modifier.padding(padding).padding(16.dp)) {
                        Text("Android China Setup", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("手机端初始化 · V0.1 清单原型")
                        Spacer(Modifier.height(12.dp))
                        Text("下载策略：智能选择可信源（规划中）")
                        Row {
                            Text("自定义代理（默认关闭）", modifier = Modifier.weight(1f))
                            Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it })
                        }
                        Text("该开关目前仅展示配置意向，尚未连接下载引擎。",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Text("已选择 ${selected.size} 项；暂未实现下载安装")
                        HorizontalDivider()
                        LazyColumn {
                            items(catalog) { app ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(app.name, style = MaterialTheme.typography.titleMedium)
                                        Text(app.category + if (app.notice.isNotEmpty()) " · " + app.notice else "",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Checkbox(
                                        checked = app.name in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + app.name else selected - app.name
                                        }
                                    )
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
