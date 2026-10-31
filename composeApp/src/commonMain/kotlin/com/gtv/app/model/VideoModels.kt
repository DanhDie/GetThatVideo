package com.gtv.app.model

/**
 * Representa o tipo de extração desejado pelo usuário.
 * - [MP4]: Extração de Vídeo (com áudio integrado).
 * - [MP3]: Extração apenas de Áudio / Música convertida para MP3.
 */
enum class ExtractionFormat(
    val id: String,
    val title: String,
    val subtitle: String,
    val fileExtension: String
) {
    MP4(
        id = "mp4",
        title = "Vídeo (MP4)",
        subtitle = "Vídeo completo com áudio",
        fileExtension = ".mp4"
    ),
    MP3(
        id = "mp3",
        title = "Música / Áudio (MP3)",
        subtitle = "Extrair apenas a faixa de áudio",
        fileExtension = ".mp3"
    );

    companion object {
        /**
         * Qualidades padrão exibidas antes mesmo de o usuário clicar em "Analisar Vídeo",
         * permitindo fazer o download direto ou escolher dentre as qualidades mais comuns.
         */
        fun defaultQualitiesFor(format: ExtractionFormat): List<QualityOption> {
            return when (format) {
                MP4 -> listOf(
                    QualityOption("1080p", "1080p Full HD", "Alta Definição (FFmpeg)", 0L, false),
                    QualityOption("720p", "720p HD", "Boa qualidade", 0L, false),
                    QualityOption("480p", "480p SD", "Qualidade média", 0L, false),
                    QualityOption("360p", "360p", "Rápido e leve", 0L, true),
                    QualityOption("240p", "240p", "Economia de dados", 0L, false),
                    QualityOption("144p", "144p", "Tamanho mínimo", 0L, false)
                )
                MP3 -> listOf(
                    QualityOption("160kbps", "MP3 160kbps", "Qualidade Alta", 0L, true),
                    QualityOption("128kbps", "MP3 128kbps", "Qualidade Padrão", 0L, true),
                    QualityOption("70kbps", "MP3 70kbps", "Qualidade Média / Leve", 0L, true),
                    QualityOption("48kbps", "MP3 48kbps", "Compacto (Voz/Podcast)", 0L, true)
                )
            }
        }
    }
}

/**
 * Representa uma opção de qualidade de vídeo (resolução) ou áudio (bitrate).
 */
data class QualityOption(
    val id: String,
    val label: String,
    val detail: String,
    val fileSizeBytes: Long = 0L,
    val isProgressive: Boolean = true
) {
    /**
     * Formata o tamanho aproximado do arquivo em MB ou KB para exibição na UI.
     */
    fun formattedFileSize(): String? {
        if (fileSizeBytes <= 0L) return null
        val mb = fileSizeBytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            val rounded = (mb * 10.0).toLong() / 10.0
            "~$rounded MB"
        } else {
            val kb = (fileSizeBytes / 1024L).coerceAtLeast(1L)
            "~$kb KB"
        }
    }
}

/**
 * Informações detalhadas do vídeo retornadas pelo endpoint `/video_info` da API.
 */
data class VideoInfo(
    val videoId: String,
    val title: String,
    val author: String,
    val lengthSeconds: Int,
    val views: Long,
    val description: String,
    val publishDate: String,
    val thumbnailUrl: String,
    val videoQualities: List<QualityOption>,
    val audioQualities: List<QualityOption>
) {
    /**
     * Converte a duração em segundos para o formato `MM:SS` ou `HH:MM:SS`.
     */
    fun formattedDuration(): String {
        if (lengthSeconds <= 0) return "00:00"
        val hours = lengthSeconds / 3600
        val minutes = (lengthSeconds % 3600) / 60
        val seconds = lengthSeconds % 60
        val minStr = minutes.toString().padStart(2, '0')
        val secStr = seconds.toString().padStart(2, '0')
        return if (hours > 0) {
            "$hours:$minStr:$secStr"
        } else {
            "$minStr:$secStr"
        }
    }

    /**
     * Formata a contagem de visualizações com separadores de milhar.
     */
    fun formattedViews(): String {
        val raw = views.toString()
        val sb = StringBuilder()
        for (i in raw.indices) {
            if (i > 0 && (raw.length - i) % 3 == 0) {
                sb.append('.')
            }
            sb.append(raw[i])
        }
        return "$sb visualizações"
    }
}

/**
 * Status do servidor Python Flask (`/health`).
 */
data class BackendHealth(
    val isOnline: Boolean,
    val serviceName: String,
    val ffmpegAvailable: Boolean
)

/**
 * Resposta de sucesso do endpoint `/download`.
 */
data class DownloadResult(
    val message: String,
    val filePath: String,
    val fileName: String,
    val outputDir: String,
    val format: String,
    val quality: String,
    val title: String
)

/**
 * Item do histórico de downloads realizados durante a sessão.
 */
data class DownloadHistoryItem(
    val title: String,
    val formatLabel: String,
    val quality: String,
    val fileName: String,
    val filePath: String,
    val outputDir: String
)
