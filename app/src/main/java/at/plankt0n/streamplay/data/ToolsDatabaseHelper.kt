package at.plankt0n.streamplay.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ToolsDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    context.getDatabasePath("appdata/tools.db").path,
    null,
    DATABASE_VERSION
) {

    init {
        val dbFile = context.getDatabasePath("appdata/tools.db")
        dbFile.parentFile?.mkdirs()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tools (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_number TEXT NOT NULL,
                description TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < DATABASE_VERSION) {
            db.execSQL("DROP TABLE IF EXISTS tools")
            onCreate(db)
        }
    }

    companion object {
        private const val DATABASE_VERSION = 1
    }
}
