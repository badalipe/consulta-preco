package com.quickprice.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ScannerActivity : AppCompatActivity() {

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            // Volta para MainActivity com o código
            val intent = android.content.Intent().apply {
                putExtra("codigo", result.contents)
            }
            setResult(RESULT_OK, intent)
            finish()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val options = ScanOptions().apply {
            setPrompt("Aponte para o código de barras")
            setCameraId(0)
            setBeepEnabled(true)
        }

        barcodeLauncher.launch(options)
    }
}
