package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Vai direto para o scanner
        startActivity(Intent(this, ScannerActivity::class.java))
        finish()
    }
}
