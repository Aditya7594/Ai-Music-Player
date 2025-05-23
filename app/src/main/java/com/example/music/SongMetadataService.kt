package com.example.music

import android.content.Context
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SongMetadataService(private val context: Context) {
    private val metadataRetriever = MediaMetadataRetriever()

    suspend fun getSongMetadata(fileName: String): SongMetadata = withContext(Dispatchers.IO) {
        val file = File(fileName)
        if (!file.exists()) {
            return@withContext createFallbackMetadata(file.nameWithoutExtension)
        }

        try {
            metadataRetriever.setDataSource(file.absolutePath)
            
            val title = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension
            val artist = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "Unknown Artist"
            val genre = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val year = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
            val album = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

            SongMetadata(
                title = title,
                artist = artist,
                genre = genre,
                year = year,
                album = album
            )
        } catch (e: Exception) {
            createFallbackMetadata(file.nameWithoutExtension)
        } finally {
            try {
                metadataRetriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    private fun createFallbackMetadata(fileName: String): SongMetadata {
        // Try to extract artist and title from common filename patterns
        // e.g. "Artist - Title" or "Artist_Title" or "Title"
        val parts = fileName.split(" - ", "_", limit = 2)
        return if (parts.size > 1) {
            SongMetadata(
                title = parts[1].trim(),
                artist = parts[0].trim(),
                genre = null,
                year = null,
                album = null
            )
        } else {
            SongMetadata(
                title = fileName,
                artist = "Unknown Artist",
                genre = null,
                year = null,
                album = null
            )
        }
    }
}

data class SongMetadata(
    val title: String,
    val artist: String,
    val genre: String?,
    val year: Int?,
    val album: String?
)