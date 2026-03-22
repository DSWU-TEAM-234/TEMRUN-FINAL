package com.temrun_finalprojects.data


enum class Preference { NONE, LIKE, DISLIKE }

data class SongFeedback(
    val id: String,        // 트랙 URI 등 고유값
    val title: String,
    val artist: String,
    var preference: Preference = Preference.NONE
)
