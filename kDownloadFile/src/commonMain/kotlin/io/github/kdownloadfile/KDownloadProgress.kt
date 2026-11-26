package io.github.kdownloadfile

/**
 * How far a [downloadFile] or [saveFile] has got.
 *
 * ```kotlin
 * downloadFile(url, "report.pdf", onProgress = { progress ->
 *     percentText = progress.percent?.let { "$it%" } ?: "Downloading…"
 * })
 * ```
 *
 * @property bytesDone bytes downloaded or written so far.
 * @property totalBytes the full size, or null when the server did not say (no `Content-Length`);
 *   then show an indeterminate loader instead of a percentage.
 */
data class KDownloadProgress(
    val bytesDone: Long,
    val totalBytes: Long?,
) {
    /** 0.0 to 1.0, for a progress bar; null when [totalBytes] is unknown. */
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (bytesDone.toFloat() / it).coerceIn(0f, 1f) }

    /** 0 to 100; null when [totalBytes] is unknown. */
    val percent: Int?
        get() = fraction?.let { (it * 100).toInt() }
}
