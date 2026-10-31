package com.gtv.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gtv.app.model.DownloadHistoryItem
import com.gtv.app.model.DownloadResult
import com.gtv.app.model.ExtractionFormat
import com.gtv.app.model.QualityOption
import com.gtv.app.model.VideoInfo
import com.gtv.app.network.YoutubeApiClient
import com.gtv.app.platform.ensureLocalBackendRunning
import com.gtv.app.platform.getDefaultApiBaseUrl
import com.gtv.app.platform.getDefaultDownloadDirectory
import com.gtv.app.platform.openDirectoryInSystem
import com.gtv.app.platform.pickDirectoryDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado único e imutável da tela principal (Arquitetura MVVM / Unidirectional Data Flow).
 * Facilita o estudo de como o Compose Multiplatform reage automaticamente a mudanças de estado.
 */
data class DownloaderUiState(
    // 1. URL do vídeo do YouTube
    val youtubeUrl: String = "",

    // 2. Tipo de extração (MP4 = Vídeo, MP3 = Áudio/Música)
    val selectedFormat: ExtractionFormat = ExtractionFormat.MP4,

    // 3. Pasta de destino do download
    val outputDirectory: String = getDefaultDownloadDirectory(),

    // 4. Qualidade selecionada e lista de qualidades disponíveis para o formato atual
    val availableQualities: List<QualityOption> = ExtractionFormat.defaultQualitiesFor(ExtractionFormat.MP4),
    val selectedQualityId: String = "720p",

    // 5. Informações do vídeo carregadas da API (/video_info)
    val videoInfo: VideoInfo? = null,
    val isFetchingInfo: Boolean = false,

    // 6. Estado de execução do download (/download)
    val isDownloading: Boolean = false,
    val lastDownloadResult: DownloadResult? = null,
    val downloadHistory: List<DownloadHistoryItem> = emptyList(),

    // 7. Mensagens de feedback e status da API Python
    val errorMessage: String? = null,
    val statusBanner: String? = null,
    val apiBaseUrl: String = getDefaultApiBaseUrl(),
    val isBackendOnline: Boolean = false,
    val isFfmpegAvailable: Boolean = false,
    val isCheckingBackend: Boolean = false
)

/**
 * ViewModel responsável por coordenar toda a lógica de negócio do aplicativo:
 * - Verificar/iniciar o servidor Python Flask (`backend/main.py`)
 * - Buscar metadados e resoluções/bitrates do vídeo (`/video_info`)
 * - Alternar entre MP4 (Vídeo) e MP3 (Música) atualizando as qualidades correspondentes
 * - Selecionar a pasta onde o arquivo será salvo
 * - Executar o download (`/download`)
 */
class DownloaderViewModel(
    private val apiClient: YoutubeApiClient = YoutubeApiClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloaderUiState())
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    init {
        // Ao abrir o app, verifica se a API Python já está ativa (e inicia automaticamente no Desktop)
        connectOrStartBackend()
    }

    /**
     * Atualiza a URL do YouTube digitada ou colada pelo usuário.
     */
    fun onUrlChanged(newUrl: String) {
        _uiState.update { state ->
            state.copy(
                youtubeUrl = newUrl,
                errorMessage = null
            )
        }
    }

    /**
     * Altera o tipo de extração entre Vídeo (MP4) e Música/Áudio (MP3).
     * Atualiza imediatamente a lista de qualidades disponíveis para o formato escolhido!
     */
    fun onFormatSelected(format: ExtractionFormat) {
        _uiState.update { state ->
            val qualitiesForFormat = when (format) {
                ExtractionFormat.MP4 -> state.videoInfo?.videoQualities
                    ?: ExtractionFormat.defaultQualitiesFor(ExtractionFormat.MP4)
                ExtractionFormat.MP3 -> state.videoInfo?.audioQualities
                    ?: ExtractionFormat.defaultQualitiesFor(ExtractionFormat.MP3)
            }
            val firstQualityId = qualitiesForFormat.firstOrNull()?.id.orEmpty()

            state.copy(
                selectedFormat = format,
                availableQualities = qualitiesForFormat,
                selectedQualityId = firstQualityId,
                errorMessage = null
            )
        }
    }

    /**
     * Define a qualidade de vídeo (ex: "1080p", "720p", "360p") ou música (ex: "160kbps", "128kbps").
     */
    fun onQualitySelected(qualityId: String) {
        _uiState.update { it.copy(selectedQualityId = qualityId, errorMessage = null) }
    }

    /**
     * Permite editar manualmente o caminho da pasta onde o download será salvo.
     */
    fun onOutputDirChanged(newPath: String) {
        _uiState.update { it.copy(outputDirectory = newPath, errorMessage = null) }
    }

    /**
     * Abre a janela nativa do sistema operacional para o usuário escolher a pasta de download.
     */
    fun browseOutputDirectory() {
        viewModelScope.launch {
            val currentDir = _uiState.value.outputDirectory
            val chosen = pickDirectoryDialog(currentDir)
            if (!chosen.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        outputDirectory = chosen,
                        errorMessage = null,
                        statusBanner = "Pasta de destino atualizada!"
                    )
                }
            }
        }
    }

    /**
     * Abre a pasta de destino no gerenciador de arquivos do sistema (Windows Explorer / Finder).
     */
    fun openFolder(path: String = _uiState.value.outputDirectory) {
        val opened = openDirectoryInSystem(path)
        if (!opened) {
            _uiState.update {
                it.copy(errorMessage = "Não foi possível abrir a pasta automaticamente: $path")
            }
        }
    }

    /**
     * Permite alterar a URL base da API (ex: se o usuário estiver rodando a API em outra porta ou IP).
     */
    fun onApiBaseUrlChanged(newBaseUrl: String) {
        _uiState.update { it.copy(apiBaseUrl = newBaseUrl) }
    }

    /**
     * Verifica se o backend Python está online e, no Desktop, inicia-o automaticamente se necessário.
     */
    fun connectOrStartBackend() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingBackend = true, errorMessage = null) }
            val baseUrl = _uiState.value.apiBaseUrl

            // Tenta iniciar automaticamente no Desktop caso não esteja respondendo
            ensureLocalBackendRunning(baseUrl)

            val healthResult = apiClient.checkHealth(baseUrl)
            healthResult.fold(
                onSuccess = { health ->
                    _uiState.update {
                        it.copy(
                            isBackendOnline = health.isOnline,
                            isFfmpegAvailable = health.ffmpegAvailable,
                            isCheckingBackend = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update {
                        it.copy(
                            isBackendOnline = false,
                            isCheckingBackend = false
                        )
                    }
                }
            )
        }
    }

    /**
     * Consulta a API (`/video_info`) para obter título, canal, duração, visualizações
     * e as qualidades reais disponíveis para aquele vídeo no YouTube.
     */
    fun fetchVideoMetadata() {
        val url = _uiState.value.youtubeUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Por favor, cole a URL de um vídeo do YouTube.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isFetchingInfo = true,
                    errorMessage = null,
                    statusBanner = "Carregando informações e qualidades do vídeo..."
                )
            }

            ensureLocalBackendRunning(_uiState.value.apiBaseUrl)

            val result = apiClient.fetchVideoInfo(
                baseUrl = _uiState.value.apiBaseUrl,
                youtubeUrl = url
            )

            result.fold(
                onSuccess = { info ->
                    _uiState.update { state ->
                        val qualities = when (state.selectedFormat) {
                            ExtractionFormat.MP4 -> info.videoQualities
                            ExtractionFormat.MP3 -> info.audioQualities
                        }
                        val preferredQuality = qualities.firstOrNull { it.id == state.selectedQualityId }?.id
                            ?: qualities.firstOrNull()?.id.orEmpty()

                        state.copy(
                            videoInfo = info,
                            availableQualities = qualities,
                            selectedQualityId = preferredQuality,
                            isFetchingInfo = false,
                            isBackendOnline = true,
                            statusBanner = "Vídeo encontrado! Escolha o formato, qualidade e clique em Fazer Download."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isFetchingInfo = false,
                            statusBanner = null,
                            errorMessage = error.message ?: "Não foi possível obter informações do vídeo."
                        )
                    }
                }
            )
        }
    }

    /**
     * Inicia o download do vídeo (MP4) ou música (MP3) na qualidade e pasta escolhidas.
     */
    fun startDownload() {
        val currentState = _uiState.value
        val url = currentState.youtubeUrl.trim()

        if (url.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Informe a URL do vídeo do YouTube antes de baixar.") }
            return
        }
        if (currentState.outputDirectory.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Selecione uma pasta de destino para salvar o download.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDownloading = true,
                    errorMessage = null,
                    lastDownloadResult = null,
                    statusBanner = "Baixando e processando ${currentState.selectedFormat.title} (${currentState.selectedQualityId})..."
                )
            }

            ensureLocalBackendRunning(currentState.apiBaseUrl)

            val result = apiClient.downloadMedia(
                baseUrl = currentState.apiBaseUrl,
                youtubeUrl = url,
                format = currentState.selectedFormat,
                quality = currentState.selectedQualityId,
                outputDir = currentState.outputDirectory
            )

            result.fold(
                onSuccess = { dlResult ->
                    val historyItem = DownloadHistoryItem(
                        title = dlResult.title,
                        formatLabel = dlResult.format.uppercase(),
                        quality = dlResult.quality,
                        fileName = dlResult.fileName,
                        filePath = dlResult.filePath,
                        outputDir = dlResult.outputDir
                    )
                    _uiState.update { state ->
                        state.copy(
                            isDownloading = false,
                            isBackendOnline = true,
                            lastDownloadResult = dlResult,
                            downloadHistory = listOf(historyItem) + state.downloadHistory,
                            statusBanner = dlResult.message
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            statusBanner = null,
                            errorMessage = error.message ?: "Erro ao realizar o download."
                        )
                    }
                }
            )
        }
    }

    /**
     * Preenche uma URL de exemplo para facilitar testes rápidos durante os estudos.
     */
    fun fillSampleUrl() {
        onUrlChanged("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        fetchVideoMetadata()
    }
}
