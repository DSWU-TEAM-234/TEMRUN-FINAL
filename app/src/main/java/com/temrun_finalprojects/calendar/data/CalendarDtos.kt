package com.temrun_finalprojects.calendar.data

data class CalendarSummary(
    val totalDuration: Int,
    val averageBpm: Int,
    val totalCalories: Int,
    val runningDates: List<String>
)
