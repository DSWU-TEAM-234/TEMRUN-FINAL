package com.temrun_finalprojects.data
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val title: String,
    val artist: String,
    val albumImageUrl: String? = null       //String
): Parcelable

