package com.jarves.mh.data

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubAuthManagerTest {

    private class InMemorySecretStore : SecretStore {
        private val map = mutableMapOf<String, String>()

        override fun put(providerId: String, secret: String) {
            map[providerId] = secret
        }

        override fun contains(providerId: String): Boolean = map.containsKey(providerId)

        override fun remove(providerId: String) {
            map.remove(providerId)
        }

        override fun get(providerId: String): String? = map[providerId]
    }

    @Test
    fun tokenStorageLifecycleWorksCorrectly() {
        val store = InMemorySecretStore()
        val authManager = GitHubAuthManager(store)

        assertFalse(authManager.hasToken())
        assertNull(authManager.getSavedToken())

        authManager.saveToken("gho_sampletoken123")
        assertTrue(authManager.hasToken())
        assertEquals("gho_sampletoken123", authManager.getSavedToken())

        authManager.clearToken()
        assertFalse(authManager.hasToken())
        assertNull(authManager.getSavedToken())
    }

    @Test
    fun exchangeCodeForTokenSucceedsAndSavesToken() = runBlocking {
        val store = InMemorySecretStore()
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val jsonResponse = """{"access_token": "gho_mock_access_token_999", "token_type": "bearer"}"""
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val authManager = GitHubAuthManager(store, mockClient)
        val result = authManager.exchangeCodeForToken("valid_auth_code", "client_id_123", "secret_abc")

        assertTrue(result.isSuccess)
        assertEquals("gho_mock_access_token_999", result.getOrNull())
        assertTrue(authManager.hasToken())
        assertEquals("gho_mock_access_token_999", authManager.getSavedToken())
    }

    @Test
    fun exchangeCodeForTokenFailsOnErrorResponse() = runBlocking {
        val store = InMemorySecretStore()
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val jsonResponse = """{"error": "bad_verification_code", "error_description": "The code passed is incorrect or has expired."}"""
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val authManager = GitHubAuthManager(store, mockClient)
        val result = authManager.exchangeCodeForToken("expired_code")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("The code passed is incorrect") == true)
        assertFalse(authManager.hasToken())
    }

    @Test
    fun exchangeCodeForTokenFailsOnHttpError() = runBlocking {
        val store = InMemorySecretStore()
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("Server error".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()

        val authManager = GitHubAuthManager(store, mockClient)
        val result = authManager.exchangeCodeForToken("any_code")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("HTTP 500") == true)
    }

    @Test
    fun oauthCredentialsCanBeSavedAndUsedAsDefaults() = runBlocking {
        val store = InMemorySecretStore()
        var capturedPayload: String? = null
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val buffer = okio.Buffer()
                chain.request().body?.writeTo(buffer)
                capturedPayload = buffer.readUtf8()

                val jsonResponse = """{"access_token": "gho_saved_cred_token", "token_type": "bearer"}"""
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val authManager = GitHubAuthManager(store, mockClient)
        authManager.saveOAuthCredentials("my_client_id_456", "my_client_secret_789")

        assertEquals("my_client_id_456", authManager.getClientId())
        assertEquals("my_client_secret_789", authManager.getClientSecret())

        val result = authManager.exchangeCodeForToken("my_auth_code")
        assertTrue(result.isSuccess)
        assertEquals("gho_saved_cred_token", result.getOrNull())
        assertTrue(capturedPayload?.contains("my_client_id_456") == true)
        assertTrue(capturedPayload?.contains("my_client_secret_789") == true)
    }
}
