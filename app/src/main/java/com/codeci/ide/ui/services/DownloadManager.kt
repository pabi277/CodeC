package com.codeci.ide.ui.services

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

sealed class DownloadState {
    data class Downloading(val progress: Int, val bytesDownloaded: Long) : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Failed(val error: String) : DownloadState()
    data class Paused(val bytesDownloaded: Long) : DownloadState()
}

class DownloadManager(private val context: Context) {

    companion object {
        private const val FLAG_RUN = 0
        private const val FLAG_PAUSE = 1
        private const val FLAG_CANCEL = 2
    }

    // Maps moduleId to their current control flag (RUN, PAUSE, CANCEL)
    private val controlFlags = ConcurrentHashMap<String, Int>()

    /**
     * Resolves the temp directory, falling back to app-specific storage if needed.
     */
    private fun getTempDir(): File {
        var dir = File(Environment.getExternalStorageDirectory(), "CodeC/modules/temp")
        try {
            if (!dir.exists()) dir.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback for Android 10+ if public directory is not writable
        if (!dir.exists() || !dir.canWrite()) {
            dir = File(context.getExternalFilesDir(null), "CodeC/modules/temp")
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
        return dir
    }

    /**
     * Downloads a module from the given URL.
     * Supports pausing, resuming (via HTTP Range), and cancellation.
     */
    fun downloadModule(url: String, moduleId: String): Flow<DownloadState> = flow {
        controlFlags[moduleId] = FLAG_RUN
        val dir = getTempDir()
        val file = File(dir, "$moduleId.tmp")
        
        var downloadedBytes = if (file.exists()) file.length() else 0L

        try {
            while (true) {
                val flag = controlFlags[moduleId]
                
                if (flag == FLAG_CANCEL) {
                    if (file.exists()) file.delete()
                    controlFlags.remove(moduleId)
                    emit(DownloadState.Failed("Download cancelled"))
                    break
                }
                
                if (flag == FLAG_PAUSE) {
                    emit(DownloadState.Paused(downloadedBytes))
                    while (controlFlags[moduleId] == FLAG_PAUSE) {
                        delay(500) // Poll while paused
                    }
                    continue // Re-check flags (could have changed to RUN or CANCEL)
                }
                
                // Active RUN state
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "CodeC-IDE")
                if (downloadedBytes > 0) {
                    connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
                }
                connection.connect()
                
                val responseCode = connection.responseCode
                
                // Handle already fully downloaded edge-case for range headers
                if (responseCode == 416) { 
                    emit(DownloadState.Completed(file.absolutePath))
                    controlFlags.remove(moduleId)
                    break
                }
                
                if (responseCode !in 200..299) {
                    emit(DownloadState.Failed("HTTP Error: $responseCode"))
                    controlFlags.remove(moduleId)
                    break
                }
                
                val totalBytes = downloadedBytes + connection.contentLength
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(file, downloadedBytes > 0)
                
                var interrupted = false
                
                try {
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
                        emit(DownloadState.Downloading(progress, downloadedBytes))
                        
                        val currentFlag = controlFlags[moduleId]
                        if (currentFlag == FLAG_PAUSE || currentFlag == FLAG_CANCEL) {
                            interrupted = true
                            break
                        }
                    }
                } finally {
                    outputStream.close()
                    inputStream.close()
                    connection.disconnect()
                }
                
                if (!interrupted) {
                    emit(DownloadState.Completed(file.absolutePath))
                    controlFlags.remove(moduleId)
                    break
                }
            }
        } catch (e: Exception) {
            emit(DownloadState.Failed(e.message ?: "Unknown error occurred"))
            controlFlags.remove(moduleId)
        }
    }.flowOn(Dispatchers.IO)

    fun pauseDownload(moduleId: String) {
        if (controlFlags.containsKey(moduleId)) {
            controlFlags[moduleId] = FLAG_PAUSE
        }
    }

    fun resumeDownload(moduleId: String) {
        if (controlFlags.containsKey(moduleId)) {
            controlFlags[moduleId] = FLAG_RUN
        }
    }

    fun cancelDownload(moduleId: String) {
        if (controlFlags.containsKey(moduleId)) {
            controlFlags[moduleId] = FLAG_CANCEL
        } else {
            // Clean up file if flow is no longer active
            val dir = getTempDir()
            val file = File(dir, "$moduleId.tmp")
            if (file.exists()) file.delete()
        }
    }

    fun verifyChecksum(filePath: String, expectedSha256: String): Boolean {
        return try {
            val file = File(filePath)
            if (!file.exists()) return false
            
            val digest = MessageDigest.getInstance("SHA-256")
            val inputStream = FileInputStream(file)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()
            
            val hashBytes = digest.digest()
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            hexString.equals(expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
