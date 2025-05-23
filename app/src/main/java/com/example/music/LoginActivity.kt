package com.example.music

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

import com.example.music.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    if (account != null && account.idToken != null) {
                        Log.d("LoginActivity", "Google sign in succeeded")
                        firebaseAuthWithGoogle(account.idToken!!)
                    } else {
                        binding.progressBar.visibility = View.GONE
                        Log.w("LoginActivity", "Google sign in failed: account or token is null")
                        Toast.makeText(this, "Google sign in failed: Could not get account information", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: ApiException) {
                    binding.progressBar.visibility = View.GONE
                    Log.w("LoginActivity", "Google sign in failed code: " + e.statusCode, e)
                    when (e.statusCode) {
                        7 -> Toast.makeText(this, "Network error. Please check your connection.", Toast.LENGTH_LONG).show()
                        12501 -> Toast.makeText(this, "Sign in canceled by user.", Toast.LENGTH_LONG).show()
                        else -> Toast.makeText(this, "Google sign in failed: ${e.message} (code: ${e.statusCode})", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Log.e("LoginActivity", "Google sign in failed with unexpected error", e)
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            binding.progressBar.visibility = View.GONE
            Log.w("LoginActivity", "Google sign in failed: result not OK")
            Toast.makeText(this, "Google sign in was canceled", Toast.LENGTH_SHORT).show()
        }
        binding.googleSignInButton.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        try {
            // Configure Google Sign-In
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)
            
            // Log to help debug
            Log.d("LoginActivity", "Google Sign-In configured successfully")
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error setting up Google Sign-In", e)
            Toast.makeText(this, "Error setting up Google Sign-In: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        // Check if user is already logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d("LoginActivity", "User already logged in: ${currentUser.email}")
            startMainActivity()
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Email and password required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading indicator
            binding.progressBar.visibility = View.VISIBLE
            binding.loginButton.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // Hide loading indicator
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true

                    if (task.isSuccessful) {
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        startMainActivity()
                    } else {
                        // Check if user exists
                        if (task.exception?.message?.contains("no user record") == true || 
                            task.exception?.message?.contains("password is invalid") == true) {
                            // User doesn't exist or password is wrong
                            Toast.makeText(this, "Invalid email or password. Please try again or register.", Toast.LENGTH_LONG).show()
                        } else {
                            Log.e("LoginActivity", "Login failed", task.exception)
                            Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }

        binding.googleSignInButton.setOnClickListener {
            startGoogleSignIn()
        }

        binding.registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun startGoogleSignIn() {
        try {
            // Check if Google Play Services is available
            val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
            
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                if (googleApiAvailability.isUserResolvableError(resultCode)) {
                    googleApiAvailability.getErrorDialog(this, resultCode, 9000)?.show()
                } else {
                    Toast.makeText(this, "This device does not support Google Play Services", Toast.LENGTH_LONG).show()
                }
                return
            }
            
            // Show loading indicator
            binding.progressBar.visibility = View.VISIBLE
            binding.googleSignInButton.isEnabled = false
            
            // Log the attempt
            Log.d("LoginActivity", "Starting Google Sign-In flow")
            
            // Launch the Google Sign-In intent
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            // Reset UI state
            binding.progressBar.visibility = View.GONE
            binding.googleSignInButton.isEnabled = true
            
            // Log and show error
            Log.e("LoginActivity", "Google Sign-In failed", e)
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }



    private fun firebaseAuthWithGoogle(idToken: String) {
        binding.progressBar.visibility = View.VISIBLE
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                binding.progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    // Sign in success
                    Log.d("LoginActivity", "signInWithCredential:success")
                    Toast.makeText(this, "Google Sign-In successful", Toast.LENGTH_SHORT).show()
                    startMainActivity()
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("LoginActivity", "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun startMainActivity() {
        try {
            Log.d("LoginActivity", "Starting MainActivity")
            
            // Use a direct intent to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error starting MainActivity", e)
            Toast.makeText(this, "Error starting app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        auth.currentUser?.let {
            startMainActivity()
        }
    }
}