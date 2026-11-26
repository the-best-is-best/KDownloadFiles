package io.github.kdownloadfile

import io.github.kdownloadfile.configration.KDownloadFileConfiguration

/**
 * Saves content you already have (for example a response body you downloaded yourself with Ktor)
 * to the same place [downloadFile] would save it, without any network request.
 *
 * Use it when [downloadFile] does not fit: the request needs your own HTTP client, auth or
 * retry logic, you need the exact HTTP status code, or the content is generated on the device.
 *
 * Where the file goes:
 * - [KDownloadFileConfiguration.saveToDownloads]: Android `Download/<app name>/<folderName>`,
 *   iOS `Documents/Downloads/<folderName>`, desktop `~/Downloads/<folderName>`.
 * - [KDownloadFileConfiguration.saveInCacheFiles]: the app's cache folder.
 * - Neither: the app's private files folder.
 *
 * With [KDownloadFileConfiguration.noDuplicateFile] an existing file with the same name is replaced;
 * otherwise the new file gets a free name such as `report (1).pdf`.
 *
 * On JS the browser downloads the file, and the result is a blob URL instead of a path.
 *
 * @return the path of the saved file, which can be passed to [openFile].
 */
expect suspend fun saveFile(
    bytes: ByteArray,
    fileName: String,
    folderName: String? = null,
    configuration: KDownloadFileConfiguration = KDownloadFileConfiguration(),
): Result<String>
