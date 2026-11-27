package io.github.u0axuan.androidchinasetup

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import java.io.File

/** A single foreground-initiated download is shared by all activity instances. */
internal object DownloadTask {
    private val main = Handler(Looper.getMainLooper())
    var downloadBusy by mutableStateOf(false)
        private set
    var downloadPercent by mutableIntStateOf(0)
        private set
    var downloadText by mutableStateOf("")
    var downloadedFile by mutableStateOf<File?>(null)
        private set
    var updateDownload by mutableStateOf(false)
        private set

    fun start(context: Context, repository: String, preferred: String, updating: Boolean,
              options: NetworkOptions, chosen: SourceSelection) {
        if (downloadBusy) return
        downloadBusy = true
        downloadPercent = 0
        downloadText = "正在获取官方发布信息…"
        downloadedFile = null
        updateDownload = updating
        val downloader = ApkDownloader(context)
        Thread {
            try {
                val asset = downloader.findLatestApk(repository, options, preferred)
                main.post { downloadText = DownloadSources.explain(asset, chosen) }
                var previous = -1
                val file = downloader.download(asset, options, chosen,
                    { value ->
                        if (value != previous) {
                            previous = value
                            main.post { downloadPercent = value }
                        }
                    },
                    { text -> main.post { downloadText = text } }
                )
                if (updating && !SelfUpdater.matchingSigner(context, file)) {
                    file.delete()
                    main.post {
                        downloadText = "更新包签名与当前安装版本不一致，已拒绝覆盖安装。"
                        downloadBusy = false
                    }
                } else {
                    main.post {
                        downloadedFile = file
                        downloadText = "APK 下载完成，等待 Android 系统安装确认"
                        downloadBusy = false
                    }
                }
            } catch (e: Exception) {
                main.post {
                    downloadText = "下载失败：" + (e.message ?: "网络不可用")
                    downloadBusy = false
                }
            }
        }.start()
    }
}
