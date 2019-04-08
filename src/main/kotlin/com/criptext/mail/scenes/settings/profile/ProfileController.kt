package com.criptext.mail.scenes.settings.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import com.criptext.mail.BaseActivity
import com.criptext.mail.ExternalActivityParams
import com.criptext.mail.IHostActivity
import com.criptext.mail.R
import com.criptext.mail.api.Hosts
import com.criptext.mail.api.models.DeviceInfo
import com.criptext.mail.api.models.SyncStatusData
import com.criptext.mail.bgworker.BackgroundWorkManager
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.ActivityMessage
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.params.*
import com.criptext.mail.scenes.settings.profile.data.ProfileRequest
import com.criptext.mail.scenes.settings.profile.data.ProfileResult
import com.criptext.mail.scenes.signin.data.LinkStatusData
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.PinLockUtils
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.Utility
import com.criptext.mail.utils.generaldatasource.data.GeneralRequest
import com.criptext.mail.utils.generaldatasource.data.GeneralResult
import com.criptext.mail.utils.generaldatasource.data.UserDataWriter
import com.criptext.mail.utils.ui.data.DialogData
import com.criptext.mail.utils.ui.data.DialogResult
import com.criptext.mail.utils.ui.data.DialogType
import com.criptext.mail.validation.AccountDataValidator
import com.criptext.mail.validation.FormData
import com.criptext.mail.websocket.WebSocketEventListener
import com.criptext.mail.websocket.WebSocketEventPublisher
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.MemoryPolicy
import java.io.File
import java.lang.Exception


