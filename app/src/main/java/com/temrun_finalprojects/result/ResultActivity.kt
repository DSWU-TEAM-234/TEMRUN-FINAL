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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.temrun_finalprojects.R
import com.temrun_finalprojects.RootActivity
import com.temrun_finalprojects.data.Song

class ResultActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_result)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.result)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val songContainer = findViewById<LinearLayout>(R.id.songLinearLayout) // 내부 LinearLayout ID

//        val songList = listOf(
//            Song("노래1", "가수1"),
//            Song("노래2", "가수2"),
//            Song("노래3", "가수3")
//        )

        val receivedSongs = intent.getParcelableArrayListExtra<Song>("songs") ?: arrayListOf()


        for (song in receivedSongs)
        {
            val itemView =
                LayoutInflater.from(this).inflate(R.layout.item_song_card, songContainer, false)

            val title = itemView.findViewById<TextView>(R.id.songTitle)
            val artist = itemView.findViewById<TextView>(R.id.songArtist)
            val image = itemView.findViewById<ImageView>(R.id.albumImageView)

            title.text = song.title
            artist.text = song.artist
            Glide.with(itemView.context)
                .load(song.albumImageUrl)
                .into(image)

            songContainer.addView(itemView)
        }

        val time = intent.getIntExtra("time", 0)
        val calorie = intent.getDoubleExtra("calorie", 0.0)
        val distance = intent.getFloatExtra("distance", 0f)
        val averageBPM = intent.getIntExtra("averageBPM", 0)

        val hours = time / 3600
        val minutes = (time % 3600) / 60
        val seconds = time % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

        findViewById<TextView>(R.id.ResultTimeText).text = timeFormatted
        findViewById<TextView>(R.id.ResultBPMText).text =  averageBPM.toString()
        findViewById<TextView>(R.id.ResultCalorieText).text = calorie.toString()

        val resultConfirmButton : Button = findViewById(R.id.resultConfirmButton)

        resultConfirmButton.setOnClickListener {
            val intent = Intent(this, RootActivity::class.java)
            intent.putExtra("targetFragment", "home")
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

}