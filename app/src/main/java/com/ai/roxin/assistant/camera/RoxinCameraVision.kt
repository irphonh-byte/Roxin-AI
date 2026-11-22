package com.ai.maya.assistant.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Roxin Camera Vision Controller.
 * Captures live frames from phone camera and prepares them for Gemini Multimodal Vision analysis.
 */
class RoxinCameraVision(private val context: Context) {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("RoxinCameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread?.looper!!)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("RoxinCamera", "Interrupted", e)
        }
    }

    /**
     * Captures a single image from the back camera and returns Base64 encoded JPEG for Gemini API.
     */
    fun captureCurrentView(onCaptured: (base64Image: String) -> Unit) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        startBackgroundThread()

        try {
            val cameraId = manager.cameraIdList[0] // Primary back camera
            imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()

                // Convert to compressed Base64 JPEG
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)

                closeCamera()
                onCaptured(base64)
            }, backgroundHandler)

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    closeCamera()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    closeCamera()
                }
            }, backgroundHandler)

        } catch (e: SecurityException) {
            Log.e("RoxinCamera", "Camera permission needed", e)
        } catch (e: Exception) {
            Log.e("RoxinCamera", "Failed to capture", e)
        }
    }

    private fun createCaptureSession() {
        try {
            val surface = imageReader?.surface ?: return
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder?.addTarget(surface)

            cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    captureRequestBuilder?.let {
                        session.capture(it.build(), null, backgroundHandler)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    closeCamera()
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            Log.e("RoxinCamera", "Access error", e)
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        stopBackgroundThread()
    }
}
