package com.criptext.mail.scenes.mailbox.emailsource

import android.view.View
import android.widget.*
import com.criptext.mail.R
import com.criptext.mail.api.models.DeviceInfo
import com.criptext.mail.scenes.signin.SignInActivity
import com.criptext.mail.scenes.signup.SignUpActivity
import com.criptext.mail.splash.SplashActivity
import com.criptext.mail.utils.KeyboardManager
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.getLocalizedUIMessage
import com.criptext.mail.utils.ui.ConfirmPasswordDialog
import com.criptext.mail.utils.ui.ForgotPasswordDialog
import com.criptext.mail.utils.ui.LinkNewDeviceAlertDialog
import com.criptext.mail.utils.ui.SyncDeviceAlertDialog
import com.criptext.mail.utils.uiobserver.UIObserver
import com.github.omadahealth.lollipin.lib.managers.LockManager


interface EmailSourceScene{

    fun attachView(uiObserver: EmailSourceUIObserver,
                   keyboardManager: KeyboardManager, model: EmailSourceModel)
    fun showMessage(message: UIMessage)
    fun showLinkDeviceAuthConfirmation(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo)
    fun showSyncDeviceAuthConfirmation(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo)

    class Default(val view: View): EmailSourceScene{
        private lateinit var emailSourceUIObserver: EmailSourceUIObserver

        private val context = view.context

        private val emailSourceText: TextView by lazy {
            view.findViewById<TextView>(R.id.source_text)
        }

        private val backButton: ImageView by lazy {
            view.findViewById<ImageView>(R.id.mailbox_back_button)
        }

        private val linkAuthDialog = LinkNewDeviceAlertDialog(context)
        private val syncAuthDialog = SyncDeviceAlertDialog(context)

        override fun attachView(uiObserver: EmailSourceUIObserver,
                                keyboardManager: KeyboardManager, model: EmailSourceModel) {

            emailSourceUIObserver = uiObserver

            emailSourceText.text = model.emailSource

            backButton.setOnClickListener {
                uiObserver.onBackButtonPressed()
            }

        }

        override fun showLinkDeviceAuthConfirmation(untrustedDeviceInfo: DeviceInfo.UntrustedDeviceInfo) {
            if(linkAuthDialog.isShowing() != null && linkAuthDialog.isShowing() == false)
                linkAuthDialog.showLinkDeviceAuthDialog(emailSourceUIObserver, untrustedDeviceInfo)
            else if(linkAuthDialog.isShowing() == null)
                linkAuthDialog.showLinkDeviceAuthDialog(emailSourceUIObserver, untrustedDeviceInfo)
        }

        override fun showSyncDeviceAuthConfirmation(trustedDeviceInfo: DeviceInfo.TrustedDeviceInfo) {
            if(syncAuthDialog.isShowing() != null && syncAuthDialog.isShowing() == false)
                syncAuthDialog.showLinkDeviceAuthDialog(emailSourceUIObserver, trustedDeviceInfo)
            else if(syncAuthDialog.isShowing() == null)
                syncAuthDialog.showLinkDeviceAuthDialog(emailSourceUIObserver, trustedDeviceInfo)
        }

        override fun showMessage(message: UIMessage) {
            val duration = Toast.LENGTH_LONG
            val toast = Toast.makeText(
                    context,
                    context.getLocalizedUIMessage(message),
                    duration)
            toast.show()
        }
    }

}