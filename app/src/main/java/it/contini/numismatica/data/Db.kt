package it.contini.numismatica.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local storage: the collection plus caches of Numista answers (to save API requests). */
class Db(context: Context) : SQLiteOpenHelper(context, "numismatica.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE owned (
                type_id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                category TEXT NOT NULL,
                issuer_code TEXT NOT NULL,
                issuer_name TEXT NOT NULL,
                min_year INTEGER,
                max_year INTEGER,
                thumb TEXT,
                composition TEXT,
                demonetized INTEGER,
                quantity INTEGER NOT NULL DEFAULT 1,
                grade TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                issue_year TEXT NOT NULL DEFAULT '',
                added_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE cache (url TEXT PRIMARY KEY, body TEXT NOT NULL, fetched_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // First version: nothing to migrate yet.
    }

    // ---------- cache ----------

    fun cached(url: String, maxAgeMs: Long): String? =
        readableDatabase.rawQuery(
            "SELECT body FROM cache WHERE url = ? AND fetched_at > ?",
            arrayOf(url, (System.currentTimeMillis() - maxAgeMs).toString())
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun putCache(url: String, body: String) {
        val v = ContentValues().apply {
            put("url", url); put("body", body); put("fetched_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- collection ----------

    fun allOwned(): List<OwnedItem> =
        readableDatabase.rawQuery("SELECT * FROM owned ORDER BY issuer_name COLLATE NOCASE, min_year, title", null)
            .use { c -> buildList { while (c.moveToNext()) add(c.toOwned()) } }

    fun upsertOwned(item: OwnedItem) {
        writableDatabase.insertWithOnConflict("owned", null, item.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteOwned(typeId: Long) {
        writableDatabase.delete("owned", "type_id = ?", arrayOf(typeId.toString()))
    }

    fun replaceAllOwned(items: List<OwnedItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("owned", null, null)
            items.forEach { db.insertWithOnConflict("owned", null, it.toValues(), SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun OwnedItem.toValues() = ContentValues().apply {
        put("type_id", typeId)
        put("title", title)
        put("category", category)
        put("issuer_code", issuerCode)
        put("issuer_name", issuerName)
        if (minYear != null) put("min_year", minYear) else putNull("min_year")
        if (maxYear != null) put("max_year", maxYear) else putNull("max_year")
        put("thumb", thumb)
        put("composition", composition)
        if (demonetized != null) put("demonetized", if (demonetized) 1 else 0) else putNull("demonetized")
        put("quantity", quantity)
        put("grade", grade)
        put("notes", notes)
        put("issue_year", issueYear)
        put("added_at", addedAt)
    }

    private fun Cursor.toOwned(): OwnedItem {
        fun s(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
        fun i(col: String) = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getInt(it) }
        return OwnedItem(
            typeId = getLong(getColumnIndexOrThrow("type_id")),
            title = s("title") ?: "",
            category = s("category") ?: "",
            issuerCode = s("issuer_code") ?: "",
            issuerName = s("issuer_name") ?: "",
            minYear = i("min_year"),
            maxYear = i("max_year"),
            thumb = s("thumb"),
            composition = s("composition"),
            demonetized = i("demonetized")?.let { it == 1 },
            quantity = i("quantity") ?: 1,
            grade = s("grade") ?: "",
            notes = s("notes") ?: "",
            issueYear = s("issue_year") ?: "",
            addedAt = getLong(getColumnIndexOrThrow("added_at")),
        )
    }
}
