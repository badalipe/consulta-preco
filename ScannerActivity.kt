package com.quickprice.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private var leuCodigo = false
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)
        previewView = findViewById(R.id.previewView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            iniciarCamera()
        } else {
            Toast.makeText(this, "Permissão necessária", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(previewView.display.rotation)
                .build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                processarFrame(imageProxy)
            }

            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processarFrame(imageProxy: ImageProxy) {
        if (leuCodigo) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val scanner = BarcodeScanning.getClient()

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    when (barcode.format) {
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39 -> {
                            barcode.rawValue?.let { codigo ->
                                if (!leuCodigo) {
                                    leuCodigo = true
                                    onCodigoLido(codigo)
                                }
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun onCodigoLido(codigo: String) {
        runOnUiThread {
            try {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
            } catch (e: Exception) { }

            val intent = android.content.Intent().apply {
                putExtra("codigo", codigo)
            }
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
