package com.criptext.mail.scenes.settings.cloudbackup

import com.criptext.mail.ExternalActivityParams
import com.criptext.mail.IHostActivity
import com.criptext.mail.R
import com.criptext.mail.api.models.DeviceInfo
import com.criptext.mail.api.models.SyncStatusData
import com.criptext.mail.bgworker.BackgroundWorkManager
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.ActivityMessage
import com.criptext.mail.scenes.SceneController
import com.criptext.mail.scenes.params.SettingsParams
import com.criptext.mail.scenes.settings.cloudbackup.data.CloudBackupData
import com.criptext.mail.scenes.settings.cloudbackup.data.CloudBackupRequest
import com.criptext.mail.scenes.settings.cloudbackup.data.CloudBackupResult
import com.criptext.mail.scenes.signin.data.LinkStatusData
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.PinLockUtils
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.websocket.WebSocketEventListener
import com.criptext.mail.websocket.WebSocketEventPublisher
import com.google.api.client.googleapis.media.MediaHttpUploader
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener
import java.io.IOException

class CloudBackupController(
        private val model: CloudBackupModel,
        private val scene: CloudBackupScene,
        private val host: IHostActivity,
        private val keyboardManager: KeyboardManager,
        private val activeAccount: ActiveAccount,
        private val storage: KeyValueStorage,
        private val websocketEvents: WebSocketEventPublisher,
        private val dataSource: BackgroundWorkManager<CloudBackupRequest, CloudBackupResult>)
    : SceneController(){



    override val menuResourceId: Int? = null

    private val dataSourceListener: (CloudBackupResult) -> Unit = { result ->
        when(result) {
            is CloudBackupResult.SetCloudBackupActive -> onSetCloudBackupActive(result)
            is CloudBackupResult.LoadCloudBakcupData -> onLoadCloudBackupData(result)
            is CloudBackupResult.UploadBackupToDrive -> onUploadBackupToDrive(result)
            is CloudBackupResult.DataFileCreation -> onDataFileCreated(result)
        }
    }

    private val progressListener = CloudBackupProgressListener()

    private val uiObserver = object: CloudBackupUIObserver{
        override fun backUpNowPressed() {
            scene.backingUpNow(true)
            if(model.mDriveService == null) model.mDriveService = scene.getGoogleDriveService()
            if(model.mDriveService != null) {
                dataSource.submitRequest(CloudBackupRequest.DataFileCreation(null))
                scene.showProgressDialog()
            }else{
                host.launchExternalActivityForResult(ExternalActivityParams.SignInGoogleDrive())
            }
        }

        override fun onPasswordChangedListener(password: String) {
            if(password.isNotEmpty() && password.length >= 3) {
                model.passwordForNonCriptextUsers = password
                scene.enableSaveButtonOnDialog()
            }else{
                model.passwordForNonCriptextUsers = null
                scene.disableSaveButtonOnDialog()
            }
        }

        override fun setOnCheckedChangeListener(isChecked: Boolean) {
            if(!isChecked){
                model.passwordForNonCriptextUsers = null
            }else{
                scene.disableSaveButtonOnDialog()
            }
        }

        override fun encryptDialogButtonPressed() {
            dataSource.submitRequest(CloudBackupRequest.DataFileCreation(model.passwordForNonCriptextUsers))
            scene.showProgressDialog()
        }

        override fun onFrequencyChanged(frequency: Int) {
            dataSource.submitRequest(CloudBackupRequest.SetCloudBackupActive(
                    CloudBackupData(
                            hasCloudBackup = model.hasCloudBackup,
                            autoBackupFrequency = frequency,
                            useWifiOnly = model.wifiOnly,
                            fileSize = model.lastBackupSize,
                            lastModified = model.lastTimeBackup
                    )
            ))
        }

        override fun onWifiOnlySwiched(isActive: Boolean) {
            dataSource.submitRequest(CloudBackupRequest.SetCloudBackupActive(
                    CloudBackupData(
                            hasCloudBackup = model.hasCloudBackup,
                            autoBackupFrequency = model.autoBackupFrequency,
                            useWifiOnly = isActive,
                            fileSize = model.lastBackupSize,
                            lastModified = model.lastTimeBackup
                    )
            ))
        }

        override fun onChangeGoogleDriveAccount() {
            host.launchExternalActivityForResult(ExternalActivityParams.ChangeAccountGoogleDrive())
        }

        override fun onCloudBackupActivated(isActive: Boolean) {
            dataSource.submitRequest(CloudBackupRequest.SetCloudBackupActive(
                    CloudBackupData(
                            hasCloudBackup = isActive,
                            autoBackupFrequency = model.autoBackupFrequency,
                            useWifiOnly = model.wifiOnly,
                            fileSize = model.lastBackupSize,
                            lastModified = model.lastTimeBackup
                    )
            ))
        }

        override fun onBackButtonPressed() {
            host.exitToScene(SettingsParams(), null,true)
        }
    }

    override fun onStart(activityMessage: ActivityMessage?): Boolean {
        PinLockUtils.enablePinLock()

        websocketEvents.setListener(webSocketEventListener)
        model.activeAccountEmail = activeAccount.userEmail
        scene.attachView(model = model, cloudBackupUIObserver1 = uiObserver)
        dataSource.listener = dataSourceListener
        dataSource.submitRequest(CloudBackupRequest.LoadCloudBackupData(model.mDriveService))
        return handleActivityMessage(activityMessage)
    }

    override fun onResume(activityMessage: ActivityMessage?): Boolean {
        return handleActivityMessage(activityMessage)
    }

    private fun handleActivityMessage(activityMessage: ActivityMessage?): Boolean {
        if (activityMessage is ActivityMessage.GoogleDriveSignIn) {
            if(activityMessage.driveService != null) {
                model.mDriveService = activityMessage.driveService
                uiObserver.backUpNowPressed()
            }else{
                scene.setCloudBackupSwitchState(false)
                scene.showMessage(UIMessage(R.string.login_fail_try_again_error_exception))
            }

            return true
        }
        return false
    }

    private fun onDataFileCreated(result: CloudBackupResult.DataFileCreation){
        when(result){
            is CloudBackupResult.DataFileCreation.Success -> {
                scene.setProgressDialog(100)
                scene.hideProgressDialog()
                if (model.mDriveService != null){
                    scene.showUploadProgressBar(true)
                    scene.setUploadProgress(0)
                    dataSource.submitRequest(CloudBackupRequest.UploadBackupToDrive(result.filePath,
                            model.mDriveService!!, progressListener))
                }else{
                    scene.backingUpNow(false)
                }
            }
            is CloudBackupResult.DataFileCreation.Progress -> {
                scene.setProgressDialog(result.progress)
            }
            is CloudBackupResult.DataFileCreation.Failure -> {
                scene.backingUpNow(false)
                scene.hideProgressDialog()
                scene.showMessage(result.message)
            }
        }
    }

    private fun onSetCloudBackupActive(result: CloudBackupResult.SetCloudBackupActive){
        when(result){
            is CloudBackupResult.SetCloudBackupActive.Success -> {
                model.wifiOnly = result.cloudBackupData.useWifiOnly
                storage.putBool(KeyValueStorage.StringKey.UseWifiOnlyForBackup, model.wifiOnly)
                if(model.autoBackupFrequency != result.cloudBackupData.autoBackupFrequency){
                    scene.removeFromScheduleCloudBackupJob()
                    model.autoBackupFrequency = result.cloudBackupData.autoBackupFrequency
                    scene.scheduleCloudBackupJob(model.autoBackupFrequency)
                }
                if(model.hasCloudBackup != result.cloudBackupData.hasCloudBackup && result.cloudBackupData.hasCloudBackup) {
                    model.hasCloudBackup = result.cloudBackupData.hasCloudBackup
                    if(model.autoBackupFrequency != 3) {
                        scene.removeFromScheduleCloudBackupJob()
                        scene.scheduleCloudBackupJob(model.autoBackupFrequency)
                    } else
                        scene.removeFromScheduleCloudBackupJob()
                    host.launchExternalActivityForResult(ExternalActivityParams.SignInGoogleDrive())

                } else {
                    model.hasCloudBackup = result.cloudBackupData.hasCloudBackup
                    scene.removeFromScheduleCloudBackupJob()
                    host.launchExternalActivityForResult(ExternalActivityParams.SignOutGoogleDrive())
                }
            }
            is CloudBackupResult.SetCloudBackupActive.Failure -> {
                scene.setCloudBackupSwitchState(!result.cloudBackupData.hasCloudBackup)
            }
        }
    }

    private fun onLoadCloudBackupData(result: CloudBackupResult.LoadCloudBakcupData){
        when(result){
            is CloudBackupResult.LoadCloudBakcupData.Success -> {
                model.autoBackupFrequency = result.cloudBackupData.autoBackupFrequency
                model.hasCloudBackup = result.cloudBackupData.hasCloudBackup
                model.wifiOnly = result.cloudBackupData.useWifiOnly
                model.lastBackupSize = result.cloudBackupData.fileSize
                model.lastTimeBackup = result.cloudBackupData.lastModified
                scene.updateCloudBackupData(model)
            }
            is CloudBackupResult.LoadCloudBakcupData.Failure -> {
                scene.updateCloudBackupData(model)
            }
        }
    }

    private fun onUploadBackupToDrive(result: CloudBackupResult.UploadBackupToDrive){
        when(result){
            is CloudBackupResult.UploadBackupToDrive.Success -> {
                model.hasCloudBackup = result.hasOldFile
                model.oldFileId = result.oldFileId
                model.fileLength = result.fileLength
                model.lastTimeBackup = result.lastModified
                scene.backingUpNow(false)
                scene.updateFileInfo(model.fileLength, model.lastTimeBackup)
                if(model.hasOldFile && model.isBackupDone) {
                    model.isBackupDone = false
                    dataSource.submitRequest(CloudBackupRequest.DeleteFileInDrive(model.mDriveService!!, model.oldFileId!!))
                    model.hasOldFile = false
                }
            }
            is CloudBackupResult.UploadBackupToDrive.Progress -> {
                scene.setUploadProgress(result.progress)
            }
            is CloudBackupResult.UploadBackupToDrive.Failure -> {
                scene.backingUpNow(false)
                scene.showUploadProgressBar(false)
                scene.showMessage(result.message)
            }
        }
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

        override fun onKeyBundleUploaded(deviceId: Int) {

        }

        override fun onDeviceLinkAuthRequest(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo) {

        }

        override fun onDeviceLinkAuthAccept(linkStatusData: LinkStatusData) {

        }

        override fun onNewEvent() {

        }

        override fun onRecoveryEmailChanged(newEmail: String) {

        }

        override fun onRecoveryEmailConfirmed() {

        }

        override fun onDeviceLocked() {

        }

        override fun onDeviceRemoved() {

        }

        override fun onError(uiMessage: UIMessage) {
            scene.showMessage(uiMessage)
        }
    }

    private fun updateUploadDone(){
        scene.setUploadProgress(100)
        scene.showUploadProgressBar(false)
        scene.backingUpNow(false)
        scene.checkCloudBackupIcon()
        scene.updateFileInfo(model.fileLength, model.lastTimeBackup)
    }

    inner class CloudBackupProgressListener: MediaHttpUploaderProgressListener {
        @Throws(IOException::class)
        override fun progressChanged(uploader: MediaHttpUploader) {
            when (uploader.uploadState) {
                MediaHttpUploader.UploadState.INITIATION_STARTED -> scene.setUploadProgress(20)
                MediaHttpUploader.UploadState.INITIATION_COMPLETE -> scene.setUploadProgress(40)
                MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS -> {
                    scene.setUploadProgress((uploader.progress * 100).toInt())
                }
                MediaHttpUploader.UploadState.MEDIA_COMPLETE -> {
                    model.isBackupDone = true
                    if(model.hasOldFile) {
                        dataSource.submitRequest(CloudBackupRequest.DeleteFileInDrive(model.mDriveService!!, model.oldFileId!!))
                        model.hasOldFile = false
                    }
                    host.runOnUiThread(Runnable {
                        updateUploadDone()
                    })
                }
            }
        }
    }

    override fun onStop() {
        websocketEvents.clearListener(webSocketEventListener)
    }

    override fun onBackPressed(): Boolean {
        uiObserver.onBackButtonPressed()
        return false
    }

    override fun onMenuChanged(menu: IHostActivity.IActivityMenu) {}

    override fun onOptionsItemSelected(itemId: Int) {

    }

    override fun requestPermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

    }
}