package com.gtv.app.network

import com.gtv.app.model.BackendHealth
import com.gtv.app.model.DownloadResult
import com.gtv.app.model.ExtractionFormat
import com.gtv.app.model.QualityOption
import com.gtv.app.model.VideoInfo
import com.gtv.app.platform.performHttpRequest

/**
 * Cliente HTTP responsável por comunicar o aplicativo Kotlin Multiplatform com a API
 * baseada em `https://github.com/zararashraf/youtube-video-downloader-api` (`backend/main.py`).
 */
class YoutubeApiClient {

    /**
     * Consulta `GET /health` para saber se o servidor Python Flask está ativo.
     */
    suspend fun checkHealth(baseUrl: String): Result<BackendHealth> = runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val response = performHttpRequest(
            url = "$cleanBase/health",
            method = "GET",
            timeoutMs = 5_000
        )
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("Servidor respondeu com HTTP ${response.statusCode}")
        }
        val json = SimpleJson.parseObject(response.body)
        BackendHealth(
            isOnline = json["status"]?.toString() == "ok",
            serviceName = json["service"]?.toString() ?: "YouTube Downloader API",
            ffmpegAvailable = json["ffmpeg_available"] as? Boolean ?: false
        )
    }

    /**
     * Consulta `POST /video_info` enviando `{"url": "..."}` para buscar metadados do vídeo
     * e as qualidades reais disponíveis tanto para Vídeo (MP4) quanto para Áudio/Música (MP3).
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun fetchVideoInfo(baseUrl: String, youtubeUrl: String): Result<VideoInfo> = runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val payload = SimpleJson.encodeMap(mapOf("url" to youtubeUrl.trim()))

        val response = performHttpRequest(
            url = "$cleanBase/video_info",
            method = "POST",
            jsonBody = payload,
            timeoutMs = 45_000
        )

        val json = SimpleJson.parseObject(response.body)
        if (response.statusCode !in 200..299) {
            val errorMsg = json["error"]?.toString() ?: "Erro ao buscar informações do vídeo (HTTP ${response.statusCode})."
            throw IllegalStateException(errorMsg)
        }

        val videoQualitiesRaw = (json["video_qualities"] as? List<Map<String, Any?>>).orEmpty()
        val audioQualitiesRaw = (json["audio_qualities"] as? List<Map<String, Any?>>).orEmpty()

        val videoQualities = videoQualitiesRaw.mapNotNull { item ->
            val id = item["id"]?.toString() ?: return@mapNotNull null
            QualityOption(
                id = id,
                label = item["label"]?.toString() ?: id,
                detail = item["detail"]?.toString() ?: "Vídeo MP4",
                fileSizeBytes = (item["file_size_bytes"] as? Number)?.toLong() ?: 0L,
                isProgressive = item["is_progressive"] as? Boolean ?: false
            )
        }

        val audioQualities = audioQualitiesRaw.mapNotNull { item ->
            val id = item["id"]?.toString() ?: return@mapNotNull null
            QualityOption(
                id = id,
                label = item["label"]?.toString() ?: "MP3 $id",
                detail = item["detail"]?.toString() ?: "Áudio MP3",
                fileSizeBytes = (item["file_size_bytes"] as? Number)?.toLong() ?: 0L,
                isProgressive = true
            )
        }

        VideoInfo(
            videoId = json["video_id"]?.toString().orEmpty(),
            title = json["title"]?.toString() ?: "Vídeo do YouTube",
            author = json["author"]?.toString() ?: "Canal desconhecido",
            lengthSeconds = (json["length"] as? Number)?.toInt() ?: 0,
            views = (json["views"] as? Number)?.toLong() ?: 0L,
            description = json["description"]?.toString().orEmpty(),
            publishDate = json["publish_date"]?.toString().orEmpty(),
            thumbnailUrl = json["thumbnail_url"]?.toString().orEmpty(),
            videoQualities = videoQualities.ifEmpty {
                ExtractionFormat.defaultQualitiesFor(ExtractionFormat.MP4)
            },
            audioQualities = audioQualities.ifEmpty {
                ExtractionFormat.defaultQualitiesFor(ExtractionFormat.MP3)
            }
        )
    }

    /**
     * Chama `POST /download` enviando a URL do vídeo, o formato de extração (`mp4` ou `mp3`),
     * a qualidade selecionada (ex: `720p` ou `128kbps`) e a pasta onde o arquivo será salvo.
     */
    suspend fun downloadMedia(
        baseUrl: String,
        youtubeUrl: String,
        format: ExtractionFormat,
        quality: String,
        outputDir: String
    ): Result<DownloadResult> = runCatching {
        val cleanBase = baseUrl.trimEnd('/')
        val payload = SimpleJson.encodeMap(
            mapOf(
                "url" to youtubeUrl.trim(),
                "format" to format.id,
                "quality" to quality.trim(),
                "output_dir" to outputDir.trim()
            )
        )

        val response = performHttpRequest(
            url = "$cleanBase/download",
            method = "POST",
            jsonBody = payload,
            timeoutMs = 300_000 // até 5 minutos para vídeos maiores
        )

        val json = SimpleJson.parseObject(response.body)
        if (response.statusCode !in 200..299) {
            val errorMsg = json["error"]?.toString() ?: "Falha no download (HTTP ${response.statusCode})."
            throw IllegalStateException(errorMsg)
        }

        DownloadResult(
            message = json["message"]?.toString() ?: "Download concluído com sucesso!",
            filePath = json["file_path"]?.toString().orEmpty(),
            fileName = json["file_name"]?.toString().orEmpty(),
            outputDir = json["output_dir"]?.toString().ifNullOrBlank { outputDir },
            format = json["format"]?.toString() ?: format.id,
            quality = json["quality"]?.toString() ?: quality,
            title = json["title"]?.toString() ?: "Arquivo baixado"
        )
    }
}

private inline fun String?.ifNullOrBlank(defaultValue: () -> String): String {
    return if (this.isNullOrBlank()) defaultValue() else this
}
