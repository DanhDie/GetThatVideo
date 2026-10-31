# GetThatVideo — YouTube Video & MP3 Downloader (Kotlin Multiplatform)

Aplicativo multiplataforma desenvolvido em **Kotlin Multiplatform (Compose Multiplatform)** com backend em **Python (Flask + Pytubefix + FFmpeg)**, construído com base no repositório [`zararashraf/youtube-video-downloader-api`](https://github.com/zararashraf/youtube-video-downloader-api). A proposta do aplicativo é aprender a estrutura de Kotlin Multiplataform com um exemplo real, sinta-se livre para editar e testar o projeto

---

## Funcionalidades Implementadas

1. **Inserir a URL de um vídeo do YouTube**:
   - Suporte a links padrão (`youtube.com/watch?v=...`), encurtados (`youtu.be/...`), `Shorts` e `YouTube Music`.
   - Botão para colar diretamente da área de transferência e consultar os metadados do vídeo (título, canal, duração e visualizações).
2. **Selecionar o Tipo de Extração (`MP4` ou `MP3`)**:
   - **Vídeo (`MP4`)**: Baixa o vídeo com áudio integrado (une automaticamente streams adaptativos DASH em alta definição usando `FFmpeg`).
   - **Música / Áudio (`MP3`)**: Extrai somente o áudio e converte para `.mp3` real usando `FFmpeg`.
3. **Selecionar a Qualidade do Vídeo ou Música**:
   - Ao escolher **MP4**, exibe as resoluções disponíveis do vídeo (ex.: `1080p`, `720p`, `480p`, `360p`, `240p`, `144p`) com o tamanho estimado.
   - Ao escolher **MP3**, exibe os bitrates de áudio disponíveis (ex.: `160kbps`, `128kbps`, `70kbps`, `50kbps`, `48kbps`) com o tamanho estimado.
4. **Selecionar a Pasta de Destino do Download**:
   - Permite digitar o caminho manualmente ou clicar em **"Escolher Pasta..."** para abrir o seletor nativo de pastas do sistema operacional (`JFileChooser` no Desktop).
   - Inclui botão **"Abrir Pasta"** para abrir o Explorer/Finder diretamente no diretório onde o arquivo foi salvo.
5. **Fazer o Download + Histórico da Sessão**:
   - Faz o download exibindo feedback em tempo real e registra todos os arquivos baixados na sessão com o caminho completo.

---

## Como executar e editar

### 1. Executar o Aplicativo Desktop (JVM)
No Desktop, o próprio aplicativo Kotlin Multiplatform **inicia automaticamente** o servidor Python `backend/main.py` em segundo plano caso ele ainda não esteja rodando!

Basta rodar pelo terminal (ou dar dois cliques em `run-desktop.bat`):
```powershell
.\gradlew.bat :composeApp:run
```

Se preferir rodar o servidor Python separadamente em outro terminal para ver os logs da API Flask:
```powershell
python backend/main.py
```

### 2. Abrir para Edição no Android Studio / IntelliJ IDEA
1. Abra o **Android Studio** ou **IntelliJ IDEA**.
2. Clique em **Open** e selecione a pasta `C:\Users\Daniel\Documents\GTV\GetThatVideo`.
3. O Gradle já está pré-configurado em `gradle.properties` para utilizar o JDK 21 do Android Studio (`C:/Program Files/Android/Android Studio/jbr`) e o Android SDK local.

### 3. Onde Editar Cada Parte (Dicas de Estudo)
- **Quer mudar as cores ou o visual dos botões/cards?**
  Edite [`Theme.kt`](composeApp/src/commonMain/kotlin/com/gtv/app/ui/Theme.kt) e [`App.kt`](composeApp/src/commonMain/kotlin/com/gtv/app/ui/App.kt).
- **Quer adicionar um novo formato (ex: `.wav` ou `.webm`)?**
  1. Adicione a opção no enum `ExtractionFormat` em [`VideoModels.kt`](composeApp/src/commonMain/kotlin/com/gtv/app/model/VideoModels.kt).
  2. Trate o novo formato na função `perform_download` em [`backend/main.py`](backend/main.py).
- **Quer entender como o Kotlin Multiplatform conversa com o sistema operacional?**
  Compare [`PlatformServices.kt`](composeApp/src/commonMain/kotlin/com/gtv/app/platform/PlatformServices.kt) (`expect`) com [`PlatformServices.jvm.kt`](composeApp/src/jvmMain/kotlin/com/gtv/app/platform/PlatformServices.jvm.kt) e [`PlatformServices.android.kt`](composeApp/src/androidMain/kotlin/com/gtv/app/platform/PlatformServices.android.kt) (`actual`).
