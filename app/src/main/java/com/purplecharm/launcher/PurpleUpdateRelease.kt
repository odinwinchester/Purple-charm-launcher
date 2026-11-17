package com.purplecharm.launcher

import org.json.JSONException
import org.json.JSONObject
import java.net.URI

/**
 * Parses release metadata without downloading or installing anything.
 * The release digest protects transfer integrity only; it does not authenticate
 * an APK. A consumer must verify the official signing certificate, package,
 * and installed version before offering the file to Android.
 *
 * Release bodies may include explicit `versionCode: N` and `versionName: X`
 * lines. Integer vN tags remain a legacy fallback for versionCode; dotted
 * version tags require an explicit versionCode.
 *
 * This parser does not follow redirects. A future download layer must reject
 * redirects or revalidate every hop before accepting the response.
 */
internal object PurpleUpdateRelease {
    const val RELEASES_API = "https://api.github.com/repos/odinwinchester/Purple-charm-launcher/releases/latest"

    /** Bound metadata-driven disk use; 100 MiB leaves room above current APK sizes. */
    const val MAX_APK_SIZE_BYTES = 100L * 1024L * 1024L

    private const val EXPECTED_HOST = "github.com"
    private const val RELEASE_PATH_PREFIX = "/odinwinchester/Purple-charm-launcher/releases/download/"
    private const val APK_PREFIX = "Purple-Charm-v"
    private const val APK_SUFFIX = "-OFFICIAL-RELEASE.apk"

    private val TAG_VERSION = Regex("""^v([0-9]+(?:\.[0-9]+){0,2})(?:-stable)?$""")
    private val VERSION_CODE_MARKER =
        Regex("""(?im)^[ \t]*(?:[-*][ \t]*)?versionCode[ \t]*:[ \t]*([^\r\n]*)[ \t]*$""")
    private val VERSION_NAME_MARKER =
        Regex("""(?im)^[ \t]*(?:[-*][ \t]*)?versionName[ \t]*:[ \t]*([^\r\n]*)[ \t]*$""")
    private val VERSION_NAME_VALUE = Regex("""^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$""")
    private val SHA256 = Regex("""^sha256:([0-9a-fA-F]{64})$""")

    data class Candidate(
        val versionCode: Long,
        val versionName: String,
        val tag: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val sha256: String
    )

    private data class Marker(val present: Boolean, val value: String?)

    fun parseLatest(json: String, installedVersionCode: Long): Candidate? {
        if (json.isBlank()) return null
        val release = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }

        val draft = release.opt("draft") as? Boolean ?: return null
        val prerelease = release.opt("prerelease") as? Boolean ?: return null
        if (draft || prerelease) return null

        val tag = release.stringOrNull("tag_name") ?: return null
        val tagVersion = TAG_VERSION.matchEntire(tag)?.groupValues?.get(1) ?: return null

        val bodyValue = release.opt("body")
        val body = when {
            bodyValue == null || bodyValue === JSONObject.NULL -> ""
            bodyValue is String -> bodyValue
            else -> return null
        }

        val versionCodeMarker = readMarker(body, VERSION_CODE_MARKER)
        val versionCode = if (versionCodeMarker.present) {
            versionCodeMarker.value?.toLongOrNull()
                ?.takeIf { it in 1L..Int.MAX_VALUE.toLong() } ?: return null
        } else {
            // Preserve the existing v12/v13 convention for older integer-tagged releases.
            tagVersion.toLongOrNull()
                ?.takeIf { it in 1L..Int.MAX_VALUE.toLong() } ?: return null
        }
        if (versionCode <= installedVersionCode) return null

        val versionNameMarker = readMarker(body, VERSION_NAME_MARKER)
        val versionName = if (versionNameMarker.present) {
            versionNameMarker.value?.takeIf(VERSION_NAME_VALUE::matches) ?: return null
        } else {
            tagVersion
        }

        val assets = release.optJSONArray("assets") ?: return null
        val expectedAsset = APK_PREFIX + tagVersion + APK_SUFFIX
        val expectedPath = RELEASE_PATH_PREFIX + tag + "/" + expectedAsset

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.stringOrNull("name") != expectedAsset) continue

            val sizeValue = asset.opt("size") as? Number ?: continue
            val sizeAsDouble = sizeValue.toDouble()
            val size = sizeValue.toLong()
            if (!sizeAsDouble.isFinite() || size.toDouble() != sizeAsDouble ||
                size <= 0L || size > MAX_APK_SIZE_BYTES) continue

            val digest = asset.stringOrNull("digest") ?: continue
            val sha = SHA256.matchEntire(digest)?.groupValues?.get(1)?.lowercase() ?: continue

            val url = asset.stringOrNull("browser_download_url") ?: continue
            val parsed = try {
                URI(url)
            } catch (_: Exception) {
                null
            } ?: continue

            if (parsed.scheme?.equals("https", ignoreCase = true) != true ||
                parsed.host?.equals(EXPECTED_HOST, ignoreCase = true) != true ||
                parsed.rawUserInfo != null || parsed.port != -1 ||
                parsed.rawQuery != null || parsed.rawFragment != null ||
                parsed.rawPath != expectedPath) continue

            return Candidate(versionCode, versionName, tag, url, size, sha)
        }
        return null
    }

    private fun readMarker(body: String, pattern: Regex): Marker {
        val matches = pattern.findAll(body).toList()
        if (matches.isEmpty()) return Marker(present = false, value = null)
        if (matches.size != 1) return Marker(present = true, value = null)
        return Marker(present = true, value = matches.single().groupValues[1].trim())
    }

    private fun JSONObject.stringOrNull(key: String): String? = opt(key) as? String
}
