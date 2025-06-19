package io.github.kdownloadfile

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSURLConnection
import platform.Foundation.NSURLResponse
import platform.Foundation.NSUserDomainMask
import platform.Foundation.sendSynchronousRequest
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.writeToURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UINavigationController
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue


private var documentController: UIDocumentInteractionController? = null
private var documentDelegate: UIDocumentInteractionControllerDelegateProtocol? = null

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


@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun downloadFile(
    url: String,
    fileName: String,
    folderName: String?,
    customHeaders: Map<String, String>,
): Result<String> = withContext(Dispatchers.Default) {
    memScoped {
        try {
            val nsUrl = NSURL.URLWithString(url.trim())
                ?: return@withContext Result.failure(Exception("Invalid URL"))

            val headRequest = NSMutableURLRequest().apply {
                setURL(nsUrl)
                setHTTPMethod("GET") // ✅ بدل HEAD
                setValue(getUserAgent(), forHTTPHeaderField = "User-Agent")
                setValue("bytes=0-0", forHTTPHeaderField = "Range") // ✅ نحمل أول بايت فقط

                for ((key, value) in customHeaders) {
                    setValue(value, forHTTPHeaderField = key)
                }
            }

            val headResponsePtr = alloc<ObjCObjectVar<NSURLResponse?>>()
            val headErrorPtr = alloc<ObjCObjectVar<NSError?>>()

            NSURLConnection.sendSynchronousRequest(
                request = headRequest,
                returningResponse = headResponsePtr.ptr,
                error = headErrorPtr.ptr
            )

            val headResponse = headResponsePtr.value as? NSHTTPURLResponse
            val statusCode = headResponse?.statusCode?.toInt() ?: -1

            if (statusCode !in 200..299) {
                return@withContext Result.failure(Exception("❌ Server responded with $statusCode"))
            }


            val request = NSMutableURLRequest().apply {
                setURL(nsUrl)
                setHTTPMethod("GET")
                setValue(getUserAgent(), forHTTPHeaderField = "User-Agent")

                // ✅ Add custom headers
                for ((key, value) in customHeaders) {
                    setValue(value, forHTTPHeaderField = key)
                }

            }

            val responsePtr = alloc<ObjCObjectVar<NSURLResponse?>>()
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()

            val data = NSURLConnection.sendSynchronousRequest(
                request = request,
                returningResponse = responsePtr.ptr,
                error = errorPtr.ptr
            )

            if (data == null || errorPtr.value != null) {
                val errorMsg = errorPtr.value?.localizedDescription ?: "Unknown error"
                return@withContext Result.failure(Exception("Failed to download file: $errorMsg"))
            }

            val fileManager = NSFileManager.defaultManager
            val documentsUrl = fileManager.URLForDirectory(
                NSDocumentDirectory,
                NSUserDomainMask,
                null,
                false,
                null
            ) ?: return@withContext Result.failure(Exception("Couldn't access document directory"))

            val baseFolderUrl = if (!folderName.isNullOrEmpty()) {
                val folderUrl = documentsUrl.URLByAppendingPathComponent(folderName, true)
                if (!fileManager.fileExistsAtPath(folderUrl!!.path!!)) {
                    val errorCreatePtr = alloc<ObjCObjectVar<NSError?>>()
                    fileManager.createDirectoryAtURL(folderUrl, true, null, errorCreatePtr.ptr)
                }
                folderUrl
            } else {
                documentsUrl
            }

            val destinationUrl = baseFolderUrl.URLByAppendingPathComponent(fileName)
            if (!data.writeToURL(destinationUrl!!, true)) {
                return@withContext Result.failure(Exception("Failed to write file"))
            }

            return@withContext Result.success(destinationUrl.path!!)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}

private fun getUserAgent(): String {
    val infoDictionary = NSBundle.mainBundle.infoDictionary
    val appName = infoDictionary?.get("CFBundleName") as? String ?: "App"
    val version = infoDictionary?.get("CFBundleShortVersionString") as? String ?: "1.0"
    val build = infoDictionary?.get("CFBundleVersion") as? String ?: "1"

    val cfNetworkVersion = NSProcessInfo.processInfo
        .environment["CFNETWORK_VERSION"] as? String ?: "Unknown"
    val darwinVersion = NSProcessInfo.processInfo
        .operatingSystemVersionString.split("Darwin/").getOrNull(1) ?: "Unknown"

    val modelName = UIDevice.currentDevice.model
    val platform = UIDevice.currentDevice.systemName
    val osVersion = NSProcessInfo.processInfo.operatingSystemVersionString

    return "$appName/$version.$build " +
            "($platform; $modelName; $osVersion) " +
            "CFNetwork/$cfNetworkVersion " +
            "Darwin/$darwinVersion"
}
