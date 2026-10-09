package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * Scanner alternativo usando ZXing (quando ML Kit não está disponível)
 */
class ZXingScannerActivity : AppCompatActivity() {

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            // Bipe
            try {
                val toneGen = android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_NOTIFICATION, 100
                )
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
            } catch (e: Exception) { }

            val intent = Intent(this, ResultadoActivity::class.java).apply {
                putExtra(ResultadoActivity.EXTRA_CODIGO, result.contents)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
            finish()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(
                ScanOptions.EAN_13,
                ScanOptions.EAN_8,
                ScanOptions.UPC_A,
                ScanOptions.UPC_E,
                ScanOptions.CODE_128,
                ScanOptions.CODE_39
            )
            setPrompt("Aponte para o código de barras")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }

        barcodeLauncher.launch(options)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
