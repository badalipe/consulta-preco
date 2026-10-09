package com.quickprice.app

import android.Manifest
import android.content.Intent
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
import com.google.mlkit.common.MlKitException
import java.util.concurrent.Executors

/**
 * Scanner HÍBRIDO: 
 * 1. Tenta usar ML Kit (Google) - melhor performance
 * 2. Se falhar (Google Play Services desatualizado), avisa o usuário
 */
class ScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private var leuCodigo = false
    private val executor = Executors.newSingleThreadExecutor()
    private var tentativasMLKit = 0

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
            Toast.makeText(this, "Permissão de câmera necessária", Toast.LENGTH_LONG).show()
        }
    }

    private fun iniciarCamera() {
        try {
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
                    processarFrameMLKit(imageProxy)
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            // Se falhar ao iniciar ML Kit, oferece alternativa
            mostrarErroMLKit()
        }
    }

    private fun processarFrameMLKit(imageProxy: ImageProxy) {
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
            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

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
                    // Se falhar por causa do Google Play Services
                    if (e is MlKitException && 
                        e.errorCode == MlKitException.UNAVAILABLE) {
                        tentativasMLKit++
                        if (tentativasMLKit >= 3) {
                            mostrarErroMLKit()
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (e: Exception) {
            imageProxy.close()
        }
    }

    private fun mostrarErroMLKit() {
        runOnUiThread {
            Toast.makeText(
                this,
                "ML Kit precisa do Google Play Services atualizado. Abrindo scanner alternativo...",
                Toast.LENGTH_LONG
            ).show()

            // Abre ZXing como alternativa
            Handler(Looper.getMainLooper()).postDelayed({
                abrirZXing()
            }, 1500)
        }
    }

    private fun abrirZXing() {
        val intent = Intent(this, ZXingScannerActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun onCodigoLido(codigo: String) {
        runOnUiThread {
            // Bipe
            try {
                val toneGen = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_NOTIFICATION, 100
                )
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
            } catch (e: Exception) { }

            // Vai para ResultadoActivity
            val intent = Intent(this, ResultadoActivity::class.java).apply {
                putExtra(ResultadoActivity.EXTRA_CODIGO, codigo)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
