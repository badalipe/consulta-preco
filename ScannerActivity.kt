package com.quickprice.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class ScannerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scanner = GmsBarcodeScanning.getClient(this)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val codigo = barcode.rawValue
                codigo?.let {
                    try {
                        val tg = android.media.ToneGenerator(
                            android.media.AudioManager.STREAM_NOTIFICATION, 100
                        )
                        tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
                    } catch (e: Exception) { }

                    val intent = android.content.Intent().apply {
                        putExtra("codigo", it)
                    }
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }
            .addOnCanceledListener {
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Erro: " + e.message, Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
