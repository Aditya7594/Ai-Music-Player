package com.example.music

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.music.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.registerButton.setOnClickListener {
            val username = binding.usernameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString()
            val confirmPassword = binding.confirmPasswordInput.text.toString()

            when {
                username.isBlank() -> {
                    Toast.makeText(this, "Username is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                email.isBlank() -> {
                    Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                password.isBlank() -> {
                    Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                password != confirmPassword -> {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                password.length < 6 -> {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // Show progress bar
            binding.progressBar.visibility = View.VISIBLE
            binding.registerButton.isEnabled = false

            registerUser(username, email, password)
        }

        binding.loginLink.setOnClickListener {
            finish() // Return to LoginActivity
        }

        binding.showPasswordButton.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(binding.passwordInput, isPasswordVisible)
            binding.showPasswordButton.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye_closed
                else R.drawable.ic_eye_open
            )
        }

        binding.showConfirmPasswordButton.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            togglePasswordVisibility(binding.confirmPasswordInput, isConfirmPasswordVisible)
            binding.showConfirmPasswordButton.setImageResource(
                if (isConfirmPasswordVisible) R.drawable.ic_eye_closed
                else R.drawable.ic_eye_open
            )
        }
    }

    private fun registerUser(username: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                // Hide progress bar
                binding.progressBar.visibility = View.GONE
                binding.registerButton.isEnabled = true
                
                if (task.isSuccessful) {
                    Log.d("RegisterActivity", "createUserWithEmail:success")
                    
                    // Update profile with username
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()

                    auth.currentUser?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { profileTask ->
                            if (profileTask.isSuccessful) {
                                Toast.makeText(this, "Registration successful! Please log in.", Toast.LENGTH_LONG).show()
                                // Return to login screen
                                finish()
                            } else {
                                Log.w("RegisterActivity", "updateProfile:failure", profileTask.exception)
                                Toast.makeText(this, "Profile update failed: ${profileTask.exception?.message}", 
                                    Toast.LENGTH_SHORT).show()
                                // Still return to login screen
                                finish()
                            }
                        }
                } else {
                    Log.w("RegisterActivity", "createUserWithEmail:failure", task.exception)
                    
                    // Check for specific error messages
                    when {
                        task.exception?.message?.contains("email address is already") == true -> {
                            Toast.makeText(this, "This email is already registered. Please log in instead.", 
                                Toast.LENGTH_LONG).show()
                        }
                        task.exception?.message?.contains("network error") == true -> {
                            Toast.makeText(this, "Network error. Please check your connection and try again.", 
                                Toast.LENGTH_LONG).show()
                        }
                        else -> {
                            Toast.makeText(this, "Registration failed: ${task.exception?.message}", 
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
    }

    private fun togglePasswordVisibility(editText: android.widget.EditText, visible: Boolean) {
        editText.transformationMethod = if (visible) {
            null // Show password
        } else {
            android.text.method.PasswordTransformationMethod.getInstance() // Hide password
        }
        editText.setSelection(editText.text.length) // Keep cursor at the end
    }
}