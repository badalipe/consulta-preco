package com.quickprice.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
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
    private lateinit var txtDebug: TextView
    private var leuCodigo = false
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout simples com debug
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
        }

        txtDebug = TextView(this).apply {
            text = "Iniciando câmera ML Kit..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setPadding(20, 20, 20, 20)
        }

        previewView = PreviewView(this)
        previewView.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        layout.addView(txtDebug)
        layout.addView(previewView)
        setContentView(layout)

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
            txtDebug.text = "ERRO: Permissão de câmera negada!"
        }
    }

    private fun iniciarCamera() {
        txtDebug.text = "Carregando CameraX..."

        try {
            val providerFuture = ProcessCameraProvider.getInstance(this)
            providerFuture.addListener({
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    txtDebug.text = "Câmera iniciada! Iniciando ML Kit..."

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(previewView.display.rotation)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        processarFrame(imageProxy)
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)

                    runOnUiThread {
                        txtDebug.text = "✓ ML Kit ATIVO - Aponte para o código de barras"
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        txtDebug.text = "ERRO CameraX: ${e.message}"
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            txtDebug.text = "ERRO ao iniciar: ${e.message}"
        }
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

        try {
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
                .addOnFailureListener { e ->
                    runOnUiThread {
                        txtDebug.text = "ERRO ML Kit: ${e.message}"
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } catch (e: Exception) {
            imageProxy.close()
        }
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
