package com.gtv.app

import com.gtv.app.model.ExtractionFormat
import com.gtv.app.model.QualityOption
import com.gtv.app.model.VideoInfo
import com.gtv.app.network.SimpleJson
import com.gtv.app.network.YoutubeApiClient
import com.gtv.app.platform.ensureLocalBackendRunning
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun testBackendAutoStartAndHealthCheck() = runBlocking {
        val baseUrl = "http://127.0.0.1:5000"
        val started = ensureLocalBackendRunning(baseUrl)
        assertTrue(started, "O servidor Python Flask local deve iniciar automaticamente no Desktop JVM")

        val client = YoutubeApiClient()
        val health = client.checkHealth(baseUrl).getOrThrow()
        assertTrue(health.isOnline)
    }

    @Test
    fun testJsonEncodeAndParse() {
        val rawJson = """
            {
                "title": "Me at the zoo",
                "length": 19,
                "ffmpeg_available": true,
                "video_qualities": [
                    {"id": "360p", "label": "360p (15fps)", "file_size_bytes": 322379}
                ]
            }
        """.trimIndent()

        val parsed = SimpleJson.parseObject(rawJson)
        assertEquals("Me at the zoo", parsed["title"])
        assertEquals(19L, parsed["length"])
        assertEquals(true, parsed["ffmpeg_available"])

        @Suppress("UNCHECKED_CAST")
        val qualities = parsed["video_qualities"] as List<Map<String, Any?>>
        assertEquals(1, qualities.size)
        assertEquals("360p", qualities[0]["id"])
    }

    @Test
    fun testExtractionFormatsAndFormatting() {
        val mp4Defaults = ExtractionFormat.defaultQualitiesFor(ExtractionFormat.MP4)
        val mp3Defaults = ExtractionFormat.defaultQualitiesFor(ExtractionFormat.MP3)

        assertTrue(mp4Defaults.any { it.id == "720p" })
        assertTrue(mp3Defaults.any { it.id == "128kbps" })

        val sampleQuality = QualityOption("720p", "720p HD", "Vídeo", fileSizeBytes = 5 * 1024 * 1024L)
        assertEquals("~5.0 MB", sampleQuality.formattedFileSize())

        val sampleVideo = VideoInfo(
            videoId = "jNQXAC9IVRw",
            title = "Me at the zoo",
            author = "jawed",
            lengthSeconds = 125,
            views = 350000000L,
            description = "Test",
            publishDate = "2005-04-23",
            thumbnailUrl = "",
            videoQualities = mp4Defaults,
            audioQualities = mp3Defaults
        )
        assertEquals("02:05", sampleVideo.formattedDuration())
        assertEquals("350.000.000 visualizações", sampleVideo.formattedViews())
    }
}
