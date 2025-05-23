package com.example.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.io.File

class MediaController(private val context: Context) {
    private var musicService: MusicService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            bound = false
        }
    }

    init {
        val intent = Intent(context, MusicService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun loadSongs(): List<File> {
        return musicService?.loadSongs() ?: emptyList()
    }

    fun playSong(filePath: String) {
        musicService?.playSong(filePath)
    }

    fun togglePlayPause() {
        musicService?.togglePlayPause()
    }

    fun isPlaying(): Boolean = musicService?.isPlaying() ?: false

    fun getCurrentPosition(): Long? = musicService?.getCurrentPosition()

    fun getDuration(): Long? = musicService?.getDuration()

    fun seekTo(position: Long) {
        musicService?.seekTo(position)
    }

    fun getCurrentSong(): File? = musicService?.getCurrentSong()

    fun playNext() {
        musicService?.playNext()
    }

    fun playPrevious() {
        musicService?.playPrevious()
    }

    fun release() {
        if (bound) {
            context.unbindService(connection)
            bound = false
        }
    }
} 