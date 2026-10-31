package com.gtv.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gtv.app.model.DownloadHistoryItem
import com.gtv.app.model.DownloadResult
import com.gtv.app.model.ExtractionFormat
import com.gtv.app.model.QualityOption
import com.gtv.app.model.VideoInfo
import com.gtv.app.platform.getPlatformName
import com.gtv.app.viewmodel.DownloaderUiState
import com.gtv.app.viewmodel.DownloaderViewModel

/**
 * Componente raiz compartilhado entre todas as plataformas (Desktop JVM, Android, iOS).
 *
 * Para estudo:
 * - Observe como [DownloaderViewModel] mantém o estado em `StateFlow` ([DownloaderUiState])
 * - A função `@Composable App` observa esse estado com `collectAsState()` e redesenha a UI automaticamente.
 */
@Composable
fun App(
    viewModel: DownloaderViewModel = viewModel { DownloaderViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    var isDarkTheme by remember { mutableStateOf(true) }
    var showApiSettings by remember { mutableStateOf(false) }

    GetThatVideoTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 880.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ------------------------------------------------------------------
                    // CABEÇALHO DO APLICATIVO + STATUS DO SERVIDOR
                    // ------------------------------------------------------------------
                    HeaderSection(
                        uiState = uiState,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme },
                        onToggleApiSettings = { showApiSettings = !showApiSettings },
                        onRefreshBackend = { viewModel.connectOrStartBackend() }
                    )

                    AnimatedVisibility(visible = showApiSettings) {
                        ApiSettingsCard(
                            apiBaseUrl = uiState.apiBaseUrl,
                            onApiBaseUrlChanged = viewModel::onApiBaseUrlChanged,
                            onTestConnection = viewModel::connectOrStartBackend
                        )
                    }

                    // ------------------------------------------------------------------
                    // PASSO 1: COLOCAR A URL DE UM VÍDEO DO YOUTUBE
                    // ------------------------------------------------------------------
                    UrlInputSection(
                        url = uiState.youtubeUrl,
                        isFetchingInfo = uiState.isFetchingInfo,
                        onUrlChanged = viewModel::onUrlChanged,
                        onFetchInfo = viewModel::fetchVideoMetadata,
                        onFillSample = viewModel::fillSampleUrl
                    )

                    // Card com detalhes do vídeo (aparece quando o vídeo é consultado)
                    uiState.videoInfo?.let { info ->
                        VideoDetailsCard(info = info)
                    }

                    // ------------------------------------------------------------------
                    // PASSO 2: SELECIONAR O TIPO DE EXTRAÇÃO (MP4 OU MP3)
                    // ------------------------------------------------------------------
                    ExtractionFormatSection(
                        selectedFormat = uiState.selectedFormat,
                        onFormatSelected = viewModel::onFormatSelected
                    )

                    // ------------------------------------------------------------------
                    // PASSO 3: SELECIONAR QUALIDADE DO VÍDEO OU MÚSICA
                    // ------------------------------------------------------------------
                    QualitySelectionSection(
                        selectedFormat = uiState.selectedFormat,
                        qualities = uiState.availableQualities,
                        selectedQualityId = uiState.selectedQualityId,
                        hasFetchedVideoInfo = uiState.videoInfo != null,
                        onQualitySelected = viewModel::onQualitySelected
                    )

                    // ------------------------------------------------------------------
                    // PASSO 4: SELECIONAR A PASTA ONDE SERÁ O DOWNLOAD
                    // ------------------------------------------------------------------
                    DestinationFolderSection(
                        outputDirectory = uiState.outputDirectory,
                        onOutputDirChanged = viewModel::onOutputDirChanged,
                        onBrowseFolder = viewModel::browseOutputDirectory,
                        onOpenFolder = { viewModel.openFolder() }
                    )

                    // ------------------------------------------------------------------
                    // PASSO 5: FAZER O DOWNLOAD + FEEDBACK E HISTÓRICO
                    // ------------------------------------------------------------------
                    DownloadActionSection(
                        uiState = uiState,
                        onStartDownload = viewModel::startDownload,
                        onOpenFolder = viewModel::openFolder
                    )

                    if (uiState.downloadHistory.isNotEmpty()) {
                        DownloadHistorySection(
                            history = uiState.downloadHistory,
                            onOpenFolder = viewModel::openFolder
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// ==============================================================================
// SEÇÕES MODULARES DA INTERFACE (FÁCEIS DE EDITAR E ESTUDAR)
// ==============================================================================

@Composable
private fun HeaderSection(
    uiState: DownloaderUiState,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onToggleApiSettings: () -> Unit,
    onRefreshBackend: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = "GetThatVideo • KMP YouTube Downloader",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kotlin Multiplatform (${getPlatformName()}) + API Python Pytubefix",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToggleApiSettings) {
                        Text("API")
                    }
                    OutlinedButton(onClick = onToggleTheme) {
                        Text(if (isDarkTheme) "☀ Claro" else "🌙 Escuro")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Barra de status do servidor backend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dotColor = if (uiState.isBackendOnline) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    val statusText = when {
                        uiState.isCheckingBackend -> "Verificando servidor Python local..."
                        uiState.isBackendOnline -> buildString {
                            append("API Python Conectada (${uiState.apiBaseUrl})")
                            if (uiState.isFfmpegAvailable) {
                                append(" • FFmpeg Ativo (MP3 & Full HD)")
                            }
                        }
                        else -> "Servidor API offline em ${uiState.apiBaseUrl}"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onRefreshBackend) {
                    Text("Reconectar")
                }
            }
        }
    }
}

@Composable
private fun ApiSettingsCard(
    apiBaseUrl: String,
    onApiBaseUrlChanged: (String) -> Unit,
    onTestConnection: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Configuração do Servidor Backend (youtube-video-downloader-api)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = apiBaseUrl,
                    onValueChange = onApiBaseUrlChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("URL da API Flask") },
                    singleLine = true
                )
                Button(onClick = onTestConnection) {
                    Text("Testar / Iniciar")
                }
            }
        }
    }
}

@Composable
private fun UrlInputSection(
    url: String,
    isFetchingInfo: Boolean,
    onUrlChanged: (String) -> Unit,
    onFetchInfo: () -> Unit,
    onFillSample: () -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    StepCard(
        stepNumber = "1",
        title = "URL do Vídeo do YouTube",
        subtitle = "Cole o link do vídeo ou música do YouTube e clique em 'Carregar Qualidades' para inspecionar as opções reais."
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Link do vídeo (ex: https://www.youtube.com/watch?v=...)") },
            placeholder = { Text("https://www.youtube.com/watch?v=...") },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onFetchInfo,
                enabled = !isFetchingInfo,
                modifier = Modifier.weight(1f)
            ) {
                if (isFetchingInfo) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Consultando API...")
                } else {
                    Text("🔍 Carregar Informações e Qualidades")
                }
            }

            OutlinedButton(
                onClick = {
                    val clipText = clipboardManager.getText()?.text
                    if (!clipText.isNullOrBlank()) {
                        onUrlChanged(clipText.trim())
                    }
                }
            ) {
                Text("📋 Colar")
            }

            if (url.isNotBlank()) {
                OutlinedButton(onClick = { onUrlChanged("") }) {
                    Text("Limpar")
                }
            } else {
                TextButton(onClick = onFillSample) {
                    Text("Usar Exemplo")
                }
            }
        }
    }
}

