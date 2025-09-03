package com.temrun_finalprojects.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.temrun_finalprojects.R
import java.util.concurrent.TimeUnit

class SessionAdapter :
    ListAdapter<RunSession, SessionAdapter.SessionViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        private val textStartTime: TextView = itemView.findViewById(R.id.textStartTime)
        private val textDuration: TextView = itemView.findViewById(R.id.textDuration)
        private val textAvgBpm: TextView = itemView.findViewById(R.id.textAvgBpm)
        private val textCalories: TextView = itemView.findViewById(R.id.textCalories)

        fun bind(session: RunSession) {
            // 제목: runId 마지막 4자리
            textTitle.text = "러닝 ${session.runId.takeLast(4)}"
            textStartTime.text = session.startTime
            textAvgBpm.text = session.averageBpm.toString()
            textCalories.text = session.totalCalories.toString()

            // totalDuration (초 → mm:ss)
            val minutes = TimeUnit.SECONDS.toMinutes(session.totalDuration)
            val seconds = session.totalDuration % 60
            textDuration.text = String.format("%d:%02d", minutes, seconds)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RunSession>() {
        override fun areItemsTheSame(oldItem: RunSession, newItem: RunSession): Boolean {
            return oldItem.runId == newItem.runId
        }

        override fun areContentsTheSame(oldItem: RunSession, newItem: RunSession): Boolean {
            return oldItem == newItem
        }
    }
}
