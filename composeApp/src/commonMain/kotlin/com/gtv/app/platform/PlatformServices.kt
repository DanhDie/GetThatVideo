package com.gtv.app.platform

/**
 * Resposta HTTP genérica retornada pela implementação de cada plataforma.
 */
data class HttpResponseData(
    val statusCode: Int,
    val body: String
)

/**
 * Declarações `expect` do Kotlin Multiplatform.
 *
 * Cada plataforma (Desktop JVM em `jvmMain`, Android em `androidMain`, iOS em `iosMain`)
 * fornece sua própria implementação `actual` para estas funções nativas!
 */

/** Nome amigável da plataforma atual (ex: "Desktop (JVM)", "Android"). */
expect fun getPlatformName(): String

/** URL padrão onde a API Python Flask está rodando para a plataforma atual. */
expect fun getDefaultApiBaseUrl(): String

/** Caminho padrão da pasta de Downloads do usuário no sistema operacional. */
expect fun getDefaultDownloadDirectory(): String

/**
 * Abre o seletor nativo de pastas do sistema operacional.
 * Retorna o caminho escolhido pelo usuário ou `null` se cancelado.
 */
expect suspend fun pickDirectoryDialog(currentDirectory: String): String?

/**
 * Abre a pasta indicada no gerenciador de arquivos do sistema (Windows Explorer, Finder, etc.).
 */
expect fun openDirectoryInSystem(path: String): Boolean

/**
 * Executa uma requisição HTTP (GET ou POST com JSON) usando a pilha de rede nativa da plataforma.
 */
expect suspend fun performHttpRequest(
    url: String,
    method: String,
    jsonBody: String? = null,
    timeoutMs: Int = 180_000
): HttpResponseData

/**
 * No Desktop, verifica se o servidor local Python (`backend/main.py`) está rodando
 * e tenta iniciá-lo automaticamente caso esteja parado.
 */
expect suspend fun ensureLocalBackendRunning(baseUrl: String): Boolean
