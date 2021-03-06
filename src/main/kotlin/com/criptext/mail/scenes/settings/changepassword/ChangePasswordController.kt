package com.criptext.mail.scenes.settings.changepassword

import com.criptext.mail.IHostActivity
import com.criptext.mail.R
import com.criptext.mail.api.models.DeviceInfo
import com.criptext.mail.api.models.SyncStatusData
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.ActivityMessage
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.params.MailboxParams
import com.criptext.mail.scenes.params.SignInParams
import com.criptext.mail.scenes.settings.changepassword.data.ChangePasswordDataSource
import com.criptext.mail.scenes.settings.changepassword.data.ChangePasswordRequest
import com.criptext.mail.scenes.settings.changepassword.data.ChangePasswordResult
import com.criptext.mail.scenes.signin.data.LinkStatusData
import com.criptext.mail.signal.SignalClient
import com.criptext.mail.signal.SignalStoreCriptext
import com.criptext.mail.utils.EmailAddressUtils
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.generaldatasource.data.GeneralDataSource
import com.criptext.mail.utils.generaldatasource.data.GeneralRequest
import com.criptext.mail.utils.generaldatasource.data.GeneralResult
import com.criptext.mail.utils.ui.data.DialogResult
import com.criptext.mail.utils.ui.data.DialogType
import com.criptext.mail.validation.FormInputState
import com.criptext.mail.websocket.WebSocketEventListener
import com.criptext.mail.websocket.WebSocketEventPublisher
import com.criptext.mail.websocket.WebSocketSingleton

