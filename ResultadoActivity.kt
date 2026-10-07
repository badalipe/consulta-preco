package com.quickprice.app

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela do consultor de preço: NOME do produto + PREÇO GIGANTE.
 * Um único botão grande: voltar a escanear. Toque em qualquer lugar
 * da tela também volta a escanear. Após 20s volta sozinho.
 */
class ResultadoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOME = "nome"
        const val EXTRA_PRECO = "preco"
        const val EXTRA_CODIGO = "codigo"
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resultado)

        val nome = intent.getStringExtra(EXTRA_NOME) ?: "Produto"
        val preco = intent.getStringExtra(EXTRA_PRECO) ?: "-"
        val codigo = intent.getStringExtra(EXTRA_CODIGO) ?: ""

        findViewById<TextView>(R.id.txtNome).text = nome
        findViewById<TextView>(R.id.txtPreco).text = preco
        findViewById<TextView>(R.id.txtCodigo).text = "Cód. $codigo"

        // Bipe de confirmação
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 300)
        }

        // Toque em qualquer lugar → escanear de novo
        findViewById<android.view.View>(R.id.btnNovoScan).setOnClickListener { voltarAoScanner() }
        findViewById<android.view.View>(R.id.root).setOnClickListener { voltarAoScanner() }

        // Volta sozinho pro scanner após 20s (fluxo contínuo de consulta)
        handler.postDelayed({ voltarAoScanner() }, 20_000)
    }

    private fun voltarAoScanner() {
        handler.removeCallbacksAndMessages(null)
        startActivity(
            Intent(this, ScannerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }
}
