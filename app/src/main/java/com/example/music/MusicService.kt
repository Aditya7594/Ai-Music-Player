package com.example.music

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import com.example.music.models.Song
import java.io.File

class MusicService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private val binder = MusicBinder()
    private var currentSongFile: File? = null
    private var songFiles: List<File> = emptyList()

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun loadSongs(): List<File> {
        val allSongFiles = mutableListOf<File>()
        
        try {
            // Check main Music directory
            val externalMusicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            if (externalMusicDir.exists() && externalMusicDir.isDirectory) {
                android.util.Log.d("MusicService", "Checking external music directory: ${externalMusicDir.absolutePath}")
                findMusicFiles(externalMusicDir, allSongFiles)
            }
            
            // Check Downloads directory
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists() && downloadsDir.isDirectory) {
                android.util.Log.d("MusicService", "Checking downloads directory: ${downloadsDir.absolutePath}")
                findMusicFiles(downloadsDir, allSongFiles)
            }
            
            // Check internal app storage
            val internalDir = getExternalFilesDir(null)
            if (internalDir != null && internalDir.exists()) {
                android.util.Log.d("MusicService", "Checking internal app directory: ${internalDir.absolutePath}")
                findMusicFiles(internalDir, allSongFiles)
            }
            
            // Check DCIM directory (some users store audio there)
            val dcimDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
            if (dcimDir.exists() && dcimDir.isDirectory) {
                android.util.Log.d("MusicService", "Checking DCIM directory: ${dcimDir.absolutePath}")
                findMusicFiles(dcimDir, allSongFiles)
            }
            
            if (allSongFiles.isEmpty()) {
                android.util.Log.d("MusicService", "No music files found, creating sample directory")
                // Create sample directory if no songs found
                val sampleDir = File(getExternalFilesDir(null), "Music")
                if (!sampleDir.exists()) {
                    sampleDir.mkdirs()
                }
            }
            
            android.util.Log.d("MusicService", "Found ${allSongFiles.size} music files")
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error loading songs", e)
        }
        
        songFiles = allSongFiles
        return songFiles
    }
    
    private fun findMusicFiles(directory: File, songList: MutableList<File>) {
        try {
            val files = directory.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isDirectory) {
                        // Don't recurse too deep to avoid performance issues
                        if (directory.absolutePath.split(File.separator).size < 10) {
                            findMusicFiles(file, songList)
                        }
                    } else if (file.isFile && file.extension.lowercase() in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a")) {
                        android.util.Log.d("MusicService", "Found music file: ${file.absolutePath}")
                        songList.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error scanning directory: ${directory.absolutePath}", e)
        }
    }

    fun playSong(filePath: String) {
        try {
            // Validate file path
            if (filePath.isBlank()) {
                android.util.Log.e("MusicService", "Invalid file path: empty or blank")
                return
            }
            
            val file = File(filePath)
            if (!file.exists()) {
                android.util.Log.e("MusicService", "File does not exist: $filePath")
                return
            }
            
            // Check file size to avoid corrupt files
            if (file.length() <= 0) {
                android.util.Log.e("MusicService", "File is empty: $filePath")
                return
            }
    
            // Safely release previous MediaPlayer
            try {
                if (mediaPlayer != null) {
                    if (mediaPlayer!!.isPlaying) {
                        mediaPlayer!!.stop()
                    }
                    mediaPlayer!!.reset()
                    mediaPlayer!!.release()
                    mediaPlayer = null
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "Error releasing media player", e)
            }
            
            // Create new MediaPlayer with robust error handling
            try {
                mediaPlayer = MediaPlayer()
                mediaPlayer?.apply {
                    setOnErrorListener { _, what, extra ->
                        android.util.Log.e("MusicService", "MediaPlayer error: what=$what, extra=$extra")
                        // Reset player on error
                        try {
                            reset()
                        } catch (e: Exception) {
                            android.util.Log.e("MusicService", "Error resetting media player", e)
                        }
                        true
                    }
                    
                    setOnCompletionListener {
                        android.util.Log.d("MusicService", "Playback completed for: ${file.name}")
                    }
                    
                    try {
                        setDataSource(filePath)
                        setOnPreparedListener {
                            try {
                                start()
                                android.util.Log.d("MusicService", "Started playing: ${file.name}")
                            } catch (e: Exception) {
                                android.util.Log.e("MusicService", "Error starting playback", e)
                            }
                        }
                        prepareAsync()
                    } catch (e: Exception) {
                        android.util.Log.e("MusicService", "Error setting data source: $filePath", e)
                        reset() // Reset on error
                    }
                }
                currentSongFile = file
            } catch (e: Exception) {
                android.util.Log.e("MusicService", "Error creating media player", e)
                mediaPlayer = null
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error in playSong", e)
        }
    }

    fun togglePlayPause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    android.util.Log.d("MusicService", "Paused playback")
                } else {
                    it.start()
                    android.util.Log.d("MusicService", "Resumed playback")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error in togglePlayPause", e)
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error checking isPlaying", e)
            false
        }
    }

    fun getCurrentPosition(): Long? = mediaPlayer?.currentPosition?.toLong()

    fun getDuration(): Long? {
        return try {
            mediaPlayer?.duration?.toLong()
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error getting duration", e)
            null
        }
    }

    fun seekTo(position: Long) {
        try {
            mediaPlayer?.seekTo(position.toInt())
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error seeking to position", e)
        }
    }

    fun getCurrentSong(): File? = currentSongFile

    fun playNext() {
        currentSongFile?.let { current ->
            val currentIndex = songFiles.indexOf(current)
            if (currentIndex != -1 && currentIndex < songFiles.size - 1) {
                playSong(songFiles[currentIndex + 1].absolutePath)
            }
        }
    }

    fun playPrevious() {
        currentSongFile?.let { current ->
            val currentIndex = songFiles.indexOf(current)
            if (currentIndex > 0) {
                playSong(songFiles[currentIndex - 1].absolutePath)
            }
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Error stopping playback", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
} 