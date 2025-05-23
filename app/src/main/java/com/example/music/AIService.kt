package com.example.music

import android.content.Context
import com.example.music.models.SongMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import kotlinx.coroutines.coroutineScope

class AIService(private val context: Context) {
    private val apiKey = context.getString(R.string.gemini_api_key)
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun getSongMetadata(fileName: String): SongMetadata = withContext(Dispatchers.IO) {
        val prompt = """
            Extract song metadata from this filename: "$fileName"
            Return a JSON object with these fields:
            {
                "title": "Song title without extension",
                "artist": "Artist name or 'Unknown Artist' if unclear",
                "genre": "Best guess of genre or null",
                "year": "Year if present in filename or null",
                "album": "Album name if present or null"
            }
        """.trimIndent()

        val response = makeGeminiRequest(prompt) ?: "{}"
        parseMetadataResponse(response, fileName)
    }

    suspend fun getChatResponse(userMessage: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are a music player assistant. Respond to the following user message:
            "$userMessage"
            Keep your response concise and helpful.
        """.trimIndent()

        makeGeminiRequest(prompt) ?: "Sorry, I couldn't process your request."
    }

    private suspend fun makeGeminiRequest(prompt: String): String? = coroutineScope {
        val url = URL("$baseUrl/gemini-pro:generateContent?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }

            val requestBody = """
                {
                    "contents": [{
                        "parts":[{"text": ${JSONObject().put("text", prompt).getString("text")}}]
                    }]
                }
            """.trimIndent()

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            
            jsonResponse.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseMetadataResponse(response: String, fallbackTitle: String): SongMetadata {
        return try {
            val json = JSONObject(response)
            SongMetadata(
                title = json.optString("title", fallbackTitle.substringBeforeLast(".")),
                artist = json.optString("artist", "Unknown Artist"),
                genre = json.optString("genre").takeUnless { it.isNullOrBlank() },
                year = json.optString("year").takeUnless { it.isNullOrBlank() }?.toIntOrNull(),
                album = json.optString("album").takeUnless { it.isNullOrBlank() }
            )
        } catch (e: Exception) {
            SongMetadata(
                title = fallbackTitle.substringBeforeLast("."),
                artist = "Unknown Artist",
                genre = null,
                year = null,
                album = null
            )
        }
    }
}