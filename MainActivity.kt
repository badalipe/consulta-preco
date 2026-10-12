package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var edtCodigo: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        edtCodigo = findViewById(R.id.edtCodigo)

        // Botão SCAN
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        // Botão voltar
        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            limpar()
        }

        // BUSCA MANUAL - Quando pressiona Enter
        edtCodigo.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {

                val codigo = edtCodigo.text.toString().trim()
                if (codigo.isNotEmpty()) {
                    buscarPreco(codigo)
                } else {
                    Toast.makeText(this, "Digite um código", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            codigo?.let {
                findViewById<TextView>(R.id.txtCodigo).text = it
                edtCodigo.setText(it)
                buscarPreco(it)
            }
        }
    }

    private fun buscarPreco(codigo: String) {
        findViewById<TextView>(R.id.txtNome).text = "Buscando..."
        findViewById<TextView>(R.id.txtPreco).text = "..."

        Toast.makeText(this, "Buscando: " + codigo, Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val url = URL("https://rmarket.cartazfacil.pro/inc/buscaProduto.php?buscaProd=" + codigo + "&sessao=")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val response = conn.inputStream.bufferedReader().readText()

                runOnUiThread {
                    extrairPreco(response, codigo)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<TextView>(R.id.txtNome).text = "Erro: " + e.message
                    findViewById<TextView>(R.id.txtPreco).text = "R$ -"
                    Toast.makeText(this, "Erro na busca", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun extrairPreco(html: String, codigo: String) {
        // Extrai nome
        val nomeRegex = Regex("<small class='descRetornoProd[0-9]+'[^>]*>([^<]+)")
        val nomeMatch = nomeRegex.find(html)
        val nome = nomeMatch?.groupValues?.get(1)?.trim() ?: "Produto não encontrado"

        // Extrai preço
        val precoRegex = Regex("Valores: ([0-9]+,[0-9]{2})")
        val precoMatch = precoRegex.find(html)
        val preco = precoMatch?.groupValues?.get(1) ?: ""

        findViewById<TextView>(R.id.txtNome).text = nome
        findViewById<TextView>(R.id.txtPreco).text = if (preco.isNotEmpty()) "R$ " + preco else "R$ -"
        findViewById<TextView>(R.id.txtCodigo).text = codigo

        // Volta para scanner após 5 segundos
        handler.postDelayed({
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }, 5000)
    }

    private fun limpar() {
        findViewById<TextView>(R.id.txtNome).text = "Escaneie ou digite o código"
        findViewById<TextView>(R.id.txtCodigo).text = "-"
        findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
        edtCodigo.setText("")
    }
}
