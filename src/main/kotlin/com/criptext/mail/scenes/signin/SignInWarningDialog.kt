package com.criptext.mail.scenes.signin

import android.content.Context
import android.os.CountDownTimer
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.criptext.mail.R
import com.criptext.mail.scenes.settings.SettingsUIObserver
import com.criptext.mail.scenes.signin.data.UserData
import com.criptext.mail.utils.EmailAddressUtils
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.getLocalizedUIMessage


class SignInWarningDialog(val context: Context) {

    private var dialog: AlertDialog? = null
    private val res = context.resources

    fun showDialog(observer: SignInSceneController.SignInUIObserver?, oldAccount: String, newUserData: UserData) {

        val dialogBuilder = AlertDialog.Builder(context)
        val inflater = (context as AppCompatActivity).layoutInflater
        val dialogView = inflater.inflate(R.layout.sign_in_warning_dialog, null)
        val oldAccountsList = oldAccount.split(",").map { EmailAddressUtils.hideEmailAddress(it) }
        dialogView.findViewById<TextView>(R.id.message_text).text =
                context.getLocalizedUIMessage(UIMessage(R.string.sign_in_warning_dialog_message,
                        arrayOf(oldAccountsList.joinToString())))

        dialogBuilder.setView(dialogView)

        dialog = createDialog(dialogView, dialogBuilder, observer, newUserData)
    }

    private fun createDialog(dialogView: View, dialogBuilder: AlertDialog.Builder,
                             observer: SignInSceneController.SignInUIObserver?, newUserData: UserData): AlertDialog {

        val width = res.getDimension(R.dimen.password_login_dialog_width).toInt()
        val newLogoutDialog = dialogBuilder.create()
        val window = newLogoutDialog.window
        newLogoutDialog.show()
        window?.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER_VERTICAL)
        val drawableBackground = ContextCompat.getDrawable(dialogView.context,
                R.drawable.dialog_label_chooser_shape)
        newLogoutDialog.window?.setBackgroundDrawable(drawableBackground)
        newLogoutDialog.setCanceledOnTouchOutside(false)
        newLogoutDialog.setCancelable(false)

        assignButtonEvents(dialogView, newLogoutDialog, observer, newUserData)


        return newLogoutDialog
    }

    private fun assignButtonEvents(view: View, dialog: AlertDialog,
                                   observer: SignInSceneController.SignInUIObserver?, newUserData: UserData) {

        val btn_yes = view.findViewById(R.id.settings_logout_yes) as Button
        btn_yes.isEnabled = false
        timerListener(btn_yes,10000)
        val btn_no = view.findViewById(R.id.settings_logout_cancel) as Button

        btn_yes.setOnClickListener {
            dialog.dismiss()
            observer?.onSignInWarningContinue(newUserData)
        }

        btn_no.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun timerListener(button: Button, startTime: Long) {
        object : CountDownTimer(startTime, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                val sec = ((millisUntilFinished / 1000) % 60).toInt()
                button.text = context.getString(R.string.btn_continue_with_time, sec)
            }

            override fun onFinish() {
                button.setText(R.string.btn_continue)
                button.isEnabled = true
            }
        }.start()
    }
}
