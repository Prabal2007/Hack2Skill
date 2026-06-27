package com.example.hackofiesta

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import androidx.core.widget.addTextChangedListener

class Login_Page : AppCompatActivity() {
    private var isVerified = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login_page)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnVerify = findViewById<MaterialButton>(R.id.btnVerify)
        val btnContinue = findViewById<MaterialButton>(R.id.btnContinue)

        fun resetVerification() {
            isVerified = false
            tvStatus.text = "Not Verified"
            tvStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
        }

        etUsername.addTextChangedListener{ resetVerification() }
        etPassword.addTextChangedListener{ resetVerification() }

        btnVerify.setOnClickListener {

            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty()){
                etUsername.error = "Please enter Username"
                return@setOnClickListener
            }

            if (password.isEmpty()){
                etPassword.error = "Please enter Password"
                return@setOnClickListener
            }

            if (username != "admin"){
                etUsername.error = "Wrong Username"
                return@setOnClickListener
            }

            if (password != "password"){
                etPassword.error = "Wrong Password"
                return@setOnClickListener
            }

            isVerified=true
            tvStatus.text = "Verified"
            tvStatus.setTextColor(resources.getColor((android.R.color.holo_green_dark)))
//            tvStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_check_circle_24,0,0,0)
        }

        btnContinue.setOnClickListener {

            if (!isVerified){
                tvStatus.text = "Please verify first"
                tvStatus.setTextColor(resources.getColor((android.R.color.holo_red_dark)))
                return@setOnClickListener
            }

            val intent = Intent(this, FrontPage::class.java)
            startActivity(intent)
        }


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}