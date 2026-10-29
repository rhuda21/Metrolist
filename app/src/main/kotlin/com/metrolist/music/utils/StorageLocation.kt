/**
 * Storage location helpers for the Metrolist SD-card fork.
 *
 * Only app-specific external directories are used; no broad storage permission is needed.
 * Location changes are deliberately restricted once cache/download data exists until a
 * verified migration flow is implemented. This avoids silently hiding offline downloads.
 */
package com.metrolist.music.utils

import android.content.Context
import android.os.Environment
import com.metrolist.music.constants.StorageLocationKey
import java.io.File

object StorageLocation {
    const val INTERNAL = "internal"
    const val SD_CARD = "sd_card"

    fun availableSdCardDirectory(context: Context): File? =
        context.getExternalFilesDirs(null)
            .asSequence()
            .filterNotNull()
            .drop(1)
            .firstOrNull { Environment.isExternalStorageRemovable(it) }
            ?.takeIf { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            ?.let { File(it, "Metrolist") }

    fun selectedLocation(context: Context): String =
        context.dataStore[StorageLocationKey] ?: INTERNAL

    fun selectedBaseDirectory(context: Context): File? =
        when (selectedLocation(context)) {
            SD_CARD -> availableSdCardDirectory(context)
            else -> context.filesDir
        }

    fun cacheDirectory(context: Context, name: String): File {
        val selected = selectedBaseDirectory(context)
        return when {
            selectedLocation(context) == SD_CARD && selected != null -> File(selected, name)
            name == "coil" -> File(context.cacheDir, name)
            else -> File(context.filesDir, name)
        }
    }

    /**
     * True when switching away from internal storage could hide cache/download files.
     * The caller should refuse a location change until a migration has been implemented.
     */
    fun hasExistingStorageData(context: Context): Boolean =
        listOf(
            File(context.filesDir, "download"),
            File(context.filesDir, "exoplayer"),
            File(context.cacheDir, "coil"),
            availableSdCardDirectory(context)?.let { File(it, "download") },
            availableSdCardDirectory(context)?.let { File(it, "exoplayer") },
            availableSdCardDirectory(context)?.let { File(it, "coil") },
        ).filterNotNull().any { directory ->
            directory.exists() && directory.listFiles()?.any { file -> file.isFile && file.length() > 0L && file.name != ".uid" } == true
        }
}
