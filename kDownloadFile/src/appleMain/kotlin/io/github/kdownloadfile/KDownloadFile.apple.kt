package io.github.kdownloadfile

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual fun saveBytes(
    bytes: ByteArray,
    fileName: String,
    folderName: String
): String {
    val fileManager = NSFileManager.defaultManager

    // ✅ تحديد المسار الأساسي بناءً على نوع التحميل
    val baseDirectory = NSDocumentDirectory

    // ✅ الحصول على مسار المجلد الكامل
    val directoryUrl = fileManager.URLForDirectory(
        baseDirectory,
        NSUserDomainMask,
        null,
        true,
        null
    )?.URLByAppendingPathComponent(folderName)

    val directoryPath =
        directoryUrl?.path ?: throw IllegalStateException("❌ Invalid directory path")

    // ✅ التأكد من أن المجلد موجود
    if (!fileManager.fileExistsAtPath(directoryPath)) {
        val success = fileManager.createDirectoryAtPath(
            directoryPath,
            attributes = emptyMap<Any?, Any?>()
        )
        if (!success) throw IllegalStateException("❌ Failed to create directory at $directoryPath")
    }

    // ✅ إنشاء اسم ملف فريد إذا كان الاسم موجودًا مسبقًا
    var finalFileUrl = directoryUrl.URLByAppendingPathComponent(fileName)
    var finalFilePath =
        finalFileUrl?.path ?: throw IllegalStateException("❌ Could not create file URL")

    while (fileManager.fileExistsAtPath(finalFilePath)) {
        val uniqueName = "${NSUUID.UUID().UUIDString}_$fileName"
        finalFileUrl = directoryUrl.URLByAppendingPathComponent(uniqueName)
        finalFilePath = finalFileUrl?.path
            ?: throw IllegalStateException("❌ Could not generate unique file path")
    }

    // ✅ كتابة البيانات إلى الملف
    val data = bytes.usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
    }

    if (!data.writeToFile(finalFilePath, true)) {
        throw IllegalStateException("❌ Failed to save file at: $finalFilePath")
    }

    println("✅ File saved successfully at: $finalFilePath")
    return finalFilePath
}

actual fun openFile(filePath: String) {
    val url = NSURL.fileURLWithPath(filePath)

    val documentController = UIDocumentInteractionController().apply {
        this.URL = url
    }

    val keyWindow = UIApplication.sharedApplication.delegate?.window
        ?: throw IllegalStateException("❌ No active window found!")

    val rootViewController = keyWindow.rootViewController
        ?: throw IllegalStateException("❌ No root view controller found!")

    // تعيين المفوض
    val delegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
        override fun documentInteractionControllerViewControllerForPreview(
            controller: UIDocumentInteractionController
        ): UIViewController {
            return rootViewController
        }
    }

    documentController.delegate = delegate

    // عرض المعاينة
    documentController.presentPreviewAnimated(true)
}