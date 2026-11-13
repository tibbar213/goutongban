package cn.goutongban.aac

import cn.goutongban.aac.update.AppUpdates
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class AppUpdatesTest {
    @Test fun comparesVersionPartsAndAcceptsOnlyTheExpectedReleaseAsset() {
        assertEquals("1.3.10", AppUpdates.parseRelease(manifest(), "1.3.9")?.version)
        assertNull(AppUpdates.parseRelease(manifest(), "1.3.10"))
        assertNull(AppUpdates.parseRelease(manifest(), "1.4.0"))
        assertThrows(IOException::class.java) {
            AppUpdates.parseRelease(manifest().replace("github.com/", "example.invalid/"), "1.3.9")
        }
        assertThrows(IOException::class.java) {
            AppUpdates.parseRelease(manifest().replace("sha256:", "invalid:"), "1.3.9")
        }
    }

    @Test fun usesManifestFallbackWhenGitHubApiIsRateLimited() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            server.enqueue(MockResponse().setBody(manifest()))
            val updates = AppUpdates(
                RuntimeEnvironment.getApplication(),
                listOf(server.url("/api").toString(), server.url("/fallback").toString())
            )
            assertEquals("1.3.10", updates.latest()?.version)
            assertEquals("/api", server.takeRequest().path)
            assertEquals("/fallback", server.takeRequest().path)
        }
    }

    @Test fun staleBackupCannotHideANewerRelease() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            server.enqueue(MockResponse().setBody(manifest().replace("1.3.10", BuildConfig.VERSION_NAME)))
            server.enqueue(MockResponse().setBody(manifest()))
            val updates = AppUpdates(
                RuntimeEnvironment.getApplication(),
                listOf("/api", "/cdn", "/raw").map { server.url(it).toString() }
            )
            assertEquals("1.3.10", updates.latest()?.version)
        }
    }

    private fun manifest() = """
        {
            "tag_name": "v1.3.10",
            "assets": [{
                "name": "goutongban.apk",
                "state": "uploaded",
                "browser_download_url": "https://github.com/tibbar213/goutongban/releases/download/v1.3.10/goutongban.apk",
                "size": 123456,
                "digest": "sha256:${"a".repeat(64)}"
            }]
        }
    """.trimIndent()
}
