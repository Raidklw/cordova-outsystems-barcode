
package com.wku.osbarcode

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Base64
import android.util.Size
import android.view.WindowManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanAndGrabActivity : Activity() {
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var finished = false

    private val jpegQuality by lazy { intent?.getIntExtra("jpegQuality", 80) ?: 80 }
    private val facingBack  by lazy { intent?.getBooleanExtra("facingBack", true) ?: true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(resources.getIdentifier("activity_scan_and_grab", "layout", packageName))
        previewView = findViewById(resources.getIdentifier("previewView", "id", packageName))
        cameraExecutor = Executors.newSingleThreadExecutor()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        }
    }

    override fun onRequestPermissionsResult(reqCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(reqCode, permissions, grantResults)
        if (reqCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
        else { OSBarcodePlugin.completeWithError("Camera permission denied"); finish() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().setTargetResolution(Size(1280, 720)).build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor, ScanAnalyzer(
                onDecoded = { text, format, imageProxy ->
                    if (finished) { imageProxy.close(); return@ScanAnalyzer }
                    finished = true
                    try {
                        val jpeg = yuv420ToJpeg(imageProxy, jpegQuality)
                        val rotated = rotateJpegIfNeeded(jpeg, imageProxy.imageInfo.rotationDegrees)
                        val base64 = Base64.encodeToString(rotated, Base64.NO_WRAP)
                        OSBarcodePlugin.completeWithSuccess(IntentResult(text, format, base64))
                    } catch (t: Throwable) {
                        OSBarcodePlugin.completeWithError("Snapshot failed: ${t.message}")
                    } finally { runOnUiThread { finish() } }
                },
                onError = { e -> if (!finished) { finished = true; OSBarcodePlugin.completeWithError(e.message ?: "Decode failed"); runOnUiThread { finish() } } }
            ))
            val selector = if (facingBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            try { cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(this, selector, preview, analysis) }
            catch (e: Exception) { OSBarcodePlugin.completeWithError("Camera bind failed: ${e.message}"); finish() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun yuv420ToJpeg(image: ImageProxy, quality: Int): ByteArray {
        val nv21 = yuv420888ToNv21(image)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), quality, out)
        image.close()
        return out.toByteArray()
    }

    private fun rotateJpegIfNeeded(jpeg: ByteArray, rotationDegrees: Int): ByteArray {
        if (rotationDegrees == 0) return jpeg
        val src = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val dst = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val out = ByteArrayOutputStream()
        dst.compress(Bitmap.CompressFormat.JPEG, 100, out)
        return out.toByteArray()
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val out = ByteArray(ySize + uvSize)

        val yPlane = image.planes[0]; val yBuf = yPlane.buffer
        val yRow = yPlane.rowStride; val yPix = yPlane.pixelStride
        var pos = 0
        if (yPix == 1 && yRow == width) { yBuf.get(out, 0, ySize); pos = ySize }
        else {
            val row = ByteArray(yRow)
            for (r in 0 until height) {
                yBuf.position(r * yRow)
                val len = min(yRow, yBuf.remaining())
                yBuf.get(row, 0, len)
                var c = 0
                while (c < width) { out[pos++] = row[c * yPix]; c++ }
            }
        }

        val uPlane = image.planes[1]; val vPlane = image.planes[2]
        val uBuf = uPlane.buffer; val vBuf = vPlane.buffer
        val uRow = uPlane.rowStride; val vRow = vPlane.rowStride
        val uPix = uPlane.pixelStride; val vPix = vPlane.pixelStride
        val cHeight = height / 2; val cWidth = width / 2
        val uRowBytes = ByteArray(uRow); val vRowBytes = ByteArray(vRow)
        for (r in 0 until cHeight) {
            uBuf.position(r * uRow); vBuf.position(r * vRow)
            uBuf.get(uRowBytes, 0, min(uRow, uBuf.remaining()))
            vBuf.get(vRowBytes, 0, min(vRow, vBuf.remaining()))
            var c = 0; var uIdx = 0; var vIdx = 0
            while (c < cWidth) { out[pos++] = vRowBytes[vIdx]; out[pos++] = uRowBytes[uIdx]; uIdx += uPix; vIdx += vPix; c++ }
        }
        return out
    }

    private fun min(a: Int, b: Int) = a if (a < b) else b

    override fun onDestroy() { super.onDestroy(); if (::cameraExecutor.isInitialized) cameraExecutor.shutdown() }
}