@Composable
private fun VideoDetailsCard(info: VideoInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "VÍDEO IDENTIFICADO PELA API",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👤 Canal: ${info.author}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "⏱ Duração: ${info.formattedDuration()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (info.views > 0) {
                    Text(
                        text = "👁 ${info.formattedViews()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtractionFormatSection(
    selectedFormat: ExtractionFormat,
    onFormatSelected: (ExtractionFormat) -> Unit
) {
    StepCard(
        stepNumber = "2",
        title = "Selecionar Tipo de Extração (MP4 ou MP3)",
        subtitle = "Escolha se deseja baixar o vídeo completo (MP4) ou extrair somente a música/áudio (MP3)."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExtractionFormat.entries.forEach { format ->
                val isSelected = format == selectedFormat
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                val bgColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onFormatSelected(format) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (format == ExtractionFormat.MP4) "🎬 ${format.title}" else "🎵 ${format.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isSelected) {
                                Text(
                                    text = "✓ Selecionado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = format.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualitySelectionSection(
    selectedFormat: ExtractionFormat,
    qualities: List<QualityOption>,
    selectedQualityId: String,
    hasFetchedVideoInfo: Boolean,
    onQualitySelected: (String) -> Unit
) {
    val sectionTitle = if (selectedFormat == ExtractionFormat.MP4) {
        "Selecionar Qualidade do Vídeo (Resolução MP4)"
    } else {
        "Selecionar Qualidade da Música / Áudio (Bitrate MP3)"
    }

    val sectionSubtitle = if (hasFetchedVideoInfo) {
        "Qualidades reais encontradas para este vídeo no YouTube:"
    } else {
        "Qualidades padrão (clique em 'Carregar Informações' acima para ver o tamanho exato de cada opção):"
    }

    StepCard(
        stepNumber = "3",
        title = sectionTitle,
        subtitle = sectionSubtitle
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            qualities.forEach { option ->
                val isSelected = option.id == selectedQualityId
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                val bgColor = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }

                Card(
                    modifier = Modifier
                        .widthIn(min = 145.dp)
                        .clickable { onQualitySelected(option.id) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            option.formattedFileSize()?.let { sizeStr ->
                                Text(
                                    text = "($sizeStr)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Text(
                            text = option.detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationFolderSection(
    outputDirectory: String,
    onOutputDirChanged: (String) -> Unit,
    onBrowseFolder: () -> Unit,
    onOpenFolder: () -> Unit
) {
    StepCard(
        stepNumber = "4",
        title = "Selecionar Pasta de Destino do Download",
        subtitle = "Escolha ou digite o caminho da pasta onde o arquivo MP4 ou MP3 será salvo."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = outputDirectory,
                onValueChange = onOutputDirChanged,
                modifier = Modifier.weight(1f),
                label = { Text("Pasta onde o arquivo será salvo") },
                singleLine = true
            )

            Button(onClick = onBrowseFolder) {
                Text("📁 Escolher Pasta...")
            }

            OutlinedButton(onClick = onOpenFolder) {
                Text("📂 Abrir")
            }
        }
    }
}

@Composable
private fun DownloadActionSection(
    uiState: DownloaderUiState,
    onStartDownload: () -> Unit,
    onOpenFolder: (String) -> Unit
) {
    StepCard(
        stepNumber = "5",
        title = "Fazer o Download",
        subtitle = "Confirme suas escolhas e inicie a extração e download para a pasta selecionada."
    ) {
        // Resumo rápido da configuração atual
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Formato: ${uiState.selectedFormat.title}  •  Qualidade: ${uiState.selectedQualityId}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Extensão: ${uiState.selectedFormat.fileExtension}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Botão principal de Download
        Button(
            onClick = onStartDownload,
            enabled = !uiState.isDownloading && !uiState.isFetchingInfo,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            if (uiState.isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Baixando ${uiState.selectedFormat.title} (${uiState.selectedQualityId})...",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "⬇ FAZER DOWNLOAD AGORA (${uiState.selectedFormat.id.uppercase()} • ${uiState.selectedQualityId})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (uiState.isDownloading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Exibição de erros, se houver
        uiState.errorMessage?.let { errorMsg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text(
                    text = "⚠ $errorMsg",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Exibição de resultado de sucesso do último download
        uiState.lastDownloadResult?.let { result ->
            DownloadSuccessCard(
                result = result,
                onOpenFolder = { onOpenFolder(result.outputDir) }
            )
        }
    }
}

@Composable
private fun DownloadSuccessCard(
    result: DownloadResult,
    onOpenFolder: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✅ ${result.message}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = onOpenFolder) {
                    Text("📂 Abrir Pasta do Arquivo")
                }
            }
            Text(
                text = "Arquivo: ${result.fileName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Salvo em: ${result.filePath}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DownloadHistorySection(
    history: List<DownloadHistoryItem>,
    onOpenFolder: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Histórico de Downloads da Sessão (${history.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            history.forEach { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.formatLabel} (${item.quality}) • ${item.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.filePath,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { onOpenFolder(item.outputDir) }) {
                            Text("Abrir Pasta")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    stepNumber: String,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stepNumber,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}
