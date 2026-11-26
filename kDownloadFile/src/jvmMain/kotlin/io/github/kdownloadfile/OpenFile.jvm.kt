package io.github.kdownloadfile

import java.awt.Desktop
import java.io.File

actual fun openFile(filePath: String) {
    val file = File(filePath)
    if (file.exists()) {
        Desktop.getDesktop().open(file)
    } else {
        println("File does not exist: $filePath")
    }
}
