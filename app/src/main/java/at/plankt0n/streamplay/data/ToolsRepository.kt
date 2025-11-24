package at.plankt0n.streamplay.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor

class ToolsRepository(context: Context) {
    private val dbHelper = ToolsDatabaseHelper(context.applicationContext)

    fun getAllTools(): List<Tool> {
        val db = dbHelper.readableDatabase
        val cursor = db.query("tools", arrayOf("id", "order_number", "description"), null, null, null, null, "order_number ASC")
        return cursor.use { generateSequence { if (it.moveToNext()) it else null }
            .map { cursorToTool(it) }
            .toList()
        }
    }

    fun getTool(id: Long): Tool? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "tools",
            arrayOf("id", "order_number", "description"),
            "id=?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return cursor.use { if (it.moveToFirst()) cursorToTool(it) else null }
    }

    fun insertTool(orderNumber: String, description: String): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("order_number", orderNumber)
            put("description", description)
        }
        return db.insert("tools", null, values)
    }

    fun updateTool(id: Long, orderNumber: String, description: String): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("order_number", orderNumber)
            put("description", description)
        }
        return db.update("tools", values, "id=?", arrayOf(id.toString()))
    }

    private fun cursorToTool(cursor: Cursor): Tool {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
        val orderNumber = cursor.getString(cursor.getColumnIndexOrThrow("order_number"))
        val description = cursor.getString(cursor.getColumnIndexOrThrow("description"))
        return Tool(id, orderNumber, description)
    }
}

data class Tool(val id: Long, val orderNumber: String, val description: String)
