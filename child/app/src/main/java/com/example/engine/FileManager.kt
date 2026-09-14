package com.example.engine

import android.content.Context
import android.os.Environment
import com.example.data.model.FileItem
import java.io.File

class FileManager(private val context: Context) {

    fun getDefaultDirectories(): List<FileItem> {
        val list = mutableListOf<FileItem>()

        val internal = context.filesDir
        list.add(
            FileItem(
                name = "App Sandbox Storage",
                path = internal.absolutePath,
                isDirectory = true,
                sizeBytes = 0,
                lastModified = internal.lastModified()
            )
        )

        try {
            val download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (download != null && download.exists()) {
                list.add(
                    FileItem(
                        name = "Downloads",
                        path = download.absolutePath,
                        isDirectory = true,
                        sizeBytes = 0,
                        lastModified = download.lastModified()
                    )
                )
            }

            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (pictures != null && pictures.exists()) {
                list.add(
                    FileItem(
                        name = "Pictures",
                        path = pictures.absolutePath,
                        isDirectory = true,
                        sizeBytes = 0,
                        lastModified = pictures.lastModified()
                    )
                )
            }

            val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documents != null && documents.exists()) {
                list.add(
                    FileItem(
                        name = "Documents",
                        path = documents.absolutePath,
                        isDirectory = true,
                        sizeBytes = 0,
                        lastModified = documents.lastModified()
                    )
                )
            }

            val rootExternal = Environment.getExternalStorageDirectory()
            if (rootExternal != null && rootExternal.exists()) {
                list.add(
                    FileItem(
                        name = "Internal Shared Storage (/sdcard)",
                        path = rootExternal.absolutePath,
                        isDirectory = true,
                        sizeBytes = 0,
                        lastModified = rootExternal.lastModified()
                    )
                )
            }
        } catch (e: Exception) {
            // In scoped storage on newer Android versions, fallback smoothly
        }

        return list
    }

    fun listDirectory(directoryPath: String): List<FileItem> {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        val files = dir.listFiles() ?: return emptyList()
        return files.map { file ->
            FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0,
                lastModified = file.lastModified()
            )
        }.sortedWith(compareByDescending<FileItem> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    fun readFileContent(filePath: String, maxBytes: Int = 1024 * 1024): ByteArray? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile || !file.canRead()) return null
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(file.length().coerceAtMost(maxBytes.toLong()).toInt())
                val read = input.read(buffer)
                if (read > 0) buffer.copyOf(read) else ByteArray(0)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveUploadedFile(targetDirPath: String, fileName: String, data: ByteArray): Boolean {
        return try {
            val dir = File(targetDirPath)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.outputStream().use { it.write(data) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
