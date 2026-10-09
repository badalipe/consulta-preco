package com.quickprice.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout simples: Botão testar + Botão escanear + Status
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
        }

        val txtStatus = TextView(this).apply {
            text = "Testando conexão com servidor..."
            textSize = 20f
        }

        val btnTestar = Button(this).apply {
            text = "TESTAR CONEXÃO"
            setOnClickListener {
                txtStatus.text = "Testando..."
                Thread {
                    try {
                        val url = URL("http://10.10.56.103/unitario.php")
                        val conn = url.openConnection() as HttpURLConnection
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        val code = conn.responseCode
                        runOnUiThread {
                            txtStatus.text = "✅ CONECTADO! Código: $code"
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            txtStatus.text = "❌ ERRO: ${e.message}"
                        }
                    }
                }.start()
            }
        }

        val btnScan = Button(this).apply {
            text = "ESCANEAR CÓDIGO"
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, ScannerActivity::class.java))
            }
        }

        layout.addView(txtStatus)
        layout.addView(btnTestar)
        layout.addView(btnScan)
        setContentView(layout)

        // Testa automaticamente ao abrir
        btnTestar.performClick()
    }
}
