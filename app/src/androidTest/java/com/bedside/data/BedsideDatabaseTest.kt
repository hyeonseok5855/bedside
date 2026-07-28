package com.bedside.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 암호화 Room DB의 DAO 왕복을 실기기에서 검증한다. 실제 bedside.db를 건드리지
 * 않도록 in-memory 인스턴스를 SQLCipher factory로 연다.
 */
@RunWith(AndroidJUnit4::class)
class BedsideDatabaseTest {

    private lateinit var db: BedsideDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory("bedside-test-passphrase".toByteArray())
        db = Room.inMemoryDatabaseBuilder(context, BedsideDatabase::class.java)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun events_insert_count_recentOrderedByOccurredDesc() = runBlocking {
        assertEquals(0, db.events().count())

        db.events().insert(CollectedEvent(source = "steps", type = "snapshot", value = "100", occurredAt = 1000, recordedAt = 1000))
        db.events().insert(CollectedEvent(source = "photo", type = "snapshot", value = "1장", occurredAt = 2000, recordedAt = 2000))

        assertEquals(2, db.events().count())

        val recent = db.events().recent(10)
        assertEquals(2, recent.size)
        assertEquals("photo", recent[0].source) // occurredAt 2000 먼저 (DESC)
        assertEquals("steps", recent[1].source)
    }

    @Test
    fun messages_forSession_filtersAndOrdersById() = runBlocking {
        db.messages().insert(Message(sessionDate = "2026-07-28", role = "assistant", text = "Q1", createdAt = 1))
        db.messages().insert(Message(sessionDate = "2026-07-28", role = "user", text = "A1", createdAt = 2))
        db.messages().insert(Message(sessionDate = "2026-07-29", role = "assistant", text = "다른 날", createdAt = 3))

        val session = db.messages().forSession("2026-07-28")
        assertEquals(2, session.size)
        assertEquals("Q1", session[0].text)
        assertEquals("A1", session[1].text)
    }
}
