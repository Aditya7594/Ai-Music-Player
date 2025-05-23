package com.example.music

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiService(private val context: Context) {
    
    // Gemini API model
    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-pro",
            apiKey = getApiKey()
        )
    }
    
    // Get the API key from metadata
    private fun getApiKey(): String {
        return try {
            val applicationInfo = context.packageManager.getApplicationInfo(
                context.packageName, 
                android.content.pm.PackageManager.GET_META_DATA
            )
            applicationInfo.metaData.getString("com.google.ai.generative.GEMINI_API_KEY") ?: ""
        } catch (e: Exception) {
            Log.e("GeminiService", "Error getting API key", e)
            ""
        }
    }
    
    // Process user input with Gemini
    suspend fun processUserInput(userInput: String): String {
        return try {
            withContext(Dispatchers.IO) {
                val prompt = """
                    You are an AI assistant integrated into a music player app. 
                    Help the user with their music-related requests and provide brief, 
                    helpful responses about music. If the user input contains commands 
                    like "play", "pause", "next", "previous", extract that intent 
                    and respond with a format that starts with "COMMAND:" followed by 
                    the command name.
                    
                    For example:
                    - If user asks to play a song: respond with "COMMAND:play songname"
                    - If user asks to pause: respond with "COMMAND:pause"
                    - If user asks about music facts: respond with your knowledge
                    
                    The goal is to interpret the user's intent and return actionable commands 
                    for the music player while keeping general chat responses helpful and brief.
                    
                    User input: $userInput
                """.trimIndent()
                
                val response = generativeModel.generateContent(
                    content {
                        text(prompt)
                    }
                )
                
                processResponse(response)
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Error processing with Gemini", e)
            "I couldn't process that request. Please try again."
        }
    }
    
    private fun processResponse(response: GenerateContentResponse): String {
        val responseText = response.text?.trim() ?: 
            return "I couldn't understand that. Please try again."
        
        // Log the actual response for debugging
        Log.d("GeminiService", "Gemini response: $responseText")
        
        return responseText
    }
}
