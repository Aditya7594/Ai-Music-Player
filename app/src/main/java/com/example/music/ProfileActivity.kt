package com.example.music

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import de.hdodenhof.circleimageview.CircleImageView

class ProfileActivity : AppCompatActivity() {
    private lateinit var profileImage: CircleImageView
    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var favoriteGenreInput: EditText
    private lateinit var favoriteArtistInput: EditText
    private lateinit var saveButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var tapToChangeText: TextView
    private var selectedImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                loadProfileImage(uri)
                Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize views
        profileImage = findViewById(R.id.profile_image)
        nameInput = findViewById(R.id.name_input)
        emailInput = findViewById(R.id.email_input)
        favoriteGenreInput = findViewById(R.id.favorite_genre)
        favoriteArtistInput = findViewById(R.id.favorite_artist)
        saveButton = findViewById(R.id.save_button)
        backButton = findViewById(R.id.back_button)
        tapToChangeText = findViewById(R.id.tap_to_change)

        // Load saved data
        val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
        
        // Load basic info
        nameInput.setText(prefs.getString("userName", ""))
        
        // Try to get email from Firebase first
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null && firebaseUser.email != null) {
            emailInput.setText(firebaseUser.email)
            // Make email field read-only if from Firebase
            emailInput.isEnabled = false
            emailInput.setHint("Logged in with: ${firebaseUser.email}")
        } else {
            // Fallback to saved preferences
            emailInput.setText(prefs.getString("userEmail", ""))
        }
        
        // Load music preferences
        favoriteGenreInput.setText(prefs.getString("favoriteGenre", ""))
        favoriteArtistInput.setText(prefs.getString("favoriteArtist", ""))
        
        // Load profile image
        val savedImageUri = prefs.getString("profileImage", null)
        if (savedImageUri != null) {
            try {
                loadProfileImage(Uri.parse(savedImageUri))
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error loading profile image", e)
                profileImage.setImageResource(R.drawable.default_profile)
            }
        } else {
            profileImage.setImageResource(R.drawable.default_profile)
        }

        // Setup click listeners
        val profileClickListener = View.OnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }
        
        // Set click listener on both image and text
        profileImage.setOnClickListener(profileClickListener)
        tapToChangeText.setOnClickListener(profileClickListener)

        saveButton.setOnClickListener {
            // Validate inputs
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                nameInput.error = "Name cannot be empty"
                return@setOnClickListener
            }
            
            // Get other field values
            val email = emailInput.text.toString().trim()
            val favoriteGenre = favoriteGenreInput.text.toString().trim()
            val favoriteArtist = favoriteArtistInput.text.toString().trim()

            // Save all data
            prefs.edit().apply {
                // Save personal info
                putString("userName", name)
                if (emailInput.isEnabled) { // Only save email if not from Firebase
                    putString("userEmail", email)
                }
                
                // Save music preferences
                putString("favoriteGenre", favoriteGenre)
                putString("favoriteArtist", favoriteArtist)
                
                // Save profile image
                selectedImageUri?.let { uri ->
                    putString("profileImage", uri.toString())
                }
                
                putBoolean("isLoggedIn", true)
                apply()
            }
            
            Log.d("ProfileActivity", "Profile saved: name=$name, favoriteGenre=$favoriteGenre, favoriteArtist=$favoriteArtist")

            Toast.makeText(this, "Profile saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        }

        backButton.setOnClickListener {
            // Check if unsaved changes
            val prefs = getSharedPreferences("UserData", MODE_PRIVATE)
            val savedName = prefs.getString("userName", "")
            val currentName = nameInput.text.toString().trim()
            
            // If there are unsaved changes, ask user to confirm
            if (savedName != currentName || selectedImageUri != null) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Unsaved Changes")
                    .setMessage("You have unsaved changes. Are you sure you want to discard them?")
                    .setPositiveButton("Yes") { _, _ -> finish() }
                    .setNegativeButton("No", null)
                    .show()
            } else {
                finish()
            }
        }
    }

    private fun loadProfileImage(uri: Uri) {
        try {
            Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(profileImage)
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error loading image: ${uri}", e)
            Toast.makeText(this, "Error loading profile image", Toast.LENGTH_SHORT).show()
            profileImage.setImageResource(R.drawable.default_profile)
        }
    }
} 