package com.criptext.mail.scenes.settings.recovery_email

import android.view.ViewGroup
import com.criptext.mail.BaseActivity
import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.bgworker.AsyncTaskWorkRunner
import com.criptext.mail.db.AppDatabase
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.settings.recovery_email.data.RecoveryEmailDataSource
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.generaldatasource.data.GeneralDataSource

class RecoveryEmailActivity: BaseActivity(){

    override val layoutId = R.layout.activity_recovery_email
    override val toolbarId = R.id.mailbox_toolbar

    override fun initController(receivedModel: Any): SceneController {
        val model = receivedModel as RecoveryEmailModel
        val view = findViewById<ViewGroup>(R.id.main_content)
        val scene = RecoveryEmailScene.Default(view)
        val appDB = AppDatabase.getAppDatabase(this)
        val dataSource = RecoveryEmailDataSource(
                httpClient = HttpClient.Default(),
                activeAccount = ActiveAccount.loadFromStorage(this)!!,
                runner = AsyncTaskWorkRunner(),
                storage = KeyValueStorage.SharedPrefs(this))
        val generalDataSource = GeneralDataSource(
                storage = KeyValueStorage.SharedPrefs(this),
                db = appDB,
                runner = AsyncTaskWorkRunner(),
                activeAccount = ActiveAccount.loadFromStorage(this)!!,
                httpClient = HttpClient.Default()
        )
        return RecoveryEmailController(
                model = model,
                scene = scene,
                generalDataSource = generalDataSource,
                dataSource = dataSource,
                keyboardManager = KeyboardManager(this),
                storage = KeyValueStorage.SharedPrefs(this),
                activeAccount = ActiveAccount.loadFromStorage(this)!!,
                host = this)
    }

}