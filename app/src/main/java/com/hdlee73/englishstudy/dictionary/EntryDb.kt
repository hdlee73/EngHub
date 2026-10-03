package com.hdlee73.englishstudy.dictionary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** The saved vocabulary list (sajeon.db). Separate from the read-only bundled dictionaries. */
class EntryDb(context: Context) : SQLiteOpenHelper(context, "sajeon.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, word TEXT NOT NULL UNIQUE COLLATE NOCASE, ipa TEXT, korean TEXT, english TEXT, examples TEXT, source TEXT DEFAULT '')")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("UPDATE entries SET ipa=''")
        if (oldVersion < 3) db.execSQL("ALTER TABLE entries ADD COLUMN source TEXT DEFAULT ''")
    }
    fun all(): List<WordEntry> {
        val out = mutableListOf<WordEntry>()
        readableDatabase.rawQuery("SELECT id,word,ipa,korean,english,examples,source FROM entries ORDER BY word COLLATE NOCASE", null).use { c ->
            while (c.moveToNext()) out += WordEntry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6).orEmpty())
        }
        return out
    }
    fun save(original: WordEntry): Boolean {
        val e = original.studyVersion()
        val v = ContentValues().apply { put("word", e.word); put("ipa", e.ipa); put("korean", e.korean); put("english", e.english); put("examples", e.examples); put("source", e.source) }
        return writableDatabase.insertWithOnConflict("entries", null, v, SQLiteDatabase.CONFLICT_REPLACE) >= 0
    }
    fun updateExamples(before: WordEntry, after: WordEntry) {
        val old = before.studyVersion()
        val updated = after.studyVersion()
        val values = ContentValues().apply { put("examples", updated.examples) }
        writableDatabase.update("entries", values,
            "word=? COLLATE NOCASE AND examples=? AND korean=? AND english=?",
            arrayOf(old.word, old.examples, old.korean, old.english))
    }
    fun delete(id: Long) { writableDatabase.delete("entries", "id=?", arrayOf(id.toString())) }
}
