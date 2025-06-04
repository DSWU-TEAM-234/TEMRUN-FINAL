package com.temrun_finalprojects.util

import android.content.Context
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


object FileUtil {
    @Throws(IOException::class)
    fun loadModelFile(context: Context, modelFilename: String?): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFilename!!)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel

        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength

        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}