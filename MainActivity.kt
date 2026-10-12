package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val SERVER = "http://10.10.254.73:8343"

    private lateinit var edtProxyIP: EditText
    private lateinit var edtProxyPorta: EditText
    private lateinit var edtProxyUser: EditText
    private lateinit var edtProxyPass: EditText
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout simples
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        txtStatus = TextView(this).apply {
            text = "Configure o Proxy"
            textSize = 20f
            setPadding(0, 0, 0, 30)
        }

        edtProxyIP = EditText(this).apply {
            hint = "Proxy IP (ex: 1.1.1.1)"
            setText("1.1.1.1")
        }

        edtProxyPorta = EditText(this).apply {
            hint = "Proxy Porta (ex: 3720)"
            setText("3720")
        }

        edtProxyUser = EditText(this).apply {
            hint = "Usuario Proxy"
        }

        edtProxyPass = EditText(this).apply {
            hint = "Senha Proxy"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val btnTestar = Button(this).apply {
            text = "TESTAR COM PROXY"
            setOnClickListener {
                testarComProxy()
            }
        }

        val btnScan = Button(this).apply {
            text = "ABRIR SCANNER"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ScannerActivity::class.java))
            }
        }

        layout.addView(txtStatus)
        layout.addView(edtProxyIP)
        layout.addView(edtProxyPorta)
        layout.addView(edtProxyUser)
        layout.addView(edtProxyPass)
        layout.addView(btnTestar)
        layout.addView(btnScan)

        setContentView(layout)
    }

    private fun testarComProxy() {
        val proxyIP = edtProxyIP.text.toString()
        val proxyPorta = edtProxyPorta.text.toString().toIntOrNull() ?: 3720
        val proxyUser = edtProxyUser.text.toString()
        val proxyPass = edtProxyPass.text.toString()

        txtStatus.text = "Testando com proxy " + proxyIP + ":" + proxyPorta + "..."

        // Configura autenticacao do proxy
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(proxyUser, proxyPass.toCharArray())
            }
        })

        Thread {
            try {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyIP, proxyPorta))
                val url = URL(SERVER + "/SMProdutosAPI/api/v4/produtos/precos-produtos?nroEmpresa=4")
                val conn = url.openConnection(proxy) as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.setRequestProperty("Accept", "application/json")

                val code = conn.responseCode
                val response = conn.inputStream.bufferedReader().readText()

                runOnUiThread {
                    if (code == 200) {
                        txtStatus.text = "PROXY OK! API respondeu!"
                        Toast.makeText(this, "Conectado via proxy!", Toast.LENGTH_LONG).show()
                    } else {
                        txtStatus.text = "Proxy respondeu: " + code
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    txtStatus.text = "Erro: " + e.message
                }
            }
        }.start()
    }
}
