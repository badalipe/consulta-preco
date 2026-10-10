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

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        val txtStatus = TextView(this).apply {
            text = "Toque para varrer as redes"
            textSize = 18f
            setPadding(0, 0, 0, 30)
        }

        val txtRede1 = TextView(this).apply {
            text = "Rede 1: 10.71.6.X"
            textSize = 16f
            setPadding(0, 0, 0, 10)
        }

        val txtRede2 = TextView(this).apply {
            text = "Rede 2: 10.10.254.X"
            textSize = 16f
            setPadding(0, 0, 0, 30)
        }

        val txtResultado = TextView(this).apply {
            text = ""
            textSize = 24f
            setTextColor(0xFF00FF00.toInt())
            setPadding(0, 40, 0, 0)
        }

        val btnVarredura = Button(this).apply {
            text = "VARRER AMBAS REDES"
            setOnClickListener {
                txtStatus.text = "Varrendo..."
                txtResultado.text = ""
                varrerRedes(txtStatus, txtResultado)
            }
        }

        layout.addView(txtStatus)
        layout.addView(txtRede1)
        layout.addView(txtRede2)
        layout.addView(btnVarredura)
        layout.addView(txtResultado)

        setContentView(layout)
    }

    private fun varrerRedes(txtStatus: TextView, txtResultado: TextView) {
        Thread {
            var encontrado = false

            // Varre rede 1: 10.71.6.X
            runOnUiThread {
                txtStatus.text = "Varrendo rede 10.71.6.X..."
            }

            for (i in 1..254) {
                if (testarIP("10.71.6." + i)) {
                    encontrado = true
                    runOnUiThread {
                        txtResultado.text = "ENCONTRADO: 10.71.6." + i
                        txtStatus.text = "Servidor encontrado!"
                    }
                    break
                }
                if (i % 50 == 0) {
                    runOnUiThread {
                        txtStatus.text = "Rede 1: " + i + "/254..."
                    }
                }
            }

            // Se não encontrou, varre rede 2: 10.10.254.X
            if (!encontrado) {
                runOnUiThread {
                    txtStatus.text = "Varrendo rede 10.10.254.X..."
                }

                for (i in 1..254) {
                    if (testarIP("10.10.254." + i)) {
                        encontrado = true
                        runOnUiThread {
                            txtResultado.text = "ENCONTRADO: 10.10.254." + i
                            txtStatus.text = "Servidor encontrado!"
                        }
                        break
                    }
                    if (i % 50 == 0) {
                        runOnUiThread {
                            txtStatus.text = "Rede 2: " + i + "/254..."
                        }
                    }
                }
            }

            if (!encontrado) {
                runOnUiThread {
                    txtStatus.text = "Nao encontrado em nenhuma rede"
                    txtResultado.text = "Tente outros IPs manualmente"
                }
            }
        }.start()
    }

    private fun testarIP(ip: String): Boolean {
        return try {
            val url = URL("http://" + ip + "/unitario.php")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 300
            conn.readTimeout = 300
            val code = conn.responseCode
            code == 200
        } catch (e: Exception) {
            false
        }
    }
}
