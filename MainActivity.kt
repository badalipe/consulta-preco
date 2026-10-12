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

        val btnScan = findViewById<Button>(R.id.btnScan)
        btnScan.setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 100)
        }

        val btnVoltar = findViewById<TextView>(R.id.btnVoltar)
        btnVoltar.setOnClickListener {
            val txtNome = findViewById<TextView>(R.id.txtNome)
            txtNome.text = "OK"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val codigo = data?.getStringExtra("codigo")
            codigo?.let {
                val txtCodigo = findViewById<TextView>(R.id.txtCodigo)
                txtCodigo.text = it
            }
        }
    }
}
