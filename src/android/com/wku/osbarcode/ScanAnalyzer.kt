
package com.wku.osbarcode

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class ScanAnalyzer(
    private val onDecoded: (text: String, format: String, image: ImageProxy) -> Unit,
    private val onError: (Throwable) -> Unit
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    override fun analyze(image: ImageProxy) {
        val media = image.image ?: run { image.close(); return }
        val rotation = image.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(media, rotation)
        scanner.process(input)
            .addOnSuccessListener { list ->
                if (!list.isNullOrEmpty()) {
                    val b = list.first()
                    val text = b.rawValue ?: ""
                    val format = when (b.format) {
                        Barcode.FORMAT_QR_CODE -> "QR_CODE"
                        Barcode.FORMAT_PDF417 -> "PDF417"
                        Barcode.FORMAT_CODE_128 -> "CODE_128"
                        Barcode.FORMAT_EAN_13 -> "EAN_13"
                        Barcode.FORMAT_EAN_8 -> "EAN_8"
                        Barcode.FORMAT_UPC_A -> "UPC_A"
                        Barcode.FORMAT_UPC_E -> "UPC_E"
                        else -> "UNKNOWN"
                    }
                    onDecoded(text, format, image) // caller closes
                } else {
                    image.close()
                }
            }
            .addOnFailureListener { e -> image.close(); onError(e) }
    }
}
