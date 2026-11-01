package com.tingyun.smartmistakebook.feature.library

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/** Counts actual local-file opens, including the decoder's bounds and bitmap reads. */
class ImageReadBudgetProvider : ContentProvider() {
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read only")
        val id = uri.lastPathSegment ?: throw FileNotFoundException()
        val file = fixtures[id] ?: throw FileNotFoundException()
        opened.add(id)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private val fixtures = ConcurrentHashMap<String, File>()
        private val opened = ConcurrentHashMap.newKeySet<String>()
        fun register(id: String, file: File): String {
            fixtures[id] = file
            return "content://com.tingyun.smartmistakebook.feature.library.test.imagebudget/$id"
        }
        fun openedIds(): Set<String> = opened.toSet()
        fun clear() { fixtures.clear(); opened.clear() }
    }
}
