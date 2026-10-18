plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// The SDK never reinstalls a HUD that is already on the glasses, so the phone compares the version the
// glasses report in GlassesHello with the HUD it bundles and replaces it when they differ. The version
// carries a hash of every input to the glasses APK, so any glasses change produces a new one.
val hudVersionName: String by extra(run {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    listOf("glasses/src/main", "protocol/src/main", "glasses/build.gradle.kts", "gradle/libs.versions.toml")
        .map(::file)
        .flatMap { if (it.isDirectory) it.walkTopDown().filter(File::isFile).toList() else listOf(it) }
        .sortedBy { it.relativeTo(rootDir).invariantSeparatorsPath }
        .forEach {
            digest.update(it.relativeTo(rootDir).invariantSeparatorsPath.toByteArray())
            digest.update(it.readBytes())
        }
    "0.2.0+" + digest.digest().take(4).joinToString("") { "%02x".format(it) }
})
