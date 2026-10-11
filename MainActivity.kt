package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Botão SCAN
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        // Botão voltar
        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            findViewById<TextView>(R.id.txtNome).text = "Escaneie ou digite o código"
            findViewById<TextView>(R.id.txtCodigo).text = "-"
            findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")

            // MOSTRA O CÓDIGO NA TELA
            findViewById<TextView>(R.id.txtCodigo).text = codigo
            findViewById<TextView>(R.id.txtNome).text = "Código detectado!"

            // AQUI VAI A BUSCA DO PREÇO (próximo passo)
            buscarPreco(codigo ?: "")
        }
    }

    private fun buscarPreco(codigo: String) {
        // Por enquanto só mostra o código
        // Depois adiciono a busca no Cartaz Fácil
        findViewById<TextView>(R.id.txtPreco).text = "Buscando..."
    }
}
