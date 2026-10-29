package io.github.u0axuan.androidchinasetup

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class UpdateInfo(val version: String, val pageUrl: String, val notes: String)

/** Release checks run on worker threads; installation always requires consent. */
object SelfUpdater {
    const val REPOSITORY = "U0Axuan/android-china-setup"
    const val PROJECT_URL = "https://github.com/U0Axuan/android-china-setup"
    const val RELEASE_URL = "https://github.com/U0Axuan/android-china-setup/releases/latest"

    fun isNewer(remote: String, installed: String): Boolean {
        fun parts(s: String): List<Int> =
            s.removePrefix("v").substringBefore('-').split('.')
                .map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(installed)
        for (i in 0 until maxOf(r.size, l.size, 3)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return installed.contains("-") && !remote.contains("-")
    }

    fun check(network: NetworkOptions): UpdateInfo {
        val url = URL("https://api.github.com/repos/" + REPOSITORY + "/releases/latest")
        val proxy = if (network.proxyEnabled) {
            require(network.proxyHost.isNotBlank() && network.proxyPort in 1..65535)
            Proxy(
                if (network.proxyType == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP,
                InetSocketAddress(network.proxyHost, network.proxyPort)
            )
        } else Proxy.NO_PROXY
        val connection = url.openConnection(proxy) as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "Android-China-Setup-Updater")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            require(connection.responseCode == 200) {
                "检查更新失败：HTTP " + connection.responseCode
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val version = json.getString("tag_name")
            require(version.matches(Regex("""v\d+(?:\.\d+){1,3}"""))) { "无法识别版本号" }
            return UpdateInfo(version, RELEASE_URL, json.optString("body").take(600))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * In-place upgrades need a matching signing identity. CI-generated debug
     * keys are not persistent across builds; stable release signing is required.
     */
    @Suppress("DEPRECATION")
    fun matchingSigner(context: Context, file: java.io.File): Boolean {
        val pm = context.packageManager
        val current = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val candidate = pm.getPackageArchiveInfo(
            file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: return false
        if (candidate.packageName != context.packageName) return false
        fun certs(info: android.content.pm.PackageInfo): Set<String> {
            val signing = info.signingInfo ?: return emptySet()
            val signatures = if (signing.hasMultipleSigners()) signing.apkContentsSigners
                             else signing.signingCertificateHistory
            return signatures.map {
                val hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(it.toByteArray())
                hash.joinToString("") { b -> "%02x".format(b) }
            }.toSet()
        }
        return certs(current).isNotEmpty() && certs(current) == certs(candidate)
    }
}
