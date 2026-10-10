package com.quickprice.app

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val SERVIDOR = "10.72.6.23"

    private val caminhos = listOf(
        "/",
        "/index.html",
        "/index.php",
        "/home.html",
        "/home.php",
        "/menu.html",
        "/menu.php",
        "/produto.html",
        "/produto.php",
        "/produtos.html",
        "/produtos.php",
        "/busca.html",
        "/busca.php",
        "/consulta.html",
        "/consulta.php",
        "/preco.html",
        "/preco.php",
        "/precos.html",
        "/precos.php",
        "/api/produto.php",
        "/api/produtos.php",
        "/api/busca.php",
        "/api/consulta.php",
        "/api/preco.php",
        "/sistema/",
        "/sistema/index.php",
        "/cartaz/",
        "/cartaz/index.php",
        "/consulta/index.php",
        "/preco/index.php",
        "/sweda/",
        "/sweda/index.php",
        "/ecd/",
        "/ecd/index.php"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(30, 60, 30, 30)
        }

        val txtStatus = TextView(this).apply {
            text = "Toque para descobrir caminhos no servidor"
            textSize = 18f
            setPadding(0, 0, 0, 20)
        }

        val btnTestar = Button(this).apply {
            text = "DESCOBRIR CAMINHOS"
            setOnClickListener {
                txtResultado.text = ""
                descobrir(txtStatus)
            }
        }

        val scrollView = ScrollView(this)
        txtResultado = TextView(this).apply {
            text = ""
            textSize = 14f
            setPadding(20, 20, 20, 20)
        }
        scrollView.addView(txtResultado)

        layout.addView(txtStatus)
        layout.addView(btnTestar)
        layout.addView(scrollView)

        setContentView(layout)
    }

    private lateinit var txtResultado: TextView

    private fun descobrir(txtStatus: TextView) {
        Thread {
            var encontrados = 0

            for (caminho in caminhos) {
                val url = "http://" + SERVIDOR + caminho
                try {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    val code = conn.responseCode

                    if (code == 200) {
                        encontrados++
                        val html = conn.inputStream.bufferedReader().readText()
                        val titulo = if (html.length > 100) html.substring(0, 100) else html

                        runOnUiThread {
                            txtResultado.append("ENCONTRADO: " + caminho + "
")
                            txtResultado.append("Status: " + code + "
")
                            txtResultado.append("Preview: " + titulo + "

")
                        }
                    }
                } catch (e: Exception) {
                    // Ignora erro
                }

                runOnUiThread {
                    txtStatus.text = "Testando: " + caminho
                }
            }

            runOnUiThread {
                txtStatus.text = "Concluido! Encontrados: " + encontrados
                if (encontrados == 0) {
                    txtResultado.text = "Nenhum caminho encontrado. Servidor pode nao ter pagina web."
                }
            }
        }.start()
    }
}
