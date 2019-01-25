package com.criptext.mail.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.util.Log
import com.criptext.mail.db.dao.*
import com.criptext.mail.db.dao.signal.RawIdentityKeyDao
import com.criptext.mail.db.dao.signal.RawPreKeyDao
import com.criptext.mail.db.dao.signal.RawSessionDao
import com.criptext.mail.db.dao.signal.RawSignedPreKeyDao
import com.criptext.mail.db.models.*
import com.criptext.mail.db.models.signal.CRIdentityKey
import com.criptext.mail.db.models.signal.CRPreKey
import com.criptext.mail.db.models.signal.CRSessionRecord
import com.criptext.mail.db.models.signal.CRSignedPreKey
import com.criptext.mail.db.typeConverters.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.criptext.mail.utils.EmailAddressUtils
import com.criptext.mail.utils.sha256
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import java.io.File
import java.util.*


/**
 * Created by sebas on 1/24/18.
 */

@Database(entities = [GeneralAccount::class],
        version = 1,
        exportSchema = false)
@TypeConverters(
        BooleanConverter::class)
abstract class AccountsDatabase : RoomDatabase() {
    abstract fun accountDao(): GeneralAccountDao
    companion object {
        private var INSTANCE : AccountsDatabase? = null

        fun getAppDatabase(context: Context): AccountsDatabase {
            if(INSTANCE == null){
                INSTANCE = Room.databaseBuilder(context,
                        AccountsDatabase::class.java,
                        "criptextAccountDB")
                        .openHelperFactory(RequerySQLiteOpenHelperFactory())
                        .build()
            }
            return INSTANCE!!
        }
    }
}
