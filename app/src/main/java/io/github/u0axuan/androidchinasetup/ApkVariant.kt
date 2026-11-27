package io.github.u0axuan.androidchinasetup

internal object ApkVariant {
    private val abiPattern = Regex("""(?i)(?<![a-z0-9])(arm64(?:-v8a)?|aarch64|armeabi-v7a|armeabi|arm32v7|armv7a?|x86[_-]64|x64|x86)(?![a-z0-9])""")
    private fun abis(name: String) = abiPattern.findAll(name).map {
        when (it.value.lowercase()) {
            "arm64", "aarch64", "arm64-v8a" -> "arm64-v8a"
            "arm32v7", "armv7", "armv7a" -> "armeabi-v7a"
            "x64", "x86-64" -> "x86_64"
            else -> it.value.lowercase()
        }
    }.toSet()

    fun select(names: List<String>, device: List<String>, preferred: String = ""): String {
        val variants = names.filter { preferred.isBlank() || Regex(preferred).matches(it) }.associateWith(::abis)
        for (abi in device) {
            val matching = variants.filterValues { abi in it }.keys
            if (matching.size == 1) return matching.single()
            if (matching.size > 1) break
        }
        val neutral = variants.filterValues { it.isEmpty() }.keys
        val universal = neutral.filter { name -> listOf("universal", "noarch", "all-arch").any { name.contains(it, true) } || name == "app-release.apk" }
        return universal.singleOrNull() ?: neutral.singleOrNull()?.takeIf { variants.size == 1 }
            ?: error("无法确定与本机架构兼容的 APK，请到原项目发布页选择")
    }
}
