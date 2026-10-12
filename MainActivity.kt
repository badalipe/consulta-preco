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
            startActivity(intent)
        }

        val btnVoltar = findViewById<TextView>(R.id.btnVoltar)
        btnVoltar.setOnClickListener {
            val txtNome = findViewById<TextView>(R.id.txtNome)
            txtNome.text = "OK"
        }
    }
}
