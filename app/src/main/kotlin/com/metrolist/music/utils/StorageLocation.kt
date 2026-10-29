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
import timber.log.Timber

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

    private fun removableSdCardDirectory(context: Context): File? =
        context.getExternalFilesDirs(null)
            .asSequence()
            .filterNotNull()
            .drop(1)
            .firstOrNull { Environment.isExternalStorageRemovable(it) }
            ?.takeIf { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            ?.let { File(it, "Metrolist") }

    fun cacheDirectory(context: Context, name: String): File {
        // Artwork is disposable and stays in internal cache so a missing SD card cannot
        // prevent the app UI from creating its image loader.
        if (name == "coil") return File(context.cacheDir, name)

        if (selectedLocation(context) == SD_CARD) {
            val sdDirectory = removableSdCardDirectory(context)
            if (sdDirectory != null) return File(sdDirectory, name)

            // Let the app UI start when the card is temporarily absent. Existing downloads
            // remain on the card and must not be deleted; offline playback/downloads may fail
            // until the card is reinserted and the app restarted.
            Timber.w("SD card storage selected but unavailable; using an internal cache directory temporarily")
        }
        return File(context.filesDir, name)
    }

    /**
     * True when switching away from internal storage could hide cache/download files.
     * The caller should refuse a location change until a migration has been implemented.
     */
    fun hasExistingStorageData(context: Context): Boolean {
        val directories = listOf(
            File(context.filesDir, "download"),
            availableSdCardDirectory(context)?.let { File(it, "download") },
        ).filterNotNull()
        return directories.any { directory ->
            directory.exists() &&
                directory.walkTopDown().any { file ->
                    file.isFile && file.length() > 0L && file.name != ".uid"
                }
        }
    }
}
