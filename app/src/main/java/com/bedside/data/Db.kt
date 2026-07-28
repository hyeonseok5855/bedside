package com.bedside.data

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * 암호화된 Room DB 싱글턴 제공. SQLCipher를 openHelperFactory로 물린다(결정 15).
 * 암호화는 v1 기반이라 처음부터 켠다 — 나중에 넣으면 마이그레이션 지옥이다.
 */
object Db {

    @Volatile
    private var instance: BedsideDatabase? = null

    fun get(context: Context): BedsideDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(context: Context): BedsideDatabase {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(DbKey.getOrCreate(context))
        return Room.databaseBuilder(context, BedsideDatabase::class.java, "bedside.db")
            .openHelperFactory(factory)
            .build()
    }
}
