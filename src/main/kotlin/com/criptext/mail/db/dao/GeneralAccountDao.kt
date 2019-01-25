package com.criptext.mail.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.criptext.mail.db.models.GeneralAccount

/**
 * Created by sebas on 3/3/18.
 */


@Dao
interface GeneralAccountDao {

    @Insert
    fun insertAll(accounts : List<GeneralAccount>)

    @Insert
    fun insert(account :GeneralAccount)

    @Query("SELECT * FROM account")
    fun getAll() : List<GeneralAccount>

    @Query("SELECT * FROM account WHERE email=:email AND isActive = 1")
    fun getLoggedInAccount(email: String) : GeneralAccount?

    @Query("DELETE FROM account")
    fun nukeTable()

    @Delete
    fun deleteAll(accounts: List<GeneralAccount>)

}
