package com.criptext.mail.utils

import com.criptext.mail.db.AccountTypes
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.Account
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.db.typeConverters.AccountTypeConverter

object AccountUtils {
    fun setUserAsActiveAccount(user: Account, storage: KeyValueStorage): ActiveAccount{
        val activeAccount = ActiveAccount(id = user.id, name = user.name, recipientId = user.recipientId,
                deviceId = user.deviceId, jwt = user.jwt, signature = user.signature, refreshToken = user.refreshToken,
                domain = user.domain, type = user.type)
        storage.putString(KeyValueStorage.StringKey.ActiveAccount,
                activeAccount.toJSON().toString())
        return activeAccount
    }

    fun getLastLoggedAccounts(storage: KeyValueStorage): MutableList<String> {
        val storageLastUser = storage.getString(KeyValueStorage.StringKey.LastLoggedUser, "")
        return if(storageLastUser.isEmpty()) mutableListOf() else
            storageLastUser.split(",").map { it.trim() }.toMutableList()
    }

    fun getFrequencyPeriod(period: Int): Long {
        return when(period){
            1 -> 86400000L * 7L
            2 -> 86400000L * 30L
            else -> 86400000L
        }
    }

    fun isNotPlus(accountType: AccountTypes): Boolean {
        return accountType == AccountTypes.STANDARD
                || accountType == AccountTypes.LUCKY
    }

    fun getAccountTypeFromInt(ordinal: Int): AccountTypes {
        val converter = AccountTypeConverter()
        return converter.getAccountType(ordinal)
    }
}