package com.criptext.mail.scenes.settings.recovery_email

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import com.criptext.mail.R

/**
 * Created by sebas on 3/8/18.
 */

class ConfirmationSentDialog(val context: Context) {

    private var dialog: AlertDialog? = null
    private val res = context.resources

    fun showDialog() {

        val dialogBuilder = AlertDialog.Builder(context)
        val inflater = (context as AppCompatActivity).layoutInflater
        val dialogView = inflater.inflate(R.layout.recovery_email_sent_message_dialog, null)

        dialogBuilder.setView(dialogView)

        dialog = createDialog(dialogView, dialogBuilder)
    }

    private fun createDialog(dialogView: View, dialogBuilder: AlertDialog.Builder): AlertDialog {

        val width = res.getDimension(R.dimen.password_login_dialog_width).toInt()
        val newLogoutDialog = dialogBuilder.create()
        val window = newLogoutDialog.window
        newLogoutDialog.show()
        window?.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT)
        window?.setGravity(Gravity.CENTER_VERTICAL)
        val drawableBackground = ContextCompat.getDrawable(dialogView.context,
                R.drawable.dialog_label_chooser_shape)
        newLogoutDialog.window?.setBackgroundDrawable(drawableBackground)

        assignButtonEvents(dialogView, newLogoutDialog)


        return newLogoutDialog
    }

    private fun assignButtonEvents(view: View, dialog: AlertDialog) {

        val btn_yes = view.findViewById(R.id.recovery_email_ok) as Button

        btn_yes.setOnClickListener {
            dialog.dismiss()
        }
    }
}
