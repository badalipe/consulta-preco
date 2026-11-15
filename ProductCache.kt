package com.quickprice.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Cache local aprendido a partir de pesquisas bem-sucedidas do Cartaz Fácil. */
class ProductCache(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    data class Entry(
        val barcode: String,
        val productCode: String,
        val name: String,
        val family: String,
        val imageUrl: String
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE_PRODUCTS (
                barcode TEXT PRIMARY KEY NOT NULL,
                product_code TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL DEFAULT '',
                family TEXT NOT NULL DEFAULT '',
                image_url TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // A versão atual não requer migrações. Se o esquema mudar, preservar os registos existentes.
    }

    fun find(barcode: String): Entry? {
        readableDatabase.query(
            TABLE_PRODUCTS,
            arrayOf("barcode", "product_code", "name", "family", "image_url"),
            "barcode = ?",
            arrayOf(barcode),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return Entry(
                barcode = cursor.getString(0).orEmpty(),
                productCode = cursor.getString(1).orEmpty(),
                name = cursor.getString(2).orEmpty(),
                family = cursor.getString(3).orEmpty(),
                imageUrl = cursor.getString(4).orEmpty()
            )
        }
    }

    fun saveProduct(barcode: String, productCode: String, name: String, family: String) {
        val previous = find(barcode)
        save(
            barcode = barcode,
            productCode = productCode.ifBlank { previous?.productCode.orEmpty() },
            name = name.ifBlank { previous?.name.orEmpty() },
            family = family.ifBlank { previous?.family.orEmpty() },
            imageUrl = previous?.imageUrl.orEmpty()
        )
    }

    fun saveImage(barcode: String, imageUrl: String) {
        val previous = find(barcode)
        save(
            barcode = barcode,
            productCode = previous?.productCode.orEmpty(),
            name = previous?.name.orEmpty(),
            family = previous?.family.orEmpty(),
            imageUrl = imageUrl.ifBlank { previous?.imageUrl.orEmpty() }
        )
    }

    fun saveDiscoveredName(barcode: String, name: String) {
        if (name.isBlank()) return
        val previous = find(barcode)
        save(
            barcode = barcode,
            productCode = previous?.productCode.orEmpty(),
            name = previous?.name?.takeIf { it.isNotBlank() } ?: name,
            family = previous?.family.orEmpty(),
            imageUrl = previous?.imageUrl.orEmpty()
        )
    }

    private fun save(barcode: String, productCode: String, name: String, family: String, imageUrl: String) {
        val values = ContentValues().apply {
            put("barcode", barcode)
            put("product_code", productCode)
            put("name", name)
            put("family", family)
            put("image_url", imageUrl)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(TABLE_PRODUCTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        private const val DATABASE_NAME = "consulta_preco_products.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_PRODUCTS = "products"
    }
}
