package com.tingyun.smartmistakebook.feature.tutor

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Holds the actual local-file read so tests can inspect the UI before a new image is decoded. */
class ControlledImageProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val fixture = fixtures[uri.lastPathSegment] ?: throw FileNotFoundException()
        fixture.readStarted.set(true)
        if (!fixture.release.await(15, TimeUnit.SECONDS)) throw FileNotFoundException("Fixture timed out")
        return ParcelFileDescriptor.open(fixture.file, ParcelFileDescriptor.MODE_READ_ONLY,
            Handler(Looper.getMainLooper())) { fixture.readsClosed.incrementAndGet() }
    }

    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?) = 0

    class Fixture(val file: File, blocked: Boolean) {
        val readStarted = AtomicBoolean(false)
        val readsClosed = AtomicInteger(0)
        val release = CountDownLatch(if (blocked) 1 else 0)
    }

    companion object {
        private val fixtures = ConcurrentHashMap<String, Fixture>()

        fun register(id: String, file: File, blocked: Boolean = false): Pair<String, Fixture> {
            val fixture = Fixture(file, blocked)
            fixtures[id] = fixture
            return "content://com.tingyun.smartmistakebook.feature.tutor.test.uiimages/$id" to fixture
        }

        fun clear() {
            fixtures.values.forEach { it.release.countDown() }
            fixtures.clear()
        }
    }
}
