package com.example.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.music.models.Song
import kotlinx.coroutines.*
import java.io.File

sealed class MusicPlayerState {
    object Stopped : MusicPlayerState()
    data class Playing(val song: Song, val position: Long, val duration: Long) : MusicPlayerState()
    data class Paused(val song: Song, val position: Long, val duration: Long) : MusicPlayerState()
}

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val mediaController = MediaController(application)
    private val songMetadataService = SongMetadataService(application)
    
    private val _playerState = MutableLiveData<MusicPlayerState>(MusicPlayerState.Stopped)
    val playerState: LiveData<MusicPlayerState> = _playerState
    
    private val _songs = MutableLiveData<List<Song>>(emptyList())
    val songs: LiveData<List<Song>> = _songs
    
    private val _filteredSongs = MutableLiveData<List<Song>>(emptyList())
    val filteredSongs: LiveData<List<Song>> = _filteredSongs
    
    private var progressUpdateJob: Job? = null
    
    init {
        loadSongs()
    }
    
    fun loadSongs() {
        viewModelScope.launch {
            try {
                val songFiles = mediaController.loadSongs()
                val loadedSongs = songFiles.mapNotNull { file ->
                    try {
                        val metadata = songMetadataService.getSongMetadata(file.name)
                        Song(
                            title = metadata.title,
                            artist = metadata.artist,
                            duration = file.length(),
                            filePath = file.absolutePath,
                            genre = metadata.genre,
                            year = metadata.year,
                            album = metadata.album
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                _songs.value = loadedSongs
                _filteredSongs.value = loadedSongs
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            mediaController.playSong(song.filePath)
            updatePlayerState(true)
            startProgressUpdates()
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            mediaController.togglePlayPause()
            updatePlayerState(mediaController.isPlaying())
            if (mediaController.isPlaying()) {
                startProgressUpdates()
            } else {
                stopProgressUpdates()
            }
        }
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            mediaController.seekTo(position)
            updatePlayerState(mediaController.isPlaying())
        }
    }

    fun playNext() {
        viewModelScope.launch {
            mediaController.playNext()
            updatePlayerState(true)
        }
    }

    fun playPrevious() {
        viewModelScope.launch {
            mediaController.playPrevious()
            updatePlayerState(true)
        }
    }
    
    // Method to update songs directly (used for sample songs when service isn't available)
    fun updateSongs(songList: List<Song>) {
        viewModelScope.launch {
            _songs.value = songList
            _filteredSongs.value = songList
        }
    }

    fun filterSongs(query: String?) {
        val allSongs = _songs.value ?: emptyList()
        _filteredSongs.value = if (query.isNullOrBlank()) {
            allSongs
        } else {
            allSongs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.album?.contains(query, ignoreCase = true) == true ||
                song.genre?.contains(query, ignoreCase = true) == true
            }
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive) {
                updatePlayerState(mediaController.isPlaying())
                delay(100) // Update every 100ms for smooth progress
            }
        }
    }

    private fun stopProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun updatePlayerState(isPlaying: Boolean) {
        val currentSong = songs.value?.firstOrNull { song ->
            song.filePath == mediaController.getCurrentSong()?.absolutePath
        } ?: return

        val position = mediaController.getCurrentPosition() ?: 0
        val duration = mediaController.getDuration() ?: 0

        val newState = if (isPlaying) {
            MusicPlayerState.Playing(currentSong, position, duration)
        } else {
            MusicPlayerState.Paused(currentSong, position, duration)
        }
        _playerState.value = newState
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        mediaController.release()
    }
}