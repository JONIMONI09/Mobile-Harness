package com.jarves.mh.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubAuthManager(
    private val secretStore: SecretStore,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {

    companion object {
        const val KEY_GITHUB_TOKEN = "github_oauth_token"
        const val KEY_GITHUB_CLIENT_ID = "github_client_id"
        const val KEY_GITHUB_CLIENT_SECRET = "github_client_secret"
        const val OAUTH_TOKEN_URL = "https://github.com/login/oauth/access_token"
    }

    fun getSavedToken(): String? = secretStore.get(KEY_GITHUB_TOKEN)

    fun hasToken(): Boolean = secretStore.contains(KEY_GITHUB_TOKEN) && !getSavedToken().isNullOrBlank()

    fun saveToken(token: String) {
        if (token.isNotBlank()) {
            secretStore.put(KEY_GITHUB_TOKEN, token.trim())
        }
    }

    fun clearToken() {
        secretStore.remove(KEY_GITHUB_TOKEN)
    }

    fun getClientId(): String = secretStore.get(KEY_GITHUB_CLIENT_ID).orEmpty()

    fun getClientSecret(): String = secretStore.get(KEY_GITHUB_CLIENT_SECRET).orEmpty()

    fun saveOAuthCredentials(clientId: String, clientSecret: String) {
        if (clientId.isNotBlank()) secretStore.put(KEY_GITHUB_CLIENT_ID, clientId.trim())
        if (clientSecret.isNotBlank()) secretStore.put(KEY_GITHUB_CLIENT_SECRET, clientSecret.trim())
    }

    fun buildOAuthAuthorizeUrl(
        clientId: String = getClientId(),
        redirectUri: String = "mobileharness://github-callback",
        scope: String = "repo,workflow,read:org",
    ): String? {
        val id = clientId.trim()
        if (id.isBlank()) return null
        return "https://github.com/login/oauth/authorize?client_id=$id&redirect_uri=$redirectUri&scope=$scope"
    }

    suspend fun exchangeCodeForToken(
        code: String,
        clientId: String = getClientId(),
        clientSecret: String = getClientSecret(),
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("client_id", clientId)
                put("client_secret", clientSecret)
                put("code", code)
            }

            val requestBody = jsonPayload.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(OAUTH_TOKEN_URL)
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("HTTP ${response.code}: $responseBody")
                )
            }

            val responseJson = JSONObject(responseBody)
            if (responseJson.has("error")) {
                val errorDesc = responseJson.optString("error_description", responseJson.optString("error"))
                return@withContext Result.failure(IOException(errorDesc))
            }

            val accessToken = responseJson.optString("access_token")
            if (accessToken.isNullOrBlank()) {
                return@withContext Result.failure(IOException("No access_token found in GitHub response"))
            }

            saveToken(accessToken)
            Result.success(accessToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
