package io.github.kdownloadfile

import kotlinx.io.IOException
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream

actual fun saveBytes(
    bytes: ByteArray,
    fileName: String,
    folderName: String
): String {
    try {
        // Create the folder if it doesn't exist
        val folder = File(folderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }

        // Create the file object with the given folder and file name
        val file = File(folder, fileName)

        // Write the bytes to the file
        FileOutputStream(file).use { outputStream ->
            outputStream.write(bytes)
        }

        return file.absolutePath // Return the file path after saving
    } catch (e: IOException) {
        e.printStackTrace()
        return "Error saving file: ${e.message}"
    }
}

actual fun openFile(filePath: String) {
    val file = File(filePath)
    if (file.exists()) {
        Desktop.getDesktop().open(file)
    } else {
        println("File does not exist: $filePath")
    }

}