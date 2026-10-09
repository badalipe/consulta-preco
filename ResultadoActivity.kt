package com.quickprice.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class ResultadoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val codigo = intent.getStringExtra("codigo") ?: "Nenhum"

        val txt = TextView(this).apply {
            text = "Código escaneado:\n$codigo\n\nBuscando preço no servidor..."
            textSize = 24f
            setPadding(50, 100, 50, 50)
        }

        setContentView(txt)

        // Busca o preço
        Thread {
            try {
                val url = URL("http://10.10.56.103/unitario.php?codigo=$codigo")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                val html = conn.inputStream.bufferedReader().readText()

                runOnUiThread {
                    txt.text = "Código: $codigo\n\nResposta do servidor:\n\n${html.take(500)}"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    txt.text = "Código: $codigo\n\nErro: ${e.message}"
                }
            }
        }.start()
    }
}
