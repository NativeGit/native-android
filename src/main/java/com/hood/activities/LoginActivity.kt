package com.hood.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hood.R
import okhttp3.*
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailEditText: EditText = findViewById(R.id.emailEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)
        val loginButton: Button = findViewById(R.id.loginButton)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            } else {
                signup(email, password) { result ->
                    runOnUiThread {
                        result.onSuccess { message ->
                            Toast.makeText(this, "Login successful: $message", Toast.LENGTH_SHORT).show()
                            // Navigate back to MainActivity
                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            finish() // Close LoginActivity
                        }.onFailure { error ->
                            Toast.makeText(this, "Login failed: ${error.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun signup(email: String, password: String, completion: (Result<String>) -> Unit) {
        // Validate and prepare email
        var validatedEmail = email
        if (!validatedEmail.contains("@")) {
            validatedEmail += "@apple.com"
            // Clear the email after modification
            getSharedPreferences("my_preferences", Context.MODE_PRIVATE).edit().putString("email", "").apply()
        }

        // Build URL with query parameters
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("minitel.co.uk")
            .addPathSegment("app")
            .addPathSegment("models")
            .addPathSegment("shopgateway")
            .addQueryParameter("command", "checkMinitelLogin")
            .addQueryParameter("email", validatedEmail)
            .addQueryParameter("password", password)
            .build()

        val request = Request.Builder().url(urlBuilder).build()

        // HTTP request
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                completion(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    completion(Result.failure(IOException("Unexpected code $response")))
                    return
                }

                val responseString = response.body?.string() ?: run {
                    completion(Result.failure(IOException("Invalid data received")))
                    return
                }

                // Process the server response
                val resultsArray = responseString.split("|")
                if (resultsArray.size > 3) {
                    val sharedPreferences = getSharedPreferences("my_preferences", Context.MODE_PRIVATE)
                    sharedPreferences.edit().apply {
                        putString("cid", resultsArray[1])
                        putString("name", resultsArray[2])
                        putString("admin", resultsArray[3])
                        putString("selectedAdminID", resultsArray[3])
                        apply()
                    }
                    Log.d("LoginActivity", "Saved cid: ${resultsArray[1]}") // Debug information
                    val resultsText = resultsArray.joinToString("\n")
                    completion(Result.success(resultsText))
                } else {
                    completion(Result.failure(IOException("Unexpected server response format")))
                }
            }
        })
    }
}
