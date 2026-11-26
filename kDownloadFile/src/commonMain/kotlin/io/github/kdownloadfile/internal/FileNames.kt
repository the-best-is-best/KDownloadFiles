package io.github.kdownloadfile.internal

/**
 * Returns [fileName], or the first of `name (1).ext`, `name (2).ext`... for which [exists] is false.
 */
internal fun freeFileName(fileName: String, exists: (String) -> Boolean): String {
    if (!exists(fileName)) return fileName
    val dot = fileName.lastIndexOf('.')
    val base = if (dot > 0) fileName.substring(0, dot) else fileName
    val ext = if (dot > 0) fileName.substring(dot) else ""
    var counter = 1
    while (true) {
        val candidate = "$base ($counter)$ext"
        if (!exists(candidate)) return candidate
        counter++
    }
}
