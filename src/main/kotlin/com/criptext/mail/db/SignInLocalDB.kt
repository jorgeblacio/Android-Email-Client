package com.criptext.mail.db

import android.content.Context
import com.criptext.mail.utils.EmailUtils
import java.io.File

/**
 * Created by sebas on 2/15/18.
 */


interface SignInLocalDB {
    fun login(): Boolean
    fun accountExistsLocally(username: String): Boolean
    fun deleteDatabase(user: String)
    fun deleteDatabase()

    class Default(private val db: AccountsDatabase, private val filesDir: File): SignInLocalDB {

        override fun accountExistsLocally(username: String): Boolean {
            val account = db.accountDao().getLoggedInAccount(username)
            if(account == null) return false
            else if(account.email == username) return true
            return false
        }

        override fun login(): Boolean {
            TODO("LOGIN NOT IMPLEMENTED")
        }

        override fun deleteDatabase() {
            db.clearAllTables()
        }

        override fun deleteDatabase(user: String) {
            EmailUtils.deleteEmailsInFileSystem(filesDir, user)
            db.clearAllTables()
        }

    }

}
