package com.temrun_finalprojects.game

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.temrun_finalprojects.MainActivity
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity

class GameResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_game_result)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.gameResult)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val score = intent.getIntExtra("score", 0)
        val perfect = intent.getIntExtra("perfect", 0)
        val good = intent.getIntExtra("good", 0)
        val miss = intent.getIntExtra("miss", 0)
        val maxCombo = intent.getIntExtra("maxCombo", 0)

        findViewById<TextView>(R.id.finalScore).text = score.toString()
        findViewById<TextView>(R.id.perfectCount).text = perfect.toString()
        findViewById<TextView>(R.id.goodCount).text = good.toString()
        findViewById<TextView>(R.id.missCount).text = miss.toString()
        findViewById<TextView>(R.id.maxCombo).text = maxCombo.toString()

        val confirmButton = findViewById<Button>(R.id.resultConfirmButton)
        confirmButton.setOnClickListener {
            val intent = Intent(this, RootActivity::class.java)
            intent.putExtra("targetFragment", "home")
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }

    }
}