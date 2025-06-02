package io.github.kdownloadfile

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUserDomainMask
import platform.Foundation.downloadTaskWithURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

//
//@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
//actual fun saveBytes(
//    bytes: ByteArray,
//    fileName: String,
//    folderName: String
//): String {
//    // Get the default file manager instance
//    val fileManager = NSFileManager.defaultManager
//    // Define the base directory as the app's Documents directory
//    val baseDirectory = NSDocumentDirectory
//
//    // Get the URL for the specified folder inside Documents directory
//    val directoryUrl = fileManager.URLForDirectory(
//        baseDirectory,
//        NSUserDomainMask,
//        null,
//        true,
//        null
//    )?.URLByAppendingPathComponent(folderName)
//        ?: throw IllegalStateException("Invalid directory URL")
//
//    // Get the path string of the directory
//    val directoryPath = directoryUrl.path ?: throw IllegalStateException("Invalid directory path")
//
//    // Create the directory if it doesn't exist yet
//    if (!fileManager.fileExistsAtPath(directoryPath)) {
//        val success = fileManager.createDirectoryAtPath(directoryPath, attributes = emptyMap<Any?, Any?>())
//        if (!success) throw IllegalStateException("Failed to create directory at $directoryPath")
//    }
//
//    // Create the full file URL by appending the fileName
//    var finalFileUrl = directoryUrl.URLByAppendingPathComponent(fileName)
//    // Get the path string of the final file URL
//    var finalFilePath = finalFileUrl?.path ?: throw IllegalStateException("Could not create file URL")
//
//    // If file with same name exists, generate a unique file name
//    while (fileManager.fileExistsAtPath(finalFilePath)) {
//        val uniqueName = "${NSUUID.UUID().UUIDString}_$fileName"
//        finalFileUrl = directoryUrl.URLByAppendingPathComponent(uniqueName)
//        finalFilePath = finalFileUrl?.path ?: throw IllegalStateException("Could not generate unique file path")
//    }
//
//    // Create NSData from the byte array
//    val data = bytes.usePinned { pinned ->
//        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
//    }
//
//    // Write the NSData to the file at the specified path
//    if (!data.writeToFile(finalFilePath, true)) {
//        throw IllegalStateException("Failed to save file at: $finalFilePath")
//    }
//
//    println("File saved successfully at: $finalFilePath")
//    return finalFilePath
//}

private var documentController: UIDocumentInteractionController? = null
private var documentDelegate: UIDocumentInteractionControllerDelegateProtocol? = null

@OptIn(ExperimentalForeignApi::class)
actual fun openFile(filePath: String) {
    val url = NSURL.fileURLWithPath(filePath)

    documentDelegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
        override fun documentInteractionControllerViewControllerForPreview(
            controller: UIDocumentInteractionController
        ): UIViewController {
            return getTopViewController()
        }
    }

    documentController = UIDocumentInteractionController().apply {
        this.URL = url
        this.delegate = documentDelegate
    }

    dispatch_async(dispatch_get_main_queue()) {
        documentController?.presentPreviewAnimated(true)
    }
}

private fun getTopViewController(
    base: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
): UIViewController {
    var baseVC = base
    while (true) {
        baseVC = when {
            baseVC == null -> throw IllegalStateException("No root view controller")
            baseVC.presentedViewController != null -> baseVC.presentedViewController
            baseVC is UINavigationController -> baseVC.visibleViewController
            baseVC is UITabBarController -> baseVC.selectedViewController
            else -> return baseVC
        }
    }
}


@OptIn(ExperimentalTime::class)
private fun generateHashedFileName(originalName: String): String {
    val dotIndex = originalName.lastIndexOf('.')
    val namePart = if (dotIndex != -1) originalName.substring(0, dotIndex) else originalName
    val extPart = if (dotIndex != -1) originalName.substring(dotIndex) else ""

    // استخدم timestamp كهاش بسيط
    val hash = Clock.System.now().toEpochMilliseconds()
    return "${namePart}_$hash$extPart"
}


@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?
): Result<String> {
    return try {
        suspendCancellableCoroutine { cont ->
            val nsUrl = NSURL.URLWithString(url) ?: run {
                cont.resume(Result.failure(Exception("Invalid URL")))
                return@suspendCancellableCoroutine
            }
            val fileManager = NSFileManager.defaultManager
            val documentsUrl = fileManager.URLForDirectory(
                NSDocumentDirectory,
                NSUserDomainMask,
                null,
                false,
                null
            ) ?: run {
                cont.resume(Result.failure(Exception("Could not get documents directory")))
                return@suspendCancellableCoroutine
            }

            // حدد مسار المجلد (لو folderName موجود)
            val baseFolderUrl = if (folderName != null) {
                val folderUrl = documentsUrl.URLByAppendingPathComponent(folderName, true)
                if (!fileManager.fileExistsAtPath(folderUrl!!.path!!)) {
                    val errorPtr = nativeHeap.alloc<ObjCObjectVar<NSError?>>()
                    try {
                        fileManager.createDirectoryAtURL(folderUrl, true, null, errorPtr.ptr)
                    } finally {
                        nativeHeap.free(errorPtr)
                    }
                }
                folderUrl
            } else {
                documentsUrl
            }

            var destinationUrl = baseFolderUrl.URLByAppendingPathComponent(fileName)

            // لو الملف موجود مسبقاً، نعدل الاسم بإضافة هاش
            if (fileManager.fileExistsAtPath(destinationUrl!!.path!!)) {
                val newFileName = generateHashedFileName(fileName)
                destinationUrl = baseFolderUrl.URLByAppendingPathComponent(newFileName)
            }

            val session = NSURLSession.sharedSession

            val task = session.downloadTaskWithURL(nsUrl) { tempFileUrl, response, error ->
                if (error != null) {
                    cont.resume(Result.failure(Exception(error.localizedDescription)))
                    return@downloadTaskWithURL
                }

                val httpResponse = response as? NSHTTPURLResponse
                if (httpResponse?.statusCode?.toInt() == 200 && tempFileUrl != null) {
                    val errorPtr = nativeHeap.alloc<ObjCObjectVar<NSError?>>()
                    try {
                        val success =
                            fileManager.moveItemAtURL(tempFileUrl, destinationUrl!!, errorPtr.ptr)
                        if (!success) {
                            val nsError = errorPtr.value
                            val message = nsError?.localizedDescription ?: "Unknown error"
                            cont.resume(Result.failure(Exception("Failed to move file: $message")))
                            return@downloadTaskWithURL
                        }
                        cont.resume(Result.success(destinationUrl.path ?: ""))
                    } finally {
                        nativeHeap.free(errorPtr)
                    }
                } else {
                    cont.resume(Result.failure(Exception("Download failed with status ${httpResponse?.statusCode}")))
                }
            }

            task.resume()

            cont.invokeOnCancellation {
                task.cancel()
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
