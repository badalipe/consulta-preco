package com.quickprice.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

class ScannerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Google Play Services Code Scanner - mais rápido e integrado
        val scanner = GmsBarcodeScanning.getClient(this)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                // Código detectado
                val codigo = barcode.rawValue
                codigo?.let {
                    // Bipe
                    try {
                        val tg = android.media.ToneGenerator(
                            android.media.AudioManager.STREAM_NOTIFICATION, 100
                        )
                        tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
                    } catch (e: Exception) { }

                    // Retorna código
                    val intent = android.content.Intent().apply {
                        putExtra("codigo", it)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }
            .addOnCanceledListener {
                // Usuário cancelou
                finish()
            }
            .addOnFailureListener { e ->
                // Erro
                Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
