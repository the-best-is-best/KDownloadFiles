package io.github.kdownloadfile

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSInteger
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Shows the file with Quick Look (`QLPreviewController`) on top of the app's current screen. */
@OptIn(ExperimentalForeignApi::class)
actual fun openFile(filePath: String) {
    if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
        println("KDownloadFile: file does not exist: $filePath")
        return
    }
    dispatch_async(dispatch_get_main_queue()) {
        val presenter = topViewController() ?: run {
            println("KDownloadFile: no screen to show the file on")
            return@dispatch_async
        }
        val source = PreviewSource(NSURL.fileURLWithPath(filePath))
        // QLPreviewController keeps its data source weakly, so it is kept here while shown.
        shownPreview = source
        val preview = QLPreviewController()
        preview.dataSource = source
        presenter.presentViewController(preview, animated = true, completion = null)
    }
}

private var shownPreview: PreviewSource? = null

private class PreviewSource(private val url: NSURL) : NSObject(), QLPreviewControllerDataSourceProtocol {
    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): NSInteger = 1

    // NSURL is a Quick Look preview item (QuickLook declares it on NSURL).
    override fun previewController(
        controller: QLPreviewController,
        previewItemAtIndex: NSInteger,
    ): QLPreviewItemProtocol = url as QLPreviewItemProtocol
}

/** The screen on top: the first window's root, then whatever it presents. */
private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull()
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
