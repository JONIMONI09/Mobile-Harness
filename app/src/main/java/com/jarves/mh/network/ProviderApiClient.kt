package com.jarves.mh.network

import com.jarves.mh.model.ProviderProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiscoveredModel(val id: String, val displayName: String = id, val isFree: Boolean = false)

sealed interface ModelDiscoveryResult {
    data class Success(val models: List<DiscoveredModel>, val endpoint: String) : ModelDiscoveryResult
    data class Failure(val message: String) : ModelDiscoveryResult
}

sealed interface ConnectionValidation {
    data class Success(val message: String) : ConnectionValidation
    data class Failure(val message: String) : ConnectionValidation
}

class ProviderApiClient {
    suspend fun discoverModels(
        baseUrl: String,
        apiKey: String,
        protocol: ProviderProtocol,
    ): ModelDiscoveryResult = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            return@withContext ModelDiscoveryResult.Failure("Enter a base URL and API key first.")
        }

        var authError = false
        var lastMessage = "This provider did not expose a model list. You can enter a custom model name."
        for (endpoint in modelEndpoints(baseUrl, protocol)) {
            val response = request(endpoint, "GET", apiKey, protocol = protocol)
            when {
                response.code == 401 || response.code == 403 -> authError = true
                response.code in 200..299 -> {
                    val models = ModelResponseParser.parse(response.body)
                    if (models.isNotEmpty()) return@withContext ModelDiscoveryResult.Success(models, endpoint)
                    lastMessage = "The provider replied, but its model list was empty or unsupported."
                }
                response.code > 0 && response.code != 404 -> lastMessage = friendlyHttpError(response.code)
                response.error != null -> lastMessage = response.error
            }
        }
        ModelDiscoveryResult.Failure(if (authError) "The API key was rejected. Check the key and try again." else lastMessage)
    }

    suspend fun validate(
        baseUrl: String,
        model: String,
        apiKey: String,
        protocol: ProviderProtocol,
        discoveredModels: List<DiscoveredModel>,
    ): ConnectionValidation = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || model.isBlank() || apiKey.isBlank()) {
            return@withContext ConnectionValidation.Failure("Base URL, model, and API key are required.")
        }
        val body = validationBody(model, protocol)
        // Many custom gateways already end in /v1 (or /anthropic). Claude Code
        // appends /v1/messages to ANTHROPIC_BASE_URL itself, so a naive
        // "$base/v1/messages" probe would double the path (…/v1/v1/messages)
        // and reject working endpoints with a false 404. Probe every candidate
        // path the runtime could end up using and accept the first one the
        // provider actually answers with a protocol response (not 404).
        var authRejected = false
        var lastCode = 0
        var lastBody = ""
        var lastError: String? = null
        var modelRejected = false
        for (endpoint in messagesEndpointCandidates(baseUrl, protocol)) {
            val response = request(endpoint, "POST", apiKey, body, protocol, connectTimeoutMs = 8_000, readTimeoutMs = 10_000)
            when {
                response.code in 200..299 -> return@withContext ConnectionValidation.Success(
                    if (protocol == ProviderProtocol.ANTHROPIC || protocol == ProviderProtocol.ANTHROPIC_GATEWAY || protocol == ProviderProtocol.OPENROUTER) {
                        "Anthropic Messages endpoint verified. Claude Code settings are ready."
                    } else {
                        "Connection successful. Claude Code settings are ready."
                    },
                )
                response.code == 401 || response.code == 403 -> authRejected = true
                response.code == 400 && response.body.contains("model", ignoreCase = true) -> modelRejected = true
                response.code == 404 -> Unit
                response.code > 0 -> {
                    lastCode = response.code
                    lastBody = response.body
                }
                response.error != null -> lastError = response.error
            }
        }
        when {
            authRejected -> ConnectionValidation.Failure("The API key was rejected.")
            modelRejected -> ConnectionValidation.Failure("The provider did not accept model '$model'. Choose a listed model or check its exact name.")
            lastCode == 404 -> ConnectionValidation.Failure("The API endpoint was not found. Check the base URL.")
            lastCode > 0 -> ConnectionValidation.Failure(friendlyHttpError(lastCode, lastBody))
            lastError?.contains("timed out", ignoreCase = true) == true ->
                ConnectionValidation.Failure("Connection timed out after 10 seconds.")
            else -> ConnectionValidation.Failure(lastError ?: "Could not connect to the provider.")
        }
    }

    private fun request(
        endpoint: String,
        method: String,
        apiKey: String,
        body: String? = null,
        protocol: ProviderProtocol,
        connectTimeoutMs: Int = 12_000,
        readTimeoutMs: Int = 20_000,
    ): HttpResult {
        return runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                if (protocol != ProviderProtocol.OPENROUTER && protocol != ProviderProtocol.OPENAI_CHAT && protocol != ProviderProtocol.OPENAI_RESPONSES) {
                    setRequestProperty("x-api-key", apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                }
                if (body != null) doOutput = true
            }
            if (body != null) connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            HttpResult(code, responseBody)
        }.getOrElse { HttpResult(0, "", it.message ?: "Network connection failed",) }
    }

    private fun modelEndpoints(baseUrl: String, protocol: ProviderProtocol): List<String> {
        val base = baseUrl.trim().trimEnd('/')
        val withoutAnthropic = base.removeSuffix("/anthropic")
        val candidates = when (protocol) {
            ProviderProtocol.OPENROUTER -> listOf("$base/v1/models")
            ProviderProtocol.OPENAI_CHAT, ProviderProtocol.OPENAI_RESPONSES -> buildList {
                add("$base/models")
                if (!base.endsWith("/v1")) add("$base/v1/models")
            }
            else -> listOf("$base/v1/models", "$base/models", "$withoutAnthropic/models", "$withoutAnthropic/v1/models")
        }
        return candidates.distinct()
    }

    /**
     * Candidate chat endpoints for validation, ordered from most to least
     * likely. Mirrors modelEndpoints() so providers whose base URL already
     * contains /v1 or /anthropic are probed without a doubled path segment.
     */
    private fun messagesEndpointCandidates(baseUrl: String, protocol: ProviderProtocol): List<String> {
        val base = baseUrl.trim().trimEnd('/')
        return when (protocol) {
            ProviderProtocol.OPENROUTER -> listOf("$base/v1/messages")
            ProviderProtocol.OPENAI_CHAT -> listOf("$base/chat/completions")
            ProviderProtocol.OPENAI_RESPONSES -> listOf("$base/responses")
            else -> {
                val withoutAnthropic = base.removeSuffix("/anthropic")
                buildList {
                    // Bases that already end in /v1 take /messages directly;
                    // appending /v1/messages again would double the segment.
                    add("$base/messages")
                    if (!base.endsWith("/v1")) add("$base/v1/messages")
                    if (withoutAnthropic != base) {
                        if (!withoutAnthropic.endsWith("/v1")) add("$withoutAnthropic/v1/messages")
                        add("$withoutAnthropic/messages")
                    }
                }.distinct()
            }
        }
    }

    private fun validationBody(model: String, protocol: ProviderProtocol): String = when (protocol) {
        ProviderProtocol.OPENAI_RESPONSES -> JSONObject()
            .put("model", model)
            .put("max_output_tokens", 1)
            .put("input", "Reply OK")
            .toString()
        ProviderProtocol.OPENAI_CHAT -> JSONObject()
            .put("model", model)
            .put("max_tokens", 1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Reply OK")))
            .toString()
        else -> JSONObject()
            .put("model", model)
            .put("max_tokens", 1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Reply OK")))
            .toString()
    }

    private fun friendlyHttpError(code: Int, body: String = ""): String {
        // Surface provider-supplied detail (e.g. quota, invalid model) so users
        // no longer see a generic HTTP code for well-known API errors.
        val detail = runCatching {
            (JSONObject(body).optJSONObject("error") as? JSONObject)?.optString("message").orEmpty()
        }.getOrNull().orEmpty().ifBlank { body.take(200) }
        val base = when (code) {
            429 -> "The provider rate limit was reached. Wait a moment and try again."
            in 500..599 -> "The provider is temporarily unavailable (HTTP $code)."
            else -> "The provider returned HTTP $code. Check the URL and account access."
        }
        return if (detail.isBlank()) base else "$base Detail: $detail"
    }

    private data class HttpResult(val code: Int, val body: String, val error: String? = null)
}

object ModelResponseParser {
    fun parse(json: String): List<DiscoveredModel> = runCatching {
        val trimmed = json.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
            }
        }
        buildList {
            for (index in 0 until array.length()) {
                when (val item = array.opt(index)) {
                    is String -> add(DiscoveredModel(item))
                    is JSONObject -> {
                        val id = item.optString("id").ifBlank { item.optString("name") }
                        if (id.isNotBlank()) {
                            val label = item.optString("display_name").ifBlank { item.optString("displayName") }.ifBlank { id }
                            val pricing = item.optJSONObject("pricing")
                            val free = id.endsWith(":free", ignoreCase = true) || pricing?.let {
                                listOf("prompt", "completion", "request").all { field ->
                                    it.optString(field, "0").toDoubleOrNull() == 0.0
                                }
                            } == true
                            add(DiscoveredModel(id, label, free))
                        }
                    }
                }
            }
        }.distinctBy { it.id }.sortedWith(compareByDescending<DiscoveredModel> { it.isFree }.thenBy { it.displayName.lowercase() })
    }.getOrDefault(emptyList())
}