class ProfileController(
        private var activeAccount: ActiveAccount,
        private val model: ProfileModel,
        private val scene: ProfileScene,
        private val host: IHostActivity,
        private val storage: KeyValueStorage,
        private val keyboardManager: KeyboardManager,
        private val websocketEvents: WebSocketEventPublisher,
        private val dataSource: BackgroundWorkManager<ProfileRequest, ProfileResult>,
        private val generalDataSource: BackgroundWorkManager<GeneralRequest, GeneralResult>)
    : SceneController(){

    override val menuResourceId: Int? = null

    private val generalDataSourceListener: (GeneralResult) -> Unit = { result ->
        when(result) {
            is GeneralResult.DeviceRemoved -> onDeviceRemovedRemotely(result)
            is GeneralResult.ConfirmPassword -> onPasswordChangedRemotely(result)
            is GeneralResult.LinkAccept -> onLinkAccept(result)
            is GeneralResult.SyncAccept -> onSyncAccept(result)
            is GeneralResult.ChangeContactName -> onContactNameChanged(result)
            is GeneralResult.GetRemoteFile -> onGetRemoteFile(result)
            is GeneralResult.DeleteAccount -> onDeleteAccount(result)
            is GeneralResult.Logout -> onLogout(result)
        }
    }

    private val dataSourceListener: (ProfileResult) -> Unit = { result ->
        when(result) {
            is ProfileResult.SetProfilePicture -> onProfilePictureSet(result)
            is ProfileResult.DeleteProfilePicture -> onProfilePictureDeleted(result)
        }
    }

    private val uiObserver = object: ProfileUIObserver{
        override fun onLogoutClicked() {
            scene.showLogoutDialog(model.userData.isLastDeviceWith2FA)
        }

        override fun onLogoutConfirmedClicked() {
            scene.showMessageAndProgressDialog(UIMessage(R.string.login_out_dialog_message))
            generalDataSource.submitRequest(GeneralRequest.Logout(false))
        }

        override fun onDeleteAccountClicked() {
            scene.showGeneralDialogWithInputPassword(DialogData.DialogMessageData(
                    title = UIMessage(R.string.delete_account_dialog_title),
                    message = listOf(UIMessage(R.string.delete_account_dialog_message)),
                    type = DialogType.DeleteAccount()
            ))
        }

        override fun onRecoveryEmailOptionClicked() {
            host.goToScene(RecoveryEmailParams(model.userData), false)
        }

        override fun onReplyToChangeClicked() {
            host.goToScene(ReplyToParams(model.userData), false)
        }

        override fun onChangePasswordOptionClicked() {
            host.goToScene(ChangePasswordParams(), true)
        }

        override fun onSignatureOptionClicked() {
            host.goToScene(SignatureParams(activeAccount.recipientId), true)
        }

        override fun onProfileNameChanged(name: String) {
            keyboardManager.hideKeyboard()
            model.userData.name = name
            generalDataSource.submitRequest(GeneralRequest.ChangeContactName(
                    fullName = name,
                    recipientId = activeAccount.recipientId
            ))
        }

        override fun onEditPicturePressed() {
            if(host.checkPermissions(BaseActivity.RequestCode.writeAccess.ordinal,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                scene.showBottomDialog(this)
            }
        }

        override fun onEditProfileNamePressed() {
            scene.showProfileNameDialog(model.userData.name)
        }

        override fun onNewCamPictureRequested() {
            scene.showProfilePictureProgress()
            PinLockUtils.setPinLockTimeout(PinLockUtils.TIMEOUT_TO_DISABLE)
            host.launchExternalActivityForResult(ExternalActivityParams.Camera())
        }

        override fun onNewGalleryPictureRequested() {
            scene.showProfilePictureProgress()
            PinLockUtils.setPinLockTimeout(PinLockUtils.TIMEOUT_TO_DISABLE)
            host.launchExternalActivityForResult(ExternalActivityParams.ProfileImagePicker())
        }

        override fun onDeletePictureRequested() {
            scene.showProfilePictureProgress()
            dataSource.submitRequest(ProfileRequest.DeleteProfilePicture())
        }

        override fun onSnackbarClicked() {

        }

        override fun onSyncAuthConfirmed(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo) {
            if(trustedDeviceInfo.syncFileVersion == UserDataWriter.FILE_SYNC_VERSION)
                generalDataSource.submitRequest(GeneralRequest.SyncAccept(trustedDeviceInfo))
            else
                scene.showMessage(UIMessage(R.string.sync_version_incorrect))
        }

        override fun onSyncAuthDenied(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo) {
            generalDataSource.submitRequest(GeneralRequest.SyncDenied(trustedDeviceInfo))
        }

        override fun onGeneralOkButtonPressed(result: DialogResult) {
            when(result){
                is DialogResult.DialogWithInput -> {
                    when(result.type){
                        is DialogType.DeleteAccount -> {
                            scene.toggleGeneralDialogLoad(true)
                            generalDataSource.submitRequest(GeneralRequest.DeleteAccount(result.textInput))
                        }
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

        override fun onLinkAuthConfirmed(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo) {
            if(untrustedDeviceInfo.syncFileVersion == UserDataWriter.FILE_SYNC_VERSION)
                generalDataSource.submitRequest(GeneralRequest.LinkAccept(untrustedDeviceInfo))
            else
                scene.showMessage(UIMessage(R.string.sync_version_incorrect))
        }

        override fun onLinkAuthDenied(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo) {
            generalDataSource.submitRequest(GeneralRequest.LinkDenied(untrustedDeviceInfo))
        }

        override fun onBackButtonPressed() {
            keyboardManager.hideKeyboard()
            host.exitToScene(SettingsParams(), null,true)
        }
    }

    override fun onStart(activityMessage: ActivityMessage?): Boolean {
        websocketEvents.setListener(webSocketEventListener)

        scene.attachView(uiObserver, activeAccount.recipientId, model)
        generalDataSource.listener = generalDataSourceListener
        dataSource.listener = dataSourceListener

        return handleActivityMessage(activityMessage)
    }

    private fun onLogout(result: GeneralResult.Logout){
        when(result) {
            is GeneralResult.Logout.Success -> {
                if(result.activeAccount == null)
                    host.exitToScene(SignInParams(), null, false, true)
                else {
                    activeAccount = result.activeAccount
                    host.exitToScene(MailboxParams(),
                            ActivityMessage.ShowUIMessage(UIMessage(R.string.snack_bar_active_account, arrayOf(activeAccount.userEmail))),
                            false, true)
                }
            }
            is GeneralResult.Logout.Failure -> {
                scene.dismissMessageAndProgressDialog()
                scene.showMessage(UIMessage(R.string.error_login_out))
            }
        }
    }

    private fun onDeleteAccount(result: GeneralResult.DeleteAccount){
        scene.toggleGeneralDialogLoad(false)
        when(result) {
            is GeneralResult.DeleteAccount.Success -> {
                if(result.activeAccount == null)
                    host.exitToScene(SignInParams(), ActivityMessage.ShowUIMessage(UIMessage(R.string.delete_account_toast_message)), false, true)
                else {
                    activeAccount = result.activeAccount
                    host.exitToScene(MailboxParams(),
                            ActivityMessage.ShowUIMessage(UIMessage(R.string.snack_bar_active_account, arrayOf(activeAccount.userEmail))),
                            false, true)
                }
            }
            is GeneralResult.DeleteAccount.Failure -> {
                scene.setGeneralDialogWithInputError(result.message)
            }
        }
    }

    private fun onLinkAccept(resultData: GeneralResult.LinkAccept){
        when (resultData) {
            is GeneralResult.LinkAccept.Success -> {
                host.exitToScene(LinkingParams(activeAccount.userEmail, resultData.deviceId,
                        resultData.uuid, resultData.deviceType), null,
                        false, true)
            }
            is GeneralResult.LinkAccept.Failure -> {
                scene.showMessage(resultData.message)
            }
        }
    }

    private fun onSyncAccept(resultData: GeneralResult.SyncAccept){
        when (resultData) {
            is GeneralResult.SyncAccept.Success -> {
                host.exitToScene(LinkingParams(activeAccount.userEmail, resultData.deviceId,
                        resultData.uuid, resultData.deviceType), ActivityMessage.SyncMailbox(),
                        false, true)
            }
            is GeneralResult.SyncAccept.Failure -> {
                scene.showMessage(resultData.message)
            }
        }
    }

    private fun onPasswordChangedRemotely(result: GeneralResult.ConfirmPassword){
        when (result) {
            is GeneralResult.ConfirmPassword.Success -> {
                scene.dismissConfirmPasswordDialog()
                scene.showMessage(UIMessage(R.string.update_password_success))
            }
            is GeneralResult.ConfirmPassword.Failure -> {
                scene.setConfirmPasswordError(result.message)
            }
        }
    }

    private fun onDeviceRemovedRemotely(result: GeneralResult.DeviceRemoved){
        when (result) {
            is GeneralResult.DeviceRemoved.Success -> {
                if(result.activeAccount == null)
                    host.exitToScene(SignInParams(), ActivityMessage.ShowUIMessage(UIMessage(R.string.device_removed_remotely_exception)),
                            true, true)
                else {
                    activeAccount = result.activeAccount
                    host.exitToScene(MailboxParams(),
                            ActivityMessage.ShowUIMessage(UIMessage(R.string.snack_bar_active_account, arrayOf(activeAccount.userEmail))),
                            false, true)
                }
            }
        }
    }

    private fun onContactNameChanged(result: GeneralResult.ChangeContactName){
        when(result) {
            is GeneralResult.ChangeContactName.Success -> {
                scene.updateProfileName(result.fullName)
                activeAccount.updateFullName(storage, model.userData.name)
                scene.showMessage(UIMessage(R.string.profile_name_updated))
            }
            is GeneralResult.ChangeContactName.Failure -> {
                scene.updateProfileName(activeAccount.name)
                model.userData.name = activeAccount.name
                scene.showMessage(UIMessage(R.string.error_updating_account))
            }
        }
    }

    private fun onProfilePictureSet(result: ProfileResult.SetProfilePicture){
        when(result) {
            is ProfileResult.SetProfilePicture.Success -> {
                Picasso.get().invalidate(Hosts.restApiBaseUrl.plus("/user/avatar/${activeAccount.recipientId}"))
                scene.hideProfilePictureProgress()
                scene.showMessage(UIMessage(R.string.profile_picture_updated))
            }
            is ProfileResult.SetProfilePicture.Failure -> {
                scene.resetProfilePicture(model.userData.name)
                scene.hideProfilePictureProgress()
                scene.showMessage(UIMessage(R.string.profile_picture_update_failed))
            }
        }
    }

    private fun onProfilePictureDeleted(result: ProfileResult.DeleteProfilePicture){
        scene.hideProfilePictureProgress()
        when(result) {
            is ProfileResult.DeleteProfilePicture.Success -> {
                scene.resetProfilePicture(model.userData.name)
                scene.showMessage(UIMessage(R.string.profile_picture_deleted))
            }
            is ProfileResult.DeleteProfilePicture.Failure -> {
                scene.showMessage(UIMessage(R.string.profile_picture_delete_failed))
            }
        }
    }

    private fun onGetRemoteFile(result: GeneralResult.GetRemoteFile) {
        when (result) {
            is GeneralResult.GetRemoteFile.Success -> {
                scene.dismissPreparingFileDialog()
                setBitmapOnProfileImage(result.remoteFiles.first().first)
            }
        }
    }

    override fun onStop() {
        websocketEvents.clearListener(webSocketEventListener)
    }

    private fun setBitmapOnProfileImage(imagePath: String){
        val file = File(imagePath)
        val bitmapImage = Utility.getBitmapFromFile(file)

        scene.showProfilePictureProgress()
        val exif = ExifInterface(file.path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> {
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(bitmapImage, 0, 0,
                bitmapImage.width, bitmapImage.height, matrix, true)
        dataSource.submitRequest(ProfileRequest.SetProfilePicture(rotatedBitmap))
        scene.updateProfilePicture(rotatedBitmap)
    }

    private fun handleActivityMessage(activityMessage: ActivityMessage?): Boolean {
        PinLockUtils.resetLastMillisPin()
        PinLockUtils.setPinLockTimeoutPosition(storage.getInt(KeyValueStorage.StringKey.PINTimeout, 1))
        if (activityMessage is ActivityMessage.ProfilePictureFile) {
            return if(activityMessage.filesMetadata.second != -1L) {
                setBitmapOnProfileImage(activityMessage.filesMetadata.first)
                true
            }else{
                val resolver = host.getContentResolver()
                if(resolver != null) {
                    scene.showPreparingFileDialog()
                    generalDataSource.submitRequest(GeneralRequest.GetRemoteFile(
                            listOf(activityMessage.filesMetadata.first), resolver)
                    )
                }
                true
            }
        }
        return false
    }

    private val webSocketEventListener = object : WebSocketEventListener {
        override fun onSyncBeginRequest(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo) {

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

        override fun onNewEvent() {

        }

        override fun onRecoveryEmailChanged(newEmail: String) {

        }

        override fun onRecoveryEmailConfirmed() {

        }

        override fun onDeviceLocked() {
            host.runOnUiThread(Runnable {
                scene.showConfirmPasswordDialog(uiObserver)
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
        uiObserver.onBackButtonPressed()
        return false
    }

    override fun onMenuChanged(menu: IHostActivity.IActivityMenu) {}

    override fun onOptionsItemSelected(itemId: Int) {

    }

    override fun requestPermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != BaseActivity.RequestCode.writeAccess.ordinal) return

        val indexOfPermission = permissions.indexOfFirst { it == Manifest.permission.WRITE_EXTERNAL_STORAGE }
        if (indexOfPermission < 0) return
        if (grantResults[indexOfPermission] != PackageManager.PERMISSION_GRANTED) {
            scene.showMessage(UIMessage(R.string.permission_filepicker_rationale))
            return
        }
        scene.showBottomDialog(uiObserver)
    }
}