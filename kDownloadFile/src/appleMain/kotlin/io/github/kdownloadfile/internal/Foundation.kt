package io.github.kdownloadfile.internal

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingToURL

/** Runs a Foundation call that reports failure through an `NSError**`, and throws that error. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun throwOnError(call: (CPointer<ObjCObjectVar<NSError?>>) -> Boolean) = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    if (!call(error.ptr)) {
        throw IllegalStateException(error.value?.localizedDescription ?: "Unknown file error")
    }
}

/** Copies part of a ByteArray into NSData. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(offset: Int = 0, length: Int = size - offset): NSData =
    if (length == 0) NSData() else usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(offset), length = length.toULong())
    }

/**
 * Writes [bytes] to [target] in chunks, reporting [progress] after each one. It writes to a temporary
 * file next to [target] first, so a failed write never replaces or leaves a partial file at [target].
 */
@OptIn(ExperimentalForeignApi::class)
internal fun writeInChunksToFile(bytes: ByteArray, target: NSURL, progress: ProgressReporter) {
    val fileManager = NSFileManager.defaultManager
    val folder = target.URLByDeletingLastPathComponent ?: error("Invalid file path")
    val temp = folder.URLByAppendingPathComponent(".${target.lastPathComponent}.part")
        ?: error("Invalid file path")
    val tempPath = temp.path ?: error("Invalid file path")

    if (!fileManager.createFileAtPath(tempPath, null, null)) error("Could not create $tempPath")
    try {
        val handle =
            throwingCall { errorPtr -> NSFileHandle.fileHandleForWritingToURL(temp, errorPtr) }
        try {
            writeInChunks(bytes, progress) { offset, length ->
                throwOnError { errorPtr ->
                    handle.writeData(
                        bytes.toNSData(offset, length),
                        errorPtr
                    )
                }
            }
        } finally {
            handle.closeAndReturnError(null)
        }
        target.path?.takeIf(fileManager::fileExistsAtPath)?.let { path ->
            throwOnError { errorPtr -> fileManager.removeItemAtPath(path, errorPtr) }
        }
        throwOnError { errorPtr -> fileManager.moveItemAtURL(temp, target, errorPtr) }
    } finally {
        if (fileManager.fileExistsAtPath(tempPath)) fileManager.removeItemAtPath(tempPath, null)
    }
}

/** Like [throwOnError], for Foundation calls that return an object or null. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun <T : Any> throwingCall(call: (CPointer<ObjCObjectVar<NSError?>>) -> T?): T = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    call(error.ptr) ?: throw IllegalStateException(
        error.value?.localizedDescription ?: "Unknown file error"
    )
}
