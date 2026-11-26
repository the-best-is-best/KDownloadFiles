package io.github.kdownloadfile

/**
 * Base class for the errors [downloadFile] returns in its [Result], so callers can tell them apart:
 *
 * ```kotlin
 * downloadFile(url, "report.pdf").onFailure { error ->
 *     when (error) {
 *         is KDownloadFileHttpException -> if (error.statusCode == 404) showNoResult()
 *         is KDownloadFileNoInternetException -> showNoInternet()
 *         else -> showSomethingWentWrong()
 *     }
 * }
 * ```
 */
open class KDownloadFileException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** The server answered with a status code that is not 2xx, for example 404. */
class KDownloadFileHttpException(val statusCode: Int) :
    KDownloadFileException("Server answered HTTP $statusCode")

/** The server could not be reached: no connection, DNS failure or a timeout. */
class KDownloadFileNoInternetException(cause: Throwable? = null) :
    KDownloadFileException("No internet connection", cause)
