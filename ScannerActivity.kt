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
        ) iniciarCamera() else
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            iniciarCamera()
        else Toast.makeText(this, "Permissão de câmera necessária", Toast.LENGTH_LONG).show()
    }

    private fun iniciarCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            val analise = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, LeitorCodigo()) }

            provider.unbindAll()
            provider.bindToLifecycle(
                this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analise
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private inner class LeitorCodigo : ImageAnalysis.Analyzer {
        private val scanner = BarcodeScanning.getClient()

        override fun analyze(imageProxy: ImageProxy) {
            if (leuCodigo) { imageProxy.close(); return }
            val media = imageProxy.image ?: run { imageProxy.close(); return }
            val input = InputImage.fromMediaImage(
                media, imageProxy.imageInfo.rotationDegrees
            )
            scanner.process(input)
                .addOnSuccessListener { codigos ->
                    codigos.firstOrNull { it.format == Barcode.FORMAT_EAN_13
                            || it.format == Barcode.FORMAT_EAN_8
                            || it.format == Barcode.FORMAT_UPC_A
                            || it.format == Barcode.FORMAT_UPC_E
                            || it.format == Barcode.FORMAT_CODE_128
                            || it.format == Barcode.FORMAT_CODE_39 }
                        ?.rawValue?.let { codigo -> onCodigoLido(codigo) }
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    private fun onCodigoLido(codigo: String) {
        if (leuCodigo) return
        leuCodigo = true
        runOnUiThread {
            startActivity(
                Intent(this, SistemaActivity::class.java)
                    .putExtra(SistemaActivity.EXTRA_CODIGO, codigo)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            leuCodigo = false
        }
    }

    override fun onDestroy() { super.onDestroy(); executor.shutdown() }
}
