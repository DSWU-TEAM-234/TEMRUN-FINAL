package com.temrun_finalprojects

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.temrun_finalprojects.login.LoginActivity
import com.temrun_finalprojects.login.RegisterActivity

class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        val btnSpotify = findViewById<Button>(R.id.btnStartSpotify)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        btnSpotify.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        tvLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}
