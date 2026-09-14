package com.jarves.mh.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class MessageBackupEntry(
    val timestamp: Long,
    val text: String,
)

object MessageBackupManager {
    const val BACKUP_FILE_NAME = "message_backups.json"
    private const val MAX_BACKUP_ENTRIES = 500
    private val mutex = kotlinx.coroutines.sync.Mutex()

    fun getBackupFile(context: Context): File = File(context.filesDir, BACKUP_FILE_NAME)

    suspend fun backupMessageToFile(backupFile: File, text: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                backupFile.parentFile?.mkdirs()
                val rootArray = if (backupFile.exists()) {
                    val content = backupFile.readText()
                    try {
                        if (content.isNotBlank()) JSONArray(content) else JSONArray()
                    } catch (_: Exception) {
                        // Recover gracefully from corrupted JSON by starting fresh
                        JSONArray()
                    }
                } else {
                    JSONArray()
                }

                val entry = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("text", text)
                }
                rootArray.put(entry)

                // Limit retained entries to prevent unbounded disk/memory growth
                val trimmedArray = if (rootArray.length() > MAX_BACKUP_ENTRIES) {
                    val trimmed = JSONArray()
                    val startIndex = rootArray.length() - MAX_BACKUP_ENTRIES
                    for (i in startIndex until rootArray.length()) {
                        trimmed.put(rootArray.get(i))
                    }
                    trimmed
                } else {
                    rootArray
                }

                // Write atomically via temporary file to prevent corruption on crash
                val tempFile = File(backupFile.parentFile ?: File("."), "${backupFile.name}.tmp")
                tempFile.writeText(trimmedArray.toString(2))
                if (!tempFile.renameTo(backupFile)) {
                    backupFile.writeText(trimmedArray.toString(2))
                    tempFile.delete()
                }
                true
            } catch (e: IOException) {
                e.printStackTrace()
                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun backupMessageBeforeSend(context: Context, text: String): Boolean =
        backupMessageToFile(getBackupFile(context), text)

    suspend fun backupMessage(context: Context, text: String): Boolean =
        backupMessageBeforeSend(context, text)

    suspend fun loadBackupsFromFile(backupFile: File): List<MessageBackupEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!backupFile.exists()) return@withLock emptyList()
                val content = backupFile.readText()
                if (content.isBlank()) return@withLock emptyList()
                val array = JSONArray(content)
                val list = mutableListOf<MessageBackupEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    list.add(
                        MessageBackupEntry(
                            timestamp = obj.optLong("timestamp", 0L),
                            text = obj.optString("text", ""),
                        )
                    )
                }
                list
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun loadBackups(context: Context): List<MessageBackupEntry> =
        loadBackupsFromFile(getBackupFile(context))

    suspend fun clearBackupsFromFile(backupFile: File): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (backupFile.exists()) {
                    backupFile.delete()
                } else {
                    true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun clearBackups(context: Context): Boolean =
        clearBackupsFromFile(getBackupFile(context))
}
