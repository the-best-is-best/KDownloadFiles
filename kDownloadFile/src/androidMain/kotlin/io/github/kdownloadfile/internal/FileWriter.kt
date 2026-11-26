package io.github.kdownloadfile.internal

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "KDownloadFile"

/**
 * Writes [bytes] into [folder] through a temporary file, so a failed write never leaves a
 * partial file behind. With [noDuplicateFile] an existing file is replaced; otherwise a free
 * name such as `name (1).ext` is used. Reports progress to [progress] while writing.
 *
 * @return the path of the written file.
 */
internal fun writeFile(
    folder: File,
    bytes: ByteArray,
    fileName: String,
    noDuplicateFile: Boolean,
    progress: ProgressReporter,
): String {
    if (!folder.exists() && !folder.mkdirs()) throw IOException("Could not create ${folder.absolutePath}")
    val name =
        if (noDuplicateFile) fileName else freeFileName(fileName) { File(folder, it).exists() }
    val temp = File(folder, ".$name.part")
    try {
        temp.outputStream().use { output ->
            writeInChunks(bytes, progress) { offset, length -> output.write(bytes, offset, length) }
        }
        return moveFile(temp, File(folder, name)).absolutePath
    } finally {
        temp.delete()
    }
}

/** Moves [source] to [target], replacing [target] if it exists. */
internal fun moveFile(source: File, target: File): File {
    if (source == target) return target
    target.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create $it") }
    deleteExisting(target)
    if (!source.renameTo(target)) {
        // renameTo fails across storage volumes (external files -> internal cache): copy instead.
        source.copyTo(target, overwrite = true)
        source.delete()
    }
    return target
}

/**
 * Deletes [file] if it exists. Some file systems refuse to delete a file that is still open,
 * so it is emptied first and deleted again.
 */
internal fun deleteExisting(file: File) {
    if (!file.exists() || file.delete()) return
    FileOutputStream(file).use { stream ->
        stream.channel.truncate(0)
        stream.fd.sync()
    }
    if (!file.delete()) {
        Log.e(TAG, "💥 Could not delete ${file.absolutePath}")
        throw IOException("Could not replace ${file.absolutePath}")
    }
}
