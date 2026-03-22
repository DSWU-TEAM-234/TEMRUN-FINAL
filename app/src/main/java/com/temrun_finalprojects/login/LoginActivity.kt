package com.temrun_finalprojects.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val editId = findViewById<EditText>(R.id.editId)
        val editPw = findViewById<EditText>(R.id.editPassword)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val loginBtn = findViewById<Button>(R.id.btnLogin)

        // 뒤로가기
        btnBack.setOnClickListener {
            finish()
        }


        loginBtn.setOnClickListener {
            val intent = Intent(this, RootActivity::class.java)
            startActivity(intent)
        }

    }
}
