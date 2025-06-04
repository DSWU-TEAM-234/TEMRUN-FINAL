package com.temrun_finalprojects

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.temrun_finalprojects.calendar.CalendarFragment
import com.temrun_finalprojects.game.GameSelectFragment

class RootActivity : AppCompatActivity() {
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 기본 Fragment 설정
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HomeFragment())
            .commit()

        bottomNav = findViewById(R.id.bottomNavigationView)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_game -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, GameSelectFragment())
                        .commit()
                    true
                }
                R.id.nav_running -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, HomeFragment()) // 예시
                        .commit()
                    true
                }
                R.id.nav_record -> { //  "기록" 탭 누르면 CalendarFragment 보여줌
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, CalendarFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }


        val target = intent.getStringExtra("targetFragment")
        if (target == "home") {
            showHomeFragment()
        }

    }
    private fun showHomeFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HomeFragment())
            .commit()
    }

}