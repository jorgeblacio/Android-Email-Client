package com.criptext.mail.db.dao

import androidx.room.*
import com.criptext.mail.db.models.Account
import com.criptext.mail.db.models.Label
import com.criptext.mail.db.models.signal.CRPreKey
import com.criptext.mail.db.models.signal.CRSignedPreKey

/**
 * Data Access Object to be used exclusively by the Sign Up Scene.
 * Created by gabriel on 4/23/18.
 */
@Dao
interface SignUpDao {

    @Insert
    fun insertLabels(labels: List<Label>)

    @Insert
    fun saveAccount(account : Account)

    @Insert
    fun insertPreKeys(preKeys : List<CRPreKey>)

    @Insert
    fun insertSignedPreKey(crSignedPreKey: CRSignedPreKey)

    @Transaction
    fun insertNewAccountData(account: Account, preKeyList: List<CRPreKey>,
                             signedPreKey: CRSignedPreKey, defaultLabels: List<Label>,
                             extraRegistrationSteps: Runnable, accountDao: AccountDao) {
        saveAccount(account)
        val savedAccount = accountDao.getLoggedInAccount()!!
        preKeyList.forEach { it.accountId = savedAccount.id }
        insertPreKeys(preKeyList)
        signedPreKey.accountId = savedAccount.id
        insertSignedPreKey(signedPreKey)
        insertLabels(defaultLabels)
        // execute extra steps here, so that if they fail, we can rollback
        extraRegistrationSteps.run()
    }

    @Transaction
    fun updateAccountData(account: Account, preKeyList: List<CRPreKey>,
                             signedPreKey: CRSignedPreKey,
                             extraRegistrationSteps: Runnable, accountDao: AccountDao) {
        saveAccount(account)
        val savedAccount = accountDao.getLoggedInAccount()!!
        preKeyList.forEach { it.accountId = savedAccount.id }
        insertPreKeys(preKeyList)
        signedPreKey.accountId = savedAccount.id
        insertSignedPreKey(signedPreKey)
        // execute extra steps here, so that if they fail, we can rollback
        extraRegistrationSteps.run()
    }

}