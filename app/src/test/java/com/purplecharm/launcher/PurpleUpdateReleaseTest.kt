package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class PurpleUpdateReleaseTest {
    private val hash = "a".repeat(64)

    private fun versionFromTag(tag: String): String =
        tag.removePrefix("v").removeSuffix("-stable")

    private fun expectedAsset(tag: String): String =
        "Purple-Charm-v" + versionFromTag(tag) + "-OFFICIAL-RELEASE.apk"

    private fun expectedUrl(tag: String): String =
        "https://github.com/odinwinchester/Purple-charm-launcher/releases/download/" +
            tag + "/" + expectedAsset(tag)

    private fun release(
        tag: String = "v13",
        body: String? = null,
        digest: String? = "sha256:$hash",
        url: String? = expectedUrl(tag),
        name: String? = expectedAsset(tag),
        draft: Boolean? = false,
        prerelease: Boolean? = false,
        size: Any? = 1000
    ): String = JSONObject().apply {
        put("tag_name", tag)
        draft?.let { put("draft", it) }
        prerelease?.let { put("prerelease", it) }
        body?.let { put("body", it) }
        put("assets", JSONArray().put(JSONObject().apply {
            name?.let { put("name", it) }
            digest?.let { put("digest", it) }
            url?.let { put("browser_download_url", it) }
            size?.let { put("size", it) }
        }))
    }.toString()

    @Test fun acceptsOfficialNewerIntegerTagAndKeepsNameSeparate() {
        val candidate = PurpleUpdateRelease.parseLatest(release(), 12)
        assertNotNull(candidate)
        assertEquals(13L, candidate!!.versionCode)
        assertEquals("13", candidate.versionName)
        assertEquals("v13", candidate.tag)
        assertEquals(hash, candidate.sha256)
    }

    @Test fun explicitVersionCodeAndVersionNameDoNotComeFromEachOther() {
        val candidate = PurpleUpdateRelease.parseLatest(
            release(tag = "v13", body = "- versionCode: 27\n- versionName: 13.0"),
            installedVersionCode = 26
        )
        assertNotNull(candidate)
        assertEquals(27L, candidate!!.versionCode)
        assertEquals("13.0", candidate.versionName)
    }

    @Test fun acceptsStableAndDottedTagsWithExplicitCode() {
        val stable = PurpleUpdateRelease.parseLatest(release(tag = "v12-stable"), 11)
        assertNotNull(stable)
        assertEquals(12L, stable!!.versionCode)
        assertEquals("12", stable.versionName)

        val dotted = PurpleUpdateRelease.parseLatest(
            release(tag = "v0.7.2", body = "- versionCode: 9\n- versionName: 0.7.2"),
            installedVersionCode = 8
        )
        assertNotNull(dotted)
        assertEquals(9L, dotted!!.versionCode)
        assertEquals("0.7.2", dotted.versionName)
    }

    @Test fun ignoresSameOrOlderVersionCode() {
        assertNull(PurpleUpdateRelease.parseLatest(release(), 13))
        assertNull(PurpleUpdateRelease.parseLatest(release(), 14))
        assertNull(PurpleUpdateRelease.parseLatest(
            release(tag = "v13", body = "versionCode: 12"),
            installedVersionCode = 12
        ))
    }

    @Test fun rejectsDraftsAndPrereleases() {
        assertNull(PurpleUpdateRelease.parseLatest(release(draft = true), 12))
        assertNull(PurpleUpdateRelease.parseLatest(release(prerelease = true), 12))
    }

    @Test fun rejectsInvalidTagsAndMalformedJsonWithoutThrowing() {
        listOf("latest", "v13-rc1", "v1.2.3-beta", "v", "v1.2.3.4").forEach { tag ->
            assertNull(PurpleUpdateRelease.parseLatest(release(tag = tag), 0))
        }
        listOf("", "   ", "{", "not-json", "[]", "null").forEach { json ->
            assertNull(PurpleUpdateRelease.parseLatest(json, 0))
        }
        assertNull(PurpleUpdateRelease.parseLatest("{}", 0))
    }

    @Test fun rejectsMissingOrMalformedVersionMetadata() {
        assertNull(PurpleUpdateRelease.parseLatest(release(draft = null), 0))
        assertNull(PurpleUpdateRelease.parseLatest(release(prerelease = null), 0))
        assertNull(PurpleUpdateRelease.parseLatest(release(body = "versionCode: 0"), 0))
        assertNull(PurpleUpdateRelease.parseLatest(release(body = "versionCode: 2147483648"), 0))
        assertNull(PurpleUpdateRelease.parseLatest(release(body = "versionCode: 13.5"), 0))
        assertNull(PurpleUpdateRelease.parseLatest(
            release(body = "versionCode: 13\nversionCode: 14"), 0
        ))
        assertNull(PurpleUpdateRelease.parseLatest(
            release(body = "versionCode: 13\nversionName: bad name"), 0
        ))
        assertNull(PurpleUpdateRelease.parseLatest(
            release(tag = "v1.2.3", body = "versionName: 1.2.3"), 0
        ))
    }

    @Test fun rejectsMissingOrInvalidAssetMetadata() {
        assertNull(PurpleUpdateRelease.parseLatest(release(digest = null), 12))
        assertNull(PurpleUpdateRelease.parseLatest(release(digest = "sha256:abc"), 12))
        assertNull(PurpleUpdateRelease.parseLatest(release(digest = "sha1:$hash"), 12))
        assertNull(PurpleUpdateRelease.parseLatest(release(name = null), 12))
        assertNull(PurpleUpdateRelease.parseLatest(release(name = "other.apk"), 12))
        assertNull(PurpleUpdateRelease.parseLatest(release(size = null), 12))
        assertNull(PurpleUpdateRelease.parseLatest(release(size = 0), 12))
        assertNull(PurpleUpdateRelease.parseLatest(release(size = -1), 12))
        assertNull(PurpleUpdateRelease.parseLatest(
            release(size = PurpleUpdateRelease.MAX_APK_SIZE_BYTES + 1), 12
        ))
        assertNull(PurpleUpdateRelease.parseLatest(release(size = "1000"), 12))
        assertNull(PurpleUpdateRelease.parseLatest(release(size = 1000.5), 12))
    }

    @Test fun rejectsUrlsOutsideTheExactOfficialAssetPath() {
        val invalidUrls = listOf(
            "http://github.com/odinwinchester/Purple-charm-launcher/releases/download/v13/" + expectedAsset("v13"),
            "https://evil.example/odinwinchester/Purple-charm-launcher/releases/download/v13/" + expectedAsset("v13"),
            "https://github.com.evil.example/odinwinchester/Purple-charm-launcher/releases/download/v13/" + expectedAsset("v13"),
            "https://github.com@evil.example/odinwinchester/Purple-charm-launcher/releases/download/v13/" + expectedAsset("v13"),
            "https://github.com:8443/odinwinchester/Purple-charm-launcher/releases/download/v13/" + expectedAsset("v13"),
            "https://github.com/odinwinchester/other/releases/download/v13/" + expectedAsset("v13"),
            "https://github.com/odinwinchester/Purple-charm-launcher/releases/download/v12/" + expectedAsset("v13"),
            expectedUrl("v13") + "?redirect=https://evil.example",
            expectedUrl("v13") + "#fragment",
            "relative/path",
            "https:github.com/odinwinchester/Purple-charm-launcher/releases/download/v13/" + expectedAsset("v13")
        )
        invalidUrls.forEach { url ->
            assertNull(PurpleUpdateRelease.parseLatest(release(url = url), 12))
        }
    }

    @Test fun rejectsAnAssetUrlWhosePathDoesNotMatchItsNameAndTag() {
        val mismatches = listOf(
            "https://github.com/odinwinchester/Purple-charm-launcher/releases/download/v13/other.apk",
            "https://github.com/odinwinchester/Purple-charm-launcher/releases/download/v13-stable/" +
                expectedAsset("v13")
        )
        mismatches.forEach { url ->
            assertNull(PurpleUpdateRelease.parseLatest(release(url = url), 12))
        }
    }
}
