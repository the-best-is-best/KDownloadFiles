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
import platform.Foundation.create

/** Runs a Foundation call that reports failure through an `NSError**`, and throws that error. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun throwOnError(call: (CPointer<ObjCObjectVar<NSError?>>) -> Boolean) = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    if (!call(error.ptr)) {
        throw IllegalStateException(error.value?.localizedDescription ?: "Unknown file error")
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
