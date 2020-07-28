package com.criptext.mail.scenes.signup.holders

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import com.google.android.material.textfield.TextInputLayout
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.AppCompatEditText
import android.view.View
import com.criptext.mail.R
import com.criptext.mail.validation.FormInputState
import com.criptext.mail.utils.getLocalizedUIMessage

/**
 * Created by gabriel on 5/15/18.
 */
class FormInputViewHolder(private val textInputLayout: TextInputLayout,
                          private val editText: AppCompatEditText,
                          private val validView: View?,
                          private val errorView: View?,
                          private val disableSubmitButton: () -> Unit) {

    private val ctx = textInputLayout.context


    @SuppressLint("RestrictedApi")
    fun setState(state: FormInputState) {
        when (state) {
            is FormInputState.Valid -> {
                validView?.visibility = View.VISIBLE
                errorView?.visibility = View.INVISIBLE
                textInputLayout.error = ""

                textInputLayout.setHintTextAppearance(R.style.textinputlayout_login)
                editText.supportBackgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(ctx, R.color.azure))
            }

            is FormInputState.Unknown -> {
                validView?.visibility = View.INVISIBLE
                errorView?.visibility = View.INVISIBLE
                textInputLayout.error = ""

                textInputLayout.setHintTextAppearance(R.style.textinputlayout_login)
                editText.supportBackgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(ctx, R.color.azure))
            }

            is FormInputState.Error -> {
                validView?.visibility = View.INVISIBLE
                errorView?.visibility = View.VISIBLE
                textInputLayout.error = ctx.getLocalizedUIMessage(state.message)

                textInputLayout.setHintTextAppearance(R.style.textinputlayout_login_error)
                editText.supportBackgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(ctx, R.color.error_color))

                disableSubmitButton()
            }
        }
    }

    fun setHintTextAppearance(resId: Int) = textInputLayout.setHintTextAppearance(resId)
}