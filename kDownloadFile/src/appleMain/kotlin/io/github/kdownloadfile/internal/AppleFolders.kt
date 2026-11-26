package io.github.kdownloadfile.internal

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * Where a file should be saved (the same folders as earlier versions of the library):
 * `Documents/Downloads`, `Library/Caches` or `Library/Application Support`, plus [folderName].
 *
 * Creates the folder. With `noDuplicateFile = false` it picks a free name; with `true` a file may
 * already exist at the returned URL and is to be replaced.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun targetUrl(
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration,
): NSURL {
    val fileManager = NSFileManager.defaultManager
    val base = when {
        configuration.saveToDownloads ->
            systemFolder(fileManager, NSDocumentDirectory)?.URLByAppendingPathComponent(
                "Downloads",
                true
            )

        configuration.saveInCacheFiles -> systemFolder(fileManager, NSCachesDirectory)
        else -> systemFolder(fileManager, NSApplicationSupportDirectory)
    } ?: error("Could not find the folder to save $fileName in")

    val folder = if (folderName.isNullOrBlank()) base else {
        base.URLByAppendingPathComponent(folderName, true)
            ?: error("Invalid folder name: $folderName")
    }
    throwOnError { errorPtr -> fileManager.createDirectoryAtURL(folder, true, null, errorPtr) }

    val name = if (configuration.noDuplicateFile) {
        fileName
    } else {
        freeFileName(fileName) { candidate ->
            folder.URLByAppendingPathComponent(candidate)?.path?.let(fileManager::fileExistsAtPath) == true
        }
    }
    return folder.URLByAppendingPathComponent(name) ?: error("Invalid file name: $name")
}

/** Marks cache files as not backed up (as the Swift side does) and returns the file's path. */
@OptIn(ExperimentalForeignApi::class)
internal fun finishSavedFile(target: NSURL, configuration: KDownloadFileConfiguration): String {
    if (configuration.saveInCacheFiles) {
        target.setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null)
    }
    return target.path ?: error("Saved the file but could not read its path")
}

private fun systemFolder(fileManager: NSFileManager, directory: NSSearchPathDirectory): NSURL? =
    fileManager.URLsForDirectory(directory, NSUserDomainMask).firstOrNull() as NSURL?
