import android.os.Bundle
import android.os.Build
import java.io.File
import androidx.appcompat.app.AppCompatActivity
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ImageButton
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.music.databinding.ActivityMainBinding
import com.example.music.models.Song
import com.example.music.models.ChatMessage
import com.example.music.SongAdapter
import com.example.music.ChatAdapter
import com.example.music.MusicService
import com.example.music.GeminiService
import com.example.music.MusicPlayerViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.speech.tts.TextToSpeech
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts

import com.example.music.MusicPlayerState
import com.example.music.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    companion object {
        private const val PREFS_NAME = "UserData"
    }

    // Permission launchers
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initializeMusicService()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (hasStoragePermission()) {
            initializeMusicService()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    // UI Elements
    private lateinit var binding: ActivityMainBinding
    private lateinit var songInfoText: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var songListRecyclerView: RecyclerView
    private lateinit var inputText: EditText
    private lateinit var songListDialog: BottomSheetDialog
    private lateinit var songAdapter: SongAdapter


    // Service related
    private var musicService: MusicService? = null
    private var isMusicServiceBound = false

    private fun initializeViews() {
        try {
            // Initialize all UI elements
            songInfoText = binding.songInfo
            playPauseButton = binding.btnPlayPause
            songListRecyclerView = binding.chatRecyclerView
            inputText = binding.inputText
            songListDialog = BottomSheetDialog(this)

            // Setup RecyclerView with null safety
            songListRecyclerView.layoutManager = LinearLayoutManager(this)
            songAdapter = SongAdapter { song -> song?.let { playSong(it) } }
            songListRecyclerView.adapter = songAdapter

            Log.d("MainActivity", "Views initialized successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing views", e)
            throw e // Rethrow to handle in onCreate
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readPermission = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            readPermission == PackageManager.PERMISSION_GRANTED
        }
    }



    private fun requestStoragePermission() {
        try {
            if (hasStoragePermission()) {
                Toast.makeText(this, R.string.permission_already_granted, Toast.LENGTH_SHORT).show()
                initializeMusicService()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        addCategory("android.intent.category.DEFAULT")
                        data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    }
                    manageStoragePermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error launching manage storage permission", e)
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStoragePermissionLauncher.launch(intent)
                }
            } else {
                storagePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting storage permission", e)
            Toast.makeText(this, R.string.permission_error, Toast.LENGTH_SHORT).show()
        }
    }

    // ViewModel and Services
    private lateinit var viewModel: MusicPlayerViewModel
    private lateinit var geminiService: GeminiService
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()

    // Coroutine
    private lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    // Text to Speech
    private var textToSpeech: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentSongIndex = -1
    private var isPlaying = false

    // Service Connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as? MusicService.LocalBinder
                if (binder == null) {
                    Log.e("MainActivity", "Service binder is null")
            return
                }

                musicService = binder.getService()
                isMusicServiceBound = true

                // Initialize music service
                musicService?.let {
                    it.initializeMediaPlayer()
                    setupMediaPlayerListeners()
                    loadSongs()
                    Log.d("MainActivity", "Music service initialized successfully")
                } ?: run {
                    Log.e("MainActivity", "Music service is null after binding")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error connecting to music service", e)
                Toast.makeText(this, "Error connecting to music service", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isMusicServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // Initialize views first
            initializeViews()

            // Initialize ViewModel
            viewModel = ViewModelProvider(this)[MusicPlayerViewModel::class.java]

            // Setup UI components
            setupChatInterface()
            setupClickListeners()
            setupObservers()
            
            // Check permissions and initialize music service last
            if (savedInstanceState == null) {
                // Only check permissions on fresh start
                Log.d("MainActivity", "Fresh start, checking permissions")
                checkStoragePermission()
                } else {
                Log.d("MainActivity", "Restored from saved state")
                // Reconnect to service if we're being restored
                if (hasStoragePermission()) {
                    initializeMusicService()
                }
            }
            
            Log.d("MainActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            // Try to continue with at least sample songs
            loadSampleSongs()
        }
    }

    private fun setupClickListeners() {
        playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            musicService?.playNext()
        }

        binding.btnPrevious.setOnClickListener {
            musicService?.playPrevious()
        }

        binding.btnMenu.setOnClickListener {
            songListDialog.show()
        }

        binding.sendButton.setOnClickListener {
            val userInput = inputText.text.toString()
            if (userInput.isNotEmpty()) {
                processUserInput(userInput)
                inputText.text.clear()
            }
        }
    }

    private fun setupObservers() {
        viewModel.playerState.observe(this) { state ->
            updateUI(state)
        }

        viewModel.songs.observe(this) { songs ->
            songAdapter.submitList(songs)
        }

        viewModel.filteredSongs.observe(this) { songs ->
            songAdapter.submitList(songs)
        }
    }

    private fun updateUI(state: MusicPlayerState) {
        try {
            when (state) {
                is MusicPlayerState.Playing -> {
                    playPauseButton.setImageResource(R.drawable.ic_pause)
                    songInfoText.text = state.song?.let { "${it.title} - ${it.artist}" } ?: "Unknown"
                }
                is MusicPlayerState.Paused -> {
                    playPauseButton.setImageResource(R.drawable.ic_play)
                    songInfoText.text = state.song?.let { "${it.title} - ${it.artist}" } ?: "Paused"
                }
                is MusicPlayerState.Stopped -> {
                    playPauseButton.setImageResource(R.drawable.ic_play)
                    songInfoText.text = "No song playing"
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating UI", e)
        }
    }

    private fun checkStoragePermission() {
        try {
            Log.d("MainActivity", "Checking storage permissions")
            if (hasStoragePermission()) {
                Log.d("MainActivity", "Storage permission already granted")
                initializeMusicService()
            } else {
                Log.d("MainActivity", "Need to request storage permission")
                requestStoragePermission()
                // Fallback to sample songs in case we don't get permission
                loadSampleSongs()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking storage permissions", e)
            // Fallback to sample songs on error
            loadSampleSongs()
        }
    }

    private fun setupTextToSpeech() {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                // Set language, pitch, speed etc.
            }
        }
    }

    private fun setupChatInterface() {
        val chatRecyclerView = binding.chatRecyclerView
        chatAdapter = ChatAdapter()
        chatRecyclerView.adapter = chatAdapter
        chatRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Messages start from bottom
        }

        binding.sendButton.setOnClickListener {
            val message = inputText.text.toString().trim()
            if (message.isNotEmpty()) {
                addMessage(message, true)
                processUserInput(message)
                inputText.text.clear()
            }
        }

        addMessage("Hi! I'm your music assistant. You can type commands or speak to control music playback.", false)
    }

    private fun processUserInput(input: String) {
        if (input.isBlank()) return

        addMessage(input, true)

        lifecycleScope.launch {
            try {
                val response = geminiService.processUserInput(input)

                if (response.startsWith("COMMAND:")) {
                    val command = response.substringAfter("COMMAND:").trim()
                    processDirectCommand(command)

                } else {
                    addMessage(response, false)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing user input via Gemini", e)
                addMessage("Sorry, I encountered an error. Please try again.", false)
            }
        }
    }

    private fun processDirectCommand(command: String) {
        try {
            Log.d("MainActivity", "Processing direct command: $command")

            if (songListDialog.isShowing && command.matches(Regex("\\d+"))) {
                try {
                    val position = command.toInt() - 1
                    val currentFilteredSongs = viewModel.filteredSongs.value

                    if (currentFilteredSongs == null) {
                        Log.e("MainActivity", "Filtered songs list is null when selecting by number.")
                        addMessage("Error: Song list not available.", false)
                        return
                    }

                    if (position >= 0 && position < currentFilteredSongs.size) {
                        val selectedSong = currentFilteredSongs[position]
                        playSong(selectedSong)
                        songListDialog.dismiss()
                        addMessage("Playing '${selectedSong.title}'", false)
                    } else {
                        addMessage("Invalid song number. Please choose from 1 to ${currentFilteredSongs.size}.", false)
                    }
                } catch (e: NumberFormatException) {
                    Log.e("MainActivity", "Invalid number format: $command", e)
                    addMessage("Invalid number format.", false)
                }
                return
            }

            var shouldShowDialog = false

            when {
                command.startsWith("play", ignoreCase = true) -> {
                    val songTitle = command.substringAfter("play").trim()
                    if (songTitle.isEmpty()) {
                        musicService?.let {
                            if (!it.isPlaying()) {
                                togglePlayPause()
                                addMessage("Music resumed", false)
                            } else {
                                addMessage("Music is already playing", false)
                            }
                        } ?: addMessage("Music service not ready", false)
                    } else {
                        val songs = songAdapter.currentList
                        val song = songs.find { it.title.equals(songTitle, ignoreCase = true) }
                        if (song != null) {
                            playSong(song)
                            addMessage("Playing: ${song.title}", false)
                        } else {
                            addMessage("Song not found: $songTitle", false)
                            shouldShowDialog = true
                        }
                    }
                }
                command.equals("pause", ignoreCase = true) -> {
                    musicService?.let {
                        if (it.isPlaying()) {
                            togglePlayPause()
                            addMessage("Music paused", false)
                        } else {
                            addMessage("No music is playing", false)
                        }
                    } ?: addMessage("Music service not ready", false)
                }
                command.equals("stop", ignoreCase = true) -> {
                    musicService?.let {
                        stopPlayback()
                        addMessage("Music stopped", false)
                    } ?: addMessage("Music service not ready", false)
                }
                command.equals("next", ignoreCase = true) -> {
                    musicService?.let {
                        it.playNext()
                        addMessage("Playing next song", false)
                    } ?: addMessage("Music service not ready", false)
                }
                command.equals("previous", ignoreCase = true) -> {
                    musicService?.let {
                        it.playPrevious()
                        addMessage("Playing previous song", false)
                    } ?: addMessage("Music service not ready", false)
                }
                command.startsWith("volume", ignoreCase = true) -> {
                    val volumeCommand = command.substringAfter("volume").trim()
                    when {
                        volumeCommand.equals("up", ignoreCase = true) -> {
                            adjustVolume(true)
                            addMessage("Volume increased", false)
                        }
                        volumeCommand.equals("down", ignoreCase = true) -> {
                            adjustVolume(false)
                            addMessage("Volume decreased", false)
                        }
                        else -> addMessage("Invalid volume command. Use 'volume up' or 'volume down'", false)
                    }
                }
                command.equals("show songs", ignoreCase = true) || 
                command.equals("list songs", ignoreCase = true) || 
                command.equals("open songs", ignoreCase = true) -> {
                    Log.d("MainActivity", "Command recognized: show songs")
                    shouldShowDialog = true
                }
                command.equals("search", ignoreCase = true) -> {
                    addMessage("Please enter your search term.", false)
                }
                command.equals("close songs", ignoreCase = true) -> {
                    if (songListDialog.isShowing) {
                        songListDialog.dismiss()
                    } else {
                        addMessage("Song list is not open.", false)
                    }
                }
                else -> {
                    Log.w("MainActivity", "Unknown command: $command")
                    addMessage("Sorry, I didn't understand the command '$command'.", false)
                }
            }

            if (shouldShowDialog) {
                Log.d("MainActivity", "Showing song list dialog.")
                songListDialog.show()
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "General error processing command", e)
            addMessage("Sorry, an error occurred while processing your command.", false)
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val message = ChatMessage(text, isUser)
        chatMessages.add(message)
        chatAdapter.submitList(ArrayList(chatMessages))

        val recyclerView = binding.chatRecyclerView
        recyclerView.post {
            val position = chatMessages.size - 1
            if (position >= 0) {
                recyclerView.smoothScrollToPosition(position)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateProfileButtonImage()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            
            // Clean up music service
            if (isMusicServiceBound) {
                unbindService(this)
                isMusicServiceBound = false
            }
            stopPlayback()
            musicService = null

            // Clean up coroutines
            job.cancel()

            // Clean up text-to-speech
            textToSpeech?.let { t ->
                t.stop()
                t.shutdown()
            }
            textToSpeech = null

            Log.d("MainActivity", "Resources cleaned up successfully")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error cleaning up resources", e)
        }
    }

    private fun initializeMusicService() {
        try {
            Log.d("MainActivity", "Initializing music service")
            
            // Check if service is already bound
            if (isMusicServiceBound && musicService != null) {
                Log.d("MainActivity", "Service already bound, loading songs directly")
                loadSongs()
                return
            }
            
            // First create and start the service with explicit intent
            val serviceIntent = Intent(this, MusicService::class.java)
            serviceIntent.setPackage(packageName) // Ensure it's explicit
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.d("MainActivity", "Starting service as foreground service")
                    startForegroundService(serviceIntent)
                } else {
                    Log.d("MainActivity", "Starting service normally")
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting service", e)
                // Continue with binding anyway
            }
            
            // Then bind to it
            try {
                val bindResult = bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                Log.d("MainActivity", "Bind service result: $bindResult")
                
                if (!bindResult) {
                    Log.e("MainActivity", "Failed to bind to service")
                    // If binding fails, load sample songs as fallback
                    loadSampleSongs()
                }
                // Songs will be loaded after service connection (in serviceConnection callback)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error binding to service", e)
                loadSampleSongs()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing music service", e)
            Toast.makeText(this, "Error connecting to music service: ${e.message}", Toast.LENGTH_SHORT).show()
            // Try to load sample songs as a fallback
            loadSampleSongs()
        }
    }

    private fun loadSongs() {
        if (!hasStoragePermission()) {
            Log.d("MainActivity", "No storage permission, requesting...")
            requestStoragePermission()
            return
        }

        Log.d("MainActivity", "Storage permission granted, loading songs...")
        Toast.makeText(this, R.string.loading_songs, Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Make sure the service is initialized first
                if (!isMusicServiceBound || musicService == null) {
                    Log.d("MainActivity", "Music service not bound yet, initializing...")
                    withContext(Dispatchers.Main) {
                        initializeMusicService()
                    }
                    // Wait a moment for service to bind
                    delay(500)
                }
                
                val songs = mutableListOf<Song>()
                val musicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val externalFilesDir = getExternalFilesDir(null)
                
                if (musicDirectory != null && musicDirectory.exists()) {
                    findMusicFiles(musicDirectory, songs)
                }
                
                if (downloadDirectory != null && downloadDirectory.exists()) {
                    findMusicFiles(downloadDirectory, songs)
                }

                if (externalFilesDir != null && externalFilesDir.exists()) {
                    findMusicFiles(externalFilesDir, songs)
                }

                withContext(Dispatchers.Main) {
                    if (songs.isEmpty()) {
                        Log.w("MainActivity", "No music files found on device.")
                        Toast.makeText(this@MainActivity, "No music files found. Loading sample songs.", Toast.LENGTH_LONG).show()
                        loadSampleSongs()
                } else {
                        Log.d("MainActivity", "Found ${songs.size} songs")
                        Toast.makeText(this@MainActivity, "Found ${songs.size} songs", Toast.LENGTH_SHORT).show()
                        songAdapter.submitList(songs)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading songs", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading songs: ${e.message}", Toast.LENGTH_SHORT).show()
                    loadSampleSongs()
                }
            }
        }
    }

    private fun findMusicFiles(directory: File, songs: MutableList<Song>) {
        val files = directory.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    findMusicFiles(file, songs)
                } else if (file.name.endsWith(".mp3", ignoreCase = true) ||
                    file.name.endsWith(".wav", ignoreCase = true) ||
                    file.name.endsWith(".ogg", ignoreCase = true) ||
                    file.name.endsWith(".m4a", ignoreCase = true)) {
                    songs.add(
                        Song(
                            title = file.nameWithoutExtension ?: "Unknown Title",
                            artist = "Unknown Artist",
                            filePath = file.absolutePath,
                            duration = 0,
                            album = "Unknown Album",
                            genre = "Unknown Genre"
                        )
                    )
                }
            }
        }
    }

    private fun loadSampleSongs() {
        Log.d("MainActivity", "Loading sample songs as fallback")
        
        try {
            val sampleSongs = listOf(
                Song("Sample Song 1", "Artist 1", "path1", 180000, "Sample Album", "Pop"),
                Song("Sample Song 2", "Artist 2", "path2", 240000, "Sample Album", "Rock"),
                Song("Sample Song 3", "Artist 3", "path3", 200000, "Another Album", "Jazz"),
                Song("Sample Song 4", "Artist 4", "path4", 210000, "Another Album", "Classical")
            )
            
            // Update the adapter
            songAdapter.submitList(sampleSongs)
            
            // Update the ViewModel if possible
            try {
                viewModel.updateSongs(sampleSongs)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating ViewModel with sample songs", e)
            }
            
            runOnUiThread {
                Toast.makeText(this, "Using sample songs (music service unavailable)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading sample songs", e)
            runOnUiThread {
                Toast.makeText(this, "Error loading any songs", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playSong(song: Song?) {
        if (song == null) {
            Toast.makeText(this, "Invalid song", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (musicService != null) {
                musicService?.playSong(song)
                updateUI(MusicPlayerState.Playing(song = song, position = 0, duration = 0))
            } else {
                viewModel.playSong(song)
            }
            updateNowPlaying(song)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing song", e)
            Toast.makeText(this, "Error playing song", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupMediaPlayerListeners() {
        // Simplified listener setup without progress updates
        try {
            musicService?.let { service ->
                Log.d("MainActivity", "Setting up basic media player listeners")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up media player listeners", e)
        }
    }

    private fun togglePlayPause() {
        try {
            val service = musicService
            if (service != null) {
                service.togglePlayPause()
                playPauseButton.setImageResource(
                    if (service.isPlaying()) R.drawable.ic_pause else R.drawable.ic_play
                )
            } else {
                viewModel.togglePlayPause()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error toggling playback", e)
            Toast.makeText(this, "Error controlling playback", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        musicService?.let { service ->
            service.stopPlayback()
            playPauseButton.setImageResource(R.drawable.ic_play)
            updateTimeTexts()
        }
    }



    private fun adjustVolume(increase: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val direction = if (increase)
            android.media.AudioManager.ADJUST_RAISE
        else
            android.media.AudioManager.ADJUST_LOWER



        audioManager.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            direction,
            android.media.AudioManager.FLAG_SHOW_UI
        )



        Toast.makeText(this, if (increase) R.string.volume_up else R.string.volume_down, Toast.LENGTH_SHORT).show()
    }

    private fun updateProfileButtonImage() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedImageUri = prefs.getString("profileImage", null)

        if (savedImageUri != null) {
            val profileButton = binding.profileButton

            try {
                com.bumptech.glide.Glide.with(this)
                    .load(android.net.Uri.parse(savedImageUri))
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .into(profileButton)

                Log.d("MainActivity", "Loaded profile picture into profile button")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to load profile picture", e)
            }
        }
    }

    private fun updateNowPlaying(song: Song) {
        songInfoText.text = "${song.title} - ${song.artist}"
    }

    private fun updateTimeTexts() {
        // Time updates removed for stability
    }

    private fun formatTime(milliseconds: Long): String {
        val minutes = (milliseconds / 1000) / 60
        val seconds = (milliseconds / 1000) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}