package com.temrun_finalprojects.song_feedback

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.temrun_finalprojects.R
import com.temrun_finalprojects.data.Preference
import com.temrun_finalprojects.data.SongFeedback

class SongViewHolder(private val v: View) : RecyclerView.ViewHolder(v) {
    private val title = v.findViewById<TextView>(R.id.songTitle)
    private val artist = v.findViewById<TextView>(R.id.songArtist)
    private val btnLike = v.findViewById<ImageView>(R.id.btnLike)
    private val btnDislike = v.findViewById<ImageView>(R.id.btnDislike)

    fun bind(item: SongFeedback, onChanged: (position: Int, pref: Preference) -> Unit) {
        title.text = item.title
        artist.text = item.artist
        applyIcons(item.preference)

        btnLike.setOnClickListener {
            val next = if (item.preference == Preference.LIKE) Preference.NONE else Preference.LIKE
            val pos = adapterPosition
            if (pos != RecyclerView.NO_POSITION) onChanged(pos, next)
        }
        btnDislike.setOnClickListener {
            val next = if (item.preference == Preference.DISLIKE) Preference.NONE else Preference.DISLIKE
            val pos = adapterPosition
            if (pos != RecyclerView.NO_POSITION) onChanged(pos, next)
        }
    }

    private fun applyIcons(pref: Preference) {
        val likeIcon = if (pref == Preference.LIKE) R.drawable.thumb_up_green else R.drawable.thumb_up_line
        val dislikeIcon = if (pref == Preference.DISLIKE) R.drawable.thumb_down_green else R.drawable.thumb_down_line
        btnLike.setImageResource(likeIcon)
        btnDislike.setImageResource(dislikeIcon)
    }
}
