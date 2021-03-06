package com.criptext.mail.scenes.signup.data

import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.HttpResponseData
import com.criptext.mail.signal.PreKeyBundleShareData
import com.criptext.mail.scenes.signup.IncompleteAccount
import org.json.JSONObject

/**
 * Created by sebas on 2/26/18.
 */

class SignUpAPIClient(private val httpClient: HttpClient) {

    fun createUser(
            account: IncompleteAccount,
            keyBundle : PreKeyBundleShareData.UploadBundle
    ): HttpResponseData {
        val jsonObject = JSONObject()
        jsonObject.put("name", account.name)
        jsonObject.put("password", account.password)
        jsonObject.put("recipientId", account.username)
        jsonObject.put("keybundle", keyBundle.toJSON())
        jsonObject.put("captchaKey", account.captchaKey)
        jsonObject.put("captchaAnswer", account.captchaAnswer)
        if (account.recoveryEmail != null)
            jsonObject.put("recoveryEmail", account.recoveryEmail)

        return httpClient.post("/user", null, jsonObject)
    }

    fun userCanLogin(username: String, domain: String): HttpResponseData {
        return httpClient.get("/user/canlogin?username=$username&domain=$domain", null)
    }

    fun isUsernameAvailable(username: String): HttpResponseData {
        return httpClient.get("/user/available?username=$username", null)
    }

    fun isRecoveryEmailAvailable(username: String, recoveryEmail: String): HttpResponseData {
        val jsonObject = JSONObject()
        jsonObject.put("email", recoveryEmail)
        jsonObject.put("username", username)
        return httpClient.post("/user/recovery/check", null, jsonObject)
    }

    fun postForgotPassword(recipientId: String, domain: String): HttpResponseData{
        val jsonPut = JSONObject()
        jsonPut.put("recipientId", recipientId)
        jsonPut.put("domain", domain)

        return httpClient.post(path = "/user/password/reset", authToken = null, body = jsonPut)
    }

    fun getDeviceList(tempToken: String): HttpResponseData{
        return httpClient.get(path = "/device/list", authToken = tempToken)
    }

    fun getMaxDevices(tempToken: String): HttpResponseData{
        return httpClient.get(path = "/device/max", authToken = tempToken)
    }

    fun deleteDevices(devicesIds: List<Int>, token: String): HttpResponseData{
        return httpClient.delete(path = "/device/bulk?${devicesIds.joinToString(separator = "&") { "deviceId=$it" }}", authToken = token, body = JSONObject())
    }

    fun putFirebaseToken(pushToken: String, jwt: String): HttpResponseData {
        val jsonPut = JSONObject()
        jsonPut.put("devicePushToken", pushToken)

        return httpClient.put(path = "/keybundle/pushtoken", authToken = jwt, body = jsonPut)
    }

    fun postTwoFAGenerateCode(recipientId: String, domain: String, jwt: String): HttpResponseData {
        val jsonPut = JSONObject()
        jsonPut.put("recipientId", recipientId)
        jsonPut.put("domain", domain)
        return httpClient.post(path = "/user/2fa/generatecode", authToken = jwt, body = jsonPut)
    }

    fun postValidateTwoFACode(recipientId: String, domain: String, jwt: String, code: String): HttpResponseData {
        val json = JSONObject()
        json.put("code", code)
        json.put("recipientId", recipientId)
        json.put("domain", domain)
        return httpClient.post(path = "/user/2fa/validatecode", authToken = jwt, body = json)
    }

    fun postKeybundle(bundle: PreKeyBundleShareData.UploadBundle, jwt: String): HttpResponseData {
        return httpClient.post(path = "/keybundle", body = bundle.toJSON(), authToken = jwt)
    }

    fun getCaptcha(): HttpResponseData {
        return httpClient.get(path = "/user/captcha", authToken = null)
    }
}
