package at.plankt0n.streamplay.helper

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object StreamRecordHelper {
    private const val TAG = "StreamRecordHelper"
    private const val FOLDER_NAME = "streamplay"

    @Volatile
    private var recordingJob: Job? = null
    @Volatile
    private var outputStream: OutputStream? = null
    @Volatile
    private var currentUri: Uri? = null
    @Volatile
    private var currentFile: File? = null
    @Volatile
    private var currentFileName: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for streaming
        .build()

    fun startRecording(context: Context, streamUrl: String, stationName: String): Boolean {
        if (isRecording()) {
            Log.w(TAG, "Already recording")
            return false
        }

        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val sanitizedStationName = stationName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "${timestamp}_$sanitizedStationName"
            currentFileName = fileName

            // Determine file extension from stream content-type
            val extension = getStreamExtension(streamUrl) ?: "mp3"
            val fullFileName = "$fileName.$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fullFileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, getMimeType(extension))
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                currentUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (currentUri == null) {
                    Log.e(TAG, "Failed to create MediaStore entry")
                    return false
                }

                outputStream = resolver.openOutputStream(currentUri!!)
            } else {
                // Use direct file access for older Android versions
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val streamplayDir = File(downloadDir, FOLDER_NAME)
                if (!streamplayDir.exists()) {
                    streamplayDir.mkdirs()
                }

                currentFile = File(streamplayDir, fullFileName)
                outputStream = FileOutputStream(currentFile!!)
            }

            if (outputStream == null) {
                Log.e(TAG, "Failed to create output stream")
                return false
            }

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val request = Request.Builder()
                        .url(streamUrl)
                        .header("Icy-MetaData", "0") // Don't request metadata, we want clean audio
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.e(TAG, "Stream request failed: ${response.code}")
                            return@launch
                        }

                        val inputStream = response.body?.byteStream() ?: return@launch
                        val buffer = ByteArray(8192)

                        Log.d(TAG, "Recording started: $fullFileName")

                        while (isActive) {
                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break
                            outputStream?.write(buffer, 0, bytesRead)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Recording error: ${e.message}")
                    }
                } finally {
                    finalizeRecording(context)
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            cleanup()
            return false
        }
    }

    fun stopRecording(context: Context): String? {
        if (!isRecording()) {
            return null
        }

        // Dateiname speichern bevor Job cancelled wird (cleanup() setzt ihn auf null)
        val savedFileName = currentFileName

        recordingJob?.cancel()
        recordingJob = null

        // Stream sofort schließen falls noch offen
        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing stream: ${e.message}")
        }
        outputStream = null

        return savedFileName
    }

    private fun finalizeRecording(context: Context) {
        try {
            // Stream schließen falls noch offen (könnte schon von stopRecording geschlossen sein)
            val stream = outputStream
            if (stream != null) {
                try {
                    stream.flush()
                    stream.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing stream: ${e.message}")
                }
                outputStream = null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && currentUri != null) {
                // Mark as not pending anymore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(currentUri!!, contentValues, null, null)
            }

            Log.d(TAG, "Recording saved: $currentFileName")
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing recording: ${e.message}")
        } finally {
            cleanup()
        }
    }

    private fun cleanup() {
        currentUri = null
        currentFile = null
        currentFileName = null
    }

    fun isRecording(): Boolean = recordingJob?.isActive == true

    private fun getStreamExtension(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            client.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type")?.lowercase() ?: return@use null

                when {
                    contentType.contains("audio/mpeg") || contentType.contains("audio/mp3") -> "mp3"
                    contentType.contains("audio/aac") || contentType.contains("audio/aacp") -> "aac"
                    contentType.contains("audio/ogg") -> "ogg"
                    contentType.contains("audio/flac") -> "flac"
                    contentType.contains("audio/x-wav") || contentType.contains("audio/wav") -> "wav"
                    else -> "mp3" // Default to mp3
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine stream type: ${e.message}")
            null
        }
    }

    private fun getMimeType(extension: String): String {
        return when (extension) {
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
    }
}
