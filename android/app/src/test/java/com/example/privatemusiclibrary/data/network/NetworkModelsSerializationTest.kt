package com.example.privatemusiclibrary.data.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NetworkModelsSerializationTest {

    private val gson = Gson()

    @Test
    fun loginRequest_serializesCorrectJsonKeys() {
        val request = LoginRequest(
            email = "user@example.com",
            password = "SecretPassword123!",
            device = "Pixel 8",
            platform = "Android"
        )

        val jsonString = gson.toJson(request)
        val json = gson.fromJson(jsonString, JsonObject::class.java)

        assertEquals("user@example.com", json.get("email")?.asString)
        assertEquals("SecretPassword123!", json.get("password")?.asString)
        assertEquals("Pixel 8", json.get("device")?.asString)
        assertEquals("Android", json.get("platform")?.asString)
    }

    @Test
    fun tokenResponse_deserializesCorrectly() {
        val json = """
            {
                "access_token": "mock_access_jwt",
                "refresh_token": "mock_refresh_token"
            }
        """.trimIndent()

        val response = gson.fromJson(json, TokenResponse::class.java)

        assertNotNull(response)
        assertEquals("mock_access_jwt", response.accessToken)
        assertEquals("mock_refresh_token", response.refreshToken)
    }

    @Test
    fun registerRequest_serializesCorrectJsonKeys() {
        val request = RegisterRequest(
            email = "newuser@example.com",
            password = "pwd",
            displayName = "New User",
            invitationToken = "token-123"
        )

        val jsonString = gson.toJson(request)
        val json = gson.fromJson(jsonString, JsonObject::class.java)

        assertEquals("newuser@example.com", json.get("email")?.asString)
        assertEquals("pwd", json.get("password")?.asString)
        assertEquals("New User", json.get("display_name")?.asString)
        assertEquals("token-123", json.get("invitation_token")?.asString)
    }

    @Test
    fun refreshRequest_serializesCorrectJsonKeys() {
        val request = RefreshRequest("my-refresh-token")
        val jsonString = gson.toJson(request)
        val json = gson.fromJson(jsonString, JsonObject::class.java)

        assertEquals("my-refresh-token", json.get("refresh_token")?.asString)
    }
}
