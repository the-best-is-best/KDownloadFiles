package io.github.kdownloadfile.internal

import io.github.kdownloadfile.configration.KDownloadFileConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Where a file should be saved: `~/Downloads`, `~/.kdownloadfile/cache` or `~/.kdownloadfile`,
 * plus [folderName]. Creates the folder. With `noDuplicateFile = false` it picks a free name; with
 * `true` a file may already exist at the returned path and is to be replaced.
 */
internal fun targetPath(
    fileName: String,
    folderName: String?,
    configuration: KDownloadFileConfiguration
): Path {
    val home = System.getProperty("user.home")
    val base = when {
        configuration.saveToDownloads -> Paths.get(home, "Downloads")
        configuration.saveInCacheFiles -> Paths.get(home, ".kdownloadfile", "cache")
        else -> Paths.get(home, ".kdownloadfile")
    }
    val folder = if (folderName.isNullOrBlank()) base else base.resolve(folderName)
    Files.createDirectories(folder)

    val name = if (configuration.noDuplicateFile) {
        fileName
    } else {
        freeFileName(fileName) { Files.exists(folder.resolve(it)) }
    }
    return folder.resolve(name)
}

/**
 * Lets [write] fill a temporary file next to [target], then moves it to [target]. A failed or
 * rejected write (when [write] returns false or throws) never replaces or leaves a file at [target].
 */
internal inline fun writeThroughTempFile(target: Path, write: (temp: Path) -> Boolean): Boolean {
    val temp = Files.createTempFile(target.parent, ".kdownloadfile", ".part")
    try {
        if (!write(temp)) return false
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        return true
    } finally {
        Files.deleteIfExists(temp)
    }
}