class ChangePasswordController(
        private var activeAccount: ActiveAccount,
        private val model: ChangePasswordModel,
        private val scene: ChangePasswordScene,
        private val host: IHostActivity,
        private val storage: KeyValueStorage,
        private val keyboardManager: KeyboardManager,
        private var websocketEvents: WebSocketEventPublisher,
        private val generalDataSource: GeneralDataSource,
        private val dataSource: ChangePasswordDataSource)
    : SceneController(host, activeAccount, storage){

    val arePasswordsMatching: Boolean
        get() = model.passwordText == model.confirmPasswordText

    override val menuResourceId: Int? = null

    private val generalDataSourceListener: (GeneralResult) -> Unit = { result ->
        when(result) {
            is GeneralResult.ResetPassword -> onResetPassword(result)
            is GeneralResult.DeviceRemoved -> onDeviceRemovedRemotely(result)
            is GeneralResult.ConfirmPassword -> onPasswordChangedRemotely(result)
            is GeneralResult.LinkAccept -> onLinkAccept(result)
            is GeneralResult.SyncAccept -> onSyncAccept(result)
            is GeneralResult.ChangeToNextAccount -> onChangeToNextAccount(result)
        }
    }

    private val changePasswordUIObserver = object: ChangePasswordUIObserver(generalDataSource, host){

        override fun onSnackbarClicked() {

        }

        override fun onGeneralCancelButtonPressed(result: DialogResult) {

        }

        override fun onGeneralOkButtonPressed(result: DialogResult) {
            when(result){
                is DialogResult.DialogConfirmation -> {
                    when(result.type){
                        is DialogType.SwitchAccount -> {
                            generalDataSource.submitRequest(GeneralRequest.ChangeToNextAccount())
                        }
                        is DialogType.SignIn ->
                            host.goToScene(SignInParams(true), true)
                    }
                }
            }
        }

        override fun onOkButtonPressed(password: String) {
            generalDataSource.submitRequest(GeneralRequest.ConfirmPassword(password))
        }

        override fun onCancelButtonPressed() {
            generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(true))
        }

        override fun onForgotPasswordPressed() {
            generalDataSource.submitRequest(GeneralRequest.ResetPassword(activeAccount.recipientId,
                    EmailAddressUtils.extractEmailAddressDomain(activeAccount.userEmail)))
        }

        override fun onBackButtonPressed() {
            keyboardManager.hideKeyboard()
            host.finishScene()
        }

        override fun onOldPasswordChangedListener(password: String) {
            model.oldPasswordText = password
            if(model.oldPasswordText != model.lastUsedPassword)
                scene.showOldPasswordError(null)
            else
                scene.showOldPasswordError(UIMessage(R.string.password_enter_error))
        }

        override fun onPasswordChangedListener(password: String) {
            model.passwordText = password
            if(model.confirmPasswordText.isNotEmpty())
                checkPasswords(Pair(model.passwordText, model.confirmPasswordText))
        }

        override fun onConfirmPasswordChangedListener(confirmPassword: String) {
            model.confirmPasswordText = confirmPassword
            checkPasswords(Pair(model.confirmPasswordText, model.passwordText))
        }

        override fun onChangePasswordButtonPressed() {
            keyboardManager.hideKeyboard()
            model.lastUsedPassword = model.oldPasswordText
            dataSource.submitRequest(ChangePasswordRequest.ChangePassword(model.oldPasswordText, model.confirmPasswordText))
        }
    }

    private val dataSourceListener = { result: ChangePasswordResult ->
        when (result) {
            is ChangePasswordResult.ChangePassword -> onChangePasswordResult(result)
        }
    }

    override fun onStart(activityMessage: ActivityMessage?): Boolean {
        websocketEvents.setListener(webSocketEventListener)
        scene.attachView(changePasswordUIObserver, keyboardManager, model)
        dataSource.listener = dataSourceListener
        generalDataSource.listener = generalDataSourceListener
        return false
    }

    override fun onResume(activityMessage: ActivityMessage?): Boolean {
        websocketEvents.setListener(webSocketEventListener)
        return false
    }

    private fun checkPasswords(passwords: Pair<String, String>) {
        if (arePasswordsMatching && passwords.first.length >= minimumPasswordLength) {
            scene.showPasswordDialogError(null)
            model.passwordState = FormInputState.Valid()
            if (model.passwordState is FormInputState.Valid)
                scene.toggleChangePasswordButton(true)
        } else if (arePasswordsMatching && passwords.first.isEmpty()) {
            scene.showPasswordDialogError(null)
            model.passwordState = FormInputState.Unknown()
            scene.toggleChangePasswordButton(false)
        } else if (arePasswordsMatching && passwords.first.length < minimumPasswordLength) {
            val errorMessage = UIMessage(R.string.password_length_error)
            model.passwordState = FormInputState.Error(errorMessage)
            scene.showPasswordDialogError(errorMessage)
            scene.toggleChangePasswordButton(false)
        } else {
            val errorMessage = UIMessage(R.string.password_mismatch_error)
            model.passwordState = FormInputState.Error(errorMessage)
            scene.showPasswordDialogError(errorMessage)
            scene.toggleChangePasswordButton(false)
        }
    }

    private fun onChangePasswordResult(result: ChangePasswordResult.ChangePassword){
        when(result) {
            is ChangePasswordResult.ChangePassword.Success -> {
                scene.showMessage(UIMessage(R.string.change_password_success))
                host.finishScene()
            }
            is ChangePasswordResult.ChangePassword.Failure -> {
                scene.showOldPasswordError(UIMessage(R.string.password_enter_error))
            }
            is ChangePasswordResult.ChangePassword.EnterpriseSuspended -> {
                showSuspendedAccountDialog()
            }
        }
    }

    private fun onResetPassword(result: GeneralResult.ResetPassword){
        when(result) {
            is GeneralResult.ResetPassword.Success -> {
                scene.showForgotPasswordDialog(result.email)
            }
            is GeneralResult.ResetPassword.Failure -> {
                scene.showForgotPasswordDialog(null)
            }
        }
    }

    private fun onChangeToNextAccount(result: GeneralResult.ChangeToNextAccount){
        when(result) {
            is GeneralResult.ChangeToNextAccount.Success -> {
                activeAccount = result.activeAccount
                generalDataSource.activeAccount = activeAccount
                generalDataSource.signalClient = SignalClient.Default(SignalStoreCriptext(generalDataSource.db, activeAccount))
                dataSource.activeAccount = activeAccount
                val jwts = storage.getString(KeyValueStorage.StringKey.JWTS, "")
                websocketEvents = if(jwts.isNotEmpty())
                    WebSocketSingleton.getInstance(jwts)
                else
                    WebSocketSingleton.getInstance(activeAccount.jwt)

                websocketEvents.setListener(webSocketEventListener)

                scene.dismissAccountSuspendedDialog()

                scene.showMessage(UIMessage(R.string.snack_bar_active_account, arrayOf(activeAccount.userEmail)))
                host.goToScene(
                        params = MailboxParams(),
                        activityMessage = null,
                        keep = false, deletePastIntents = true
                )
            }
        }
    }

    override fun onPause() {
        cleanup()
    }

    override fun onStop() {
        cleanup()
    }

    override fun onNeedToSendEvent(event: Int) {
        generalDataSource.submitRequest(GeneralRequest.UserEvent(event))
    }

    private fun cleanup(){
        websocketEvents.clearListener(webSocketEventListener)
    }

    private fun showSuspendedAccountDialog(){
        val jwtList = storage.getString(KeyValueStorage.StringKey.JWTS, "").split(",").map { it.trim() }
        val dialogType = if(jwtList.isNotEmpty() && jwtList.size > 1) DialogType.SwitchAccount()
        else DialogType.SignIn()
        scene.showAccountSuspendedDialog(changePasswordUIObserver, activeAccount.userEmail, dialogType)
    }

    private val webSocketEventListener = object : WebSocketEventListener {
        override fun onLinkDeviceDismiss(accountEmail: String) {
            host.runOnUiThread(Runnable {
                scene.dismissLinkDeviceDialog()
            })
        }

        override fun onSyncDeviceDismiss(accountEmail: String) {
            host.runOnUiThread(Runnable {
                scene.dismissSyncDeviceDialog()
            })
        }

        override fun onAccountSuspended(accountEmail: String) {
            host.runOnUiThread(Runnable {
                if (accountEmail == activeAccount.userEmail)
                    showSuspendedAccountDialog()
            })
        }

        override fun onAccountUnsuspended(accountEmail: String) {
            host.runOnUiThread(Runnable {
                if (accountEmail == activeAccount.userEmail)
                    scene.dismissAccountSuspendedDialog()
            })
        }

        override fun onSyncBeginRequest(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo) {
            host.runOnUiThread(Runnable {
                scene.showSyncDeviceAuthConfirmation(trustedDeviceInfo)
            })
        }

        override fun onSyncRequestAccept(syncStatusData: SyncStatusData) {

        }

        override fun onSyncRequestDeny() {

        }

        override fun onDeviceDataUploaded(key: String, dataAddress: String, authorizerId: Int) {

        }

        override fun onDeviceLinkAuthDeny() {

        }

        override fun onDeviceLinkAuthRequest(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo) {
            host.runOnUiThread(Runnable {
                scene.showLinkDeviceAuthConfirmation(untrustedDeviceInfo)
            })
        }

        override fun onDeviceLinkAuthAccept(linkStatusData: LinkStatusData) {

        }

        override fun onKeyBundleUploaded(deviceId: Int) {

        }

        override fun onNewEvent(recipientId: String, domain: String) {

        }

        override fun onRecoveryEmailChanged(newEmail: String) {

        }

        override fun onRecoveryEmailConfirmed() {

        }

        override fun onDeviceLocked() {
            host.runOnUiThread(Runnable {
                host.showConfirmPasswordDialog(changePasswordUIObserver)
            })
        }

        override fun onDeviceRemoved() {
            generalDataSource.submitRequest(GeneralRequest.DeviceRemoved(false))
        }

        override fun onError(uiMessage: UIMessage) {
            scene.showMessage(uiMessage)
        }
    }

    override fun onBackPressed(): Boolean {
        changePasswordUIObserver.onBackButtonPressed()
        return false
    }

    override fun onMenuChanged(menu: IHostActivity.IActivityMenu) {}

    override fun onOptionsItemSelected(itemId: Int) {

    }

    override fun requestPermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

    }

    companion object {
        const val minimumPasswordLength = 8
    }
}