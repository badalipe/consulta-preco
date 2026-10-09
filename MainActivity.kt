package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Botão ESCANEAR
        findViewById<Button>(R.id.btnEscanear).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        // Campo de digitação
        val edt = findViewById<EditText>(R.id.edtCodigo)
        edt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val codigo = edt.text.toString()
                if (codigo.isNotBlank()) {
                    val intent = Intent(this, ResultadoActivity::class.java)
                    intent.putExtra("codigo", codigo)
                    startActivity(intent)
                }
                true
            } else false
        }
    }
}
