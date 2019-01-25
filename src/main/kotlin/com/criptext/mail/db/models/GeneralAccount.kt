package com.criptext.mail.db.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/**
 * Created by sebas on 2/6/18.
 */

@Entity(tableName = "account", indices = [Index(value = "email")] )
class GeneralAccount(

        @PrimaryKey
        @ColumnInfo(name = "email")
        var email : String,

        @ColumnInfo(name = "avatar")
        var avatar : String,

        @ColumnInfo(name = "isActive")
        var isActive: Boolean
)
