package com.temrun_finalprojects.song_feedback

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.temrun_finalprojects.R
import com.temrun_finalprojects.data.SongFeedback

class SongAdapter(
    private val items: MutableList<SongFeedback>
) : RecyclerView.Adapter<SongViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_song_card, parent, false)
        return SongViewHolder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(items[position]) { pos, pref ->
            items[pos].preference = pref
            notifyItemChanged(pos)
        }
    }

    fun currentItems(): List<SongFeedback> = items
}