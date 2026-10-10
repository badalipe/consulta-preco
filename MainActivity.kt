package com.quickprice.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        val txtStatus = TextView(this).apply {
            text = "Toque para varrer"
            textSize = 20f
            setPadding(0, 0, 0, 40)
        }

        val txtResultado = TextView(this).apply {
            text = ""
            textSize = 24f
            setTextColor(0xFF00FF00.toInt())
            setPadding(0, 40, 0, 0)
        }

        val btnVarredura = Button(this).apply {
            text = "VARRER REDE"
            setOnClickListener {
                txtStatus.text = "Varrendo..."
                txtResultado.text = ""
                varrer(txtStatus, txtResultado)
            }
        }

        layout.addView(txtStatus)
        layout.addView(btnVarredura)
        layout.addView(txtResultado)

        setContentView(layout)

        Thread {
            val ip = getIP()
            runOnUiThread {
                txtStatus.text = "Seu IP: " + ip
            }
        }.start()
    }

    private fun getIP(): String {
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

    private fun varrer(txtStatus: TextView, txtResultado: TextView) {
        val meuIP = getIP()
        if (meuIP == "Nao encontrado" || meuIP == "Erro") {
            txtStatus.text = "IP nao encontrado"
            return
        }

        val partes = meuIP.split(".")
        if (partes.size != 4) {
            txtStatus.text = "IP invalido"
            return
        }

        val base = partes[0] + "." + partes[1] + "." + partes[2]

        Thread {
            var achou = 0
            for (i in 1..254) {
                val ip = base + "." + i
                try {
                    val url = URL("http://" + ip + "/unitario.php")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 500
                    conn.readTimeout = 500
                    val code = conn.responseCode

                    if (code == 200) {
                        achou++
                        runOnUiThread {
                            txtResultado.text = "IP: " + ip
                            txtStatus.text = "ENCONTRADO!"
                        }
                        break
                    }
                } catch (e: Exception) {
                }

                if (i % 50 == 0) {
                    runOnUiThread {
                        txtStatus.text = "Varrendo " + i + "/254..."
                    }
                }
            }

            if (achou == 0) {
                runOnUiThread {
                    txtResultado.text = "Nao encontrado"
                }
            }
        }.start()
    }
}
