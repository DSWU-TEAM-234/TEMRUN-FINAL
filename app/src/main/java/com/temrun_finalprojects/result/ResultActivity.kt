package com.temrun_finalprojects.result

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.breathing.BreathingFragment
import com.temrun_finalprojects.cadence.CadenceFragment
import com.temrun_finalprojects.data.Song
import java.lang.Math.sqrt
import kotlin.math.pow

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_result)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.result)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 인텐트 데이터 수신
        val receivedSongs = intent.getParcelableArrayListExtra<Song>("songs") ?: arrayListOf()
        val time = intent.getIntExtra("time", 0)
        val calorie = intent.getDoubleExtra("calorie", 0.0)
        val averageBPM = intent.getIntExtra("averageBPM", 0)
        val cadenceHistory = intent.getIntegerArrayListExtra("cadenceHistory") ?: arrayListOf()

        // 음악 목록 표시 (임시 비활성화)
        val songContainer = findViewById<LinearLayout>(R.id.songLinearLayout)
        for (song in receivedSongs) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_song_card, songContainer, false)

            val title = itemView.findViewById<TextView>(R.id.songTitle)
            val artist = itemView.findViewById<TextView>(R.id.songArtist)
            val image = itemView.findViewById<ImageView>(R.id.albumImageView)
            val thumbUp = itemView.findViewById<AppCompatImageView>(R.id.thumbUpImageView)
            val thumbDown = itemView.findViewById<AppCompatImageView>(R.id.thumbDownImageView)

            title.text = song.title
            artist.text = song.artist
            Glide.with(itemView.context)
                .load(R.drawable.ic_box)
                .into(image)

            songContainer.addView(itemView)

            thumbUp.setOnClickListener {
                it.isSelected = !it.isSelected
                if (it.isSelected) thumbDown.isSelected = false
            }

            thumbDown.setOnClickListener {
                it.isSelected = !it.isSelected
                if (it.isSelected) thumbUp.isSelected = false
            }
        }


        // 통계 데이터 표시
        val hours = time / 3600
        val minutes = (time % 3600) / 60
        val seconds = time % 60
        val timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        findViewById<TextView>(R.id.ResultTimeText).text = timeFormatted
        findViewById<TextView>(R.id.ResultBPMText).text = averageBPM.toString()
        findViewById<TextView>(R.id.ResultCalorieText).text = "%.1f kcal".format(calorie)

        // 프래그먼트 설정
        supportFragmentManager.beginTransaction()
            .replace(R.id.breathing_fragment_container, BreathingFragment())
            .commit()

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.cadence_fragment_container,
                CadenceFragment.newInstance(ArrayList(cadenceHistory))
            )
            .commit()

        // 확인 버튼 리스너
        findViewById<Button>(R.id.resultConfirmButton).setOnClickListener {
            val intent = Intent(this, RootActivity::class.java).apply {
                putExtra("targetFragment", "home")
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    // 표준편차 계산 함수 (CadenceFragment와 중복 제거 시 사용)
    private fun calculateStandardDeviation(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return sqrt(values.map { (it - mean).pow(2) }.average())
    }
}
