package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var txtStatus: TextView
    private lateinit var txtResultado: TextView
    private lateinit var edtIP: EditText

    // IPs conhecidos do servidor TOTVS
    private val ipsConhecidos = listOf(
        "10.10.254.212",
        "10.10.254.241", 
        "10.10.254.81",
        "10.10.254.73",
        "10.10.56.103",
        "10.72.6.23"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout simples
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        txtStatus = TextView(this).apply {
            text = "Escaneando redes..."
            textSize = 20f
            setPadding(0, 0, 0, 30)
        }

        txtResultado = TextView(this).apply {
            text = ""
            textSize = 24f
            setTextColor(0xFF00FF00.toInt())
            setPadding(0, 40, 0, 0)
        }

        edtIP = EditText(this).apply {
            hint = "Ou digite IP manual"
            setText("10.10.254.212")
        }

        val btnTestar = Button(this).apply {
            text = "TESTAR IP DIGITADO"
            setOnClickListener {
                testarIP(edtIP.text.toString())
            }
        }

        val btnVarredura = Button(this).apply {
            text = "VARRER TODAS REDES"
            setOnClickListener {
                varrerTodasRedes()
            }
        }

        val btnContinuar = Button(this).apply {
            text = "CONTINUAR PARA O APP"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ScannerActivity::class.java))
            }
        }

        layout.addView(txtStatus)
        layout.addView(edtIP)
        layout.addView(btnTestar)
        layout.addView(btnVarredura)
        layout.addView(btnContinuar)
        layout.addView(txtResultado)

        setContentView(layout)

        // Inicia varredura automatica
        varrerTodasRedes()
    }

    private fun getMeuIP(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val ip = addr.hostAddress
                        if (ip.indexOf(':') < 0 && !ip.startsWith("127")) {
                            return ip
                        }
                    }
                }
            }
            "Nao encontrado"
        } catch (e: Exception) {
            "Erro"
        }
    }

    private fun testarIP(ip: String) {
        txtStatus.text = "Testando " + ip + "..."

        Thread {
            val portas = listOf(80, 443, 63784, 8080, 3000)
            var encontrou = false

            for (porta in portas) {
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(ip, porta), 2000)
                    socket.close()

                    runOnUiThread {
                        txtResultado.text = "ENCONTRADO!\n" + ip + ":" + porta
                        txtStatus.text = "Servidor OK!"
                    }
                    encontrou = true
                    break
                } catch (e: Exception) {
                    // Tenta proxima porta
                }
            }

            if (!encontrou) {
                runOnUiThread {
                    txtStatus.text = "Nao encontrou servidor em " + ip
                }
            }
        }.start()
    }

    private fun varrerTodasRedes() {
        val meuIP = getMeuIP()
        txtStatus.text = "Meu IP: " + meuIP + "\nVarrendo IPs conhecidos..."

        Thread {
            // Testa IPs conhecidos primeiro
            for (ip in ipsConhecidos) {
                runOnUiThread {
                    txtStatus.text = "Testando " + ip + "..."
                }

                val portas = listOf(63784, 80, 443, 8080)
                for (porta in portas) {
                    try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress(ip, porta), 1000)
                        socket.close()

                        runOnUiThread {
                            txtResultado.text = "SERVIDOR ENCONTRADO!\n" + ip + ":" + porta
                            txtStatus.text = "Pronto! Toque em CONTINUAR"
                        }
                        return@Thread
                    } catch (e: Exception) {
                        // Continua
                    }
                }
            }

            // Se nao encontrou, varre rede local
            val partes = meuIP.split(".")
            if (partes.size == 4) {
                val base = partes[0] + "." + partes[1] + "." + partes[2]

                for (i in 1..254) {
                    val ip = base + "." + i

                    runOnUiThread {
                        txtStatus.text = "Varrendo " + ip + "..."
                    }

                    try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress(ip, 63784), 500)
                        socket.close()

                        runOnUiThread {
                            txtResultado.text = "ENCONTRADO NA REDE LOCAL!\n" + ip + ":63784"
                            txtStatus.text = "Pronto!"
                        }
                        return@Thread
                    } catch (e: Exception) {
                        // Continua
                    }
                }
            }

            runOnUiThread {
                txtStatus.text = "Nenhum servidor encontrado\nToque em CONTINUAR para usar o app"
            }
        }.start()
    }
}
