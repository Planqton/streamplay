package at.plankt0n.streamplay.ui

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import at.plankt0n.streamplay.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LiveScanActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var root: FrameLayout
    private lateinit var resultView: TextView
    private lateinit var cameraExecutor: ExecutorService
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var currentText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_scan)

        previewView = findViewById(R.id.previewView)
        root = findViewById(R.id.root)
        resultView = findViewById(R.id.recognizedText)

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()

        root.setOnClickListener {
            val text = currentText.trim()
            if (text.isNotEmpty()) {
                val data = Intent().putExtra("scanned_text", text)
                setResult(RESULT_OK, data)
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val width: Int
            val height: Int
            if (rotation == 0 || rotation == 180) {
                width = mediaImage.width
                height = mediaImage.height
            } else {
                width = mediaImage.height
                height = mediaImage.width
            }
            val cropHeight = height / 24
            val top = height / 2 - cropHeight / 2
            val allowedRect = Rect(0, top, width, top + cropHeight)
            val image = InputImage.fromMediaImage(
                mediaImage,
                rotation
            )
            textRecognizer.process(image)
                .addOnSuccessListener { text ->
                    var bestLine: String? = null
                    for (block in text.textBlocks) {
                        for (line in block.lines) {
                            val box = line.boundingBox
                            if (box != null && allowedRect.contains(box)) {
                                if (bestLine == null || line.text.length > bestLine!!.length) {
                                    bestLine = line.text
                                }
                            }
                        }
                    }
                    val result = bestLine?.trim() ?: ""
                    runOnUiThread {
                        if (result.isNotEmpty() && result != currentText) {
                            currentText = result
                            resultView.text = result
                        }
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        textRecognizer.close()
    }
}
