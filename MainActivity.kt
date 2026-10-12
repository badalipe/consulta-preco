package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener {
            limpar()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            codigo?.let {
                findViewById<TextView>(R.id.txtCodigo).text = it
                buscarPreco(it)
            }
        }
    }

    private fun buscarPreco(codigo: String) {
        findViewById<TextView>(R.id.txtNome).text = "Buscando..."
        findViewById<TextView>(R.id.txtPreco).text = "..."

        Thread {
            try {
                val url = URL("https://rmarket.cartazfacil.pro/inc/buscaProduto.php?buscaProd=" + codigo + "&sessao=")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

                val response = conn.inputStream.bufferedReader().readText()

                runOnUiThread {
                    extrairPreco(response)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<TextView>(R.id.txtNome).text = "Erro: " + e.message
                    findViewById<TextView>(R.id.txtPreco).text = "R$ -"
                }
            }
        }.start()
    }

    private fun extrairPreco(html: String) {
        // Extrai nome do produto
        val nomeRegex = Regex("<small class='descRetornoProd[0-9]+'[^>]*>([^<]+)")
        val nomeMatch = nomeRegex.find(html)
        val nome = nomeMatch?.groupValues?.get(1)?.trim() ?: "Produto não encontrado"

        // Extrai preço
        val precoRegex = Regex("Valores: ([0-9]+,[0-9]{2})")
        val precoMatch = precoRegex.find(html)
        val preco = precoMatch?.groupValues?.get(1) ?: ""

        findViewById<TextView>(R.id.txtNome).text = nome
        findViewById<TextView>(R.id.txtPreco).text = if (preco.isNotEmpty()) "R$ " + preco else "R$ -"

        handler.postDelayed({
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }, 5000)
    }

    private fun limpar() {
        findViewById<TextView>(R.id.txtNome).text = "Escaneie ou digite o código"
        findViewById<TextView>(R.id.txtCodigo).text = "-"
        findViewById<TextView>(R.id.txtPreco).text = "R$ 0,00"
    }
}
