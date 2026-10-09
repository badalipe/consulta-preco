package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ScannerActivity : AppCompatActivity() {

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            // Bipe
            try {
                val tg = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_NOTIFICATION, 100
                )
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
            } catch (e: Exception) { }

            // Vai para resultado
            val intent = Intent(this, ResultadoActivity::class.java).apply {
                putExtra("codigo", result.contents)
            }
            startActivity(intent)
            finish()
        } else {
            // Se cancelar, volta para o scanner
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val options = ScanOptions().apply {
            setPrompt("Aponte para o código de barras")
            setCameraId(0)
            setBeepEnabled(true)
            setOrientationLocked(false)
        }

        barcodeLauncher.launch(options)
    }
}
