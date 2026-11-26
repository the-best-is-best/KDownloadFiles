package io.github.kdownloadfile.internal

/**
 * Returns [fileName], or the first of `name (1).ext`, `name (2).ext`... for which [exists] is false.
 */
internal fun freeFileName(fileName: String, exists: (String) -> Boolean): String =
    fileNameCandidates(fileName).first { !exists(it) }

/** [fileName], then `name (1).ext`, `name (2).ext`... without end. */
internal fun fileNameCandidates(fileName: String): Sequence<String> {
    val dot = fileName.lastIndexOf('.')
    val base = if (dot > 0) fileName.substring(0, dot) else fileName
    val ext = if (dot > 0) fileName.substring(dot) else ""
    return sequenceOf(fileName) + generateSequence(1) { it + 1 }.map { "$base ($it)$ext" }
}
