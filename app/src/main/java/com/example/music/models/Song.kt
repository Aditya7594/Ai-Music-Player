package com.example.music.models

data class Song(
    val title: String,
    val artist: String,
    val filePath: String,
    val duration: Long,
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null
)

data class SongMetadata(
    val title: String,
    val artist: String,
    val genre: String?,
    val year: Int?,
    val album: String?
)
