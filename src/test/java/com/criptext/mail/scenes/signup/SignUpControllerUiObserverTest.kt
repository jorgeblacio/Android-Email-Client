package com.criptext.mail.scenes.signup

import com.criptext.mail.R
import com.criptext.mail.scenes.signup.data.SignUpRequest
import com.criptext.mail.scenes.signup.holders.SignUpLayoutState
import com.criptext.mail.validation.FormInputState
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.validation.ProgressButtonState
import com.criptext.mail.validation.TextInput
import io.mockk.*
import org.amshove.kluent.`should equal`
import org.junit.Before
import org.junit.Test

/**
 * Created by gabriel on 5/14/18.
 */

class SignUpControllerUiObserverTest: SignUpControllerTest() {

    private val uiObserverSlot = CapturingSlot<SignUpSceneController.SignUpUIObserver>()
    private val modelSlot = CapturingSlot<SignUpSceneModel>()

    @Before
    override fun setUp() {
        super.setUp()
        // mock SignInScene capturing the UI Observer
        every { scene.initLayout(capture(modelSlot), capture(uiObserverSlot)) } just Runs
        controller.onStart(null)
        clearMocks(scene)
    }

    @Test
    fun `after typing username should send throttled request to check availability`() {

        val runnableSlot = CapturingSlot<Runnable>()
        every { throttler.push(capture(runnableSlot)) } just Runs

        uiObserverSlot.captured.onUsernameChangedListener("tester")

        runnableSlot.captured.run()
        sentRequests `should equal` mutableListOf(SignUpRequest.CheckUserAvailability("tester"))

    }

    @Test
    fun `after typing username should update the username field in the model, sanitizing it`() {
        every { throttler.push(any()) } just Runs

        uiObserverSlot.captured.onUsernameChangedListener("TeSter")

        model.username `should equal` TextInput(value = "tester", state = FormInputState.Unknown())
    }

    @Test
    fun `after typing invalid username should show username error`() {
        every { throttler.push(any()) } just Runs

        model.username = TextInput(value = "tes", state = FormInputState.Unknown())

        uiObserverSlot.captured.onUsernameChangedListener("tes#")

        val expectedState = FormInputState.Error(UIMessage(R.string.username_invalid_error))
        verify {
            scene.setInputState(any(), expectedState)
        }
        model.username `should equal` TextInput(value = "tes#", state = expectedState)

    }

    @Test
    fun `after typing password should update the password field in the model`() {
        uiObserverSlot.captured.onPasswordChangedListener("pass")

        model.password `should equal` "pass"
    }

    @Test
    fun `after typing confirm password should update the confirm password field in the model, sanitizing it`() {
        uiObserverSlot.captured.onConfirmPasswordChangedListener("pass")

        model.confirmPassword `should equal` "pass"
    }

    @Test
    fun `after typing matching passwords should show password success`() {
        clearMocks(scene)
        model.state = SignUpLayoutState.Password("")
        uiObserverSlot.captured.onPasswordChangedListener("securepassword123")

        verify { scene.setPasswordCheck(true, true) }
    }

    @Test
    fun `after typing mismatching passwords should show password error`() {
        uiObserverSlot.captured.onPasswordChangedListener("securepassword123")

        clearMocks(scene)
        model.state = SignUpLayoutState.ConfirmPassword("")

        uiObserverSlot.captured.onConfirmPasswordChangedListener("securepassword12")

        verify { scene.setConfirmPasswordCheck(FormInputState.Error(UIMessage(R.string.password_mismatch_error))) }
    }
}