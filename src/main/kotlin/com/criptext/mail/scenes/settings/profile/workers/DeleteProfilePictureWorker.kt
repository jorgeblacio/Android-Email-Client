package com.criptext.mail.scenes.settings.profile.workers

import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.HttpErrorHandlingHelper
import com.criptext.mail.api.ServerErrorException
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.KeyValueStorage
import com.criptext.mail.db.dao.AccountDao
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.scenes.settings.data.SettingsAPIClient
import com.criptext.mail.scenes.settings.profile.data.ProfileAPIClient
import com.criptext.mail.scenes.settings.profile.data.ProfileResult
import com.criptext.mail.utils.ServerCodes
import com.criptext.mail.utils.UIMessage
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.mapError
import java.io.File


class DeleteProfilePictureWorker(val httpClient: HttpClient,
                                 val activeAccount: ActiveAccount,
                                 private val storage: KeyValueStorage,
                                 private val accountDao: AccountDao,
                                 override val publishFn: (ProfileResult) -> Unit)
    : BackgroundWorker<ProfileResult.SetProfilePicture> {

    private val apiClient = ProfileAPIClient(httpClient, activeAccount.jwt)

    override val canBeParallelized = false

    override fun catchException(ex: Exception): ProfileResult.SetProfilePicture {
        return if(ex is ServerErrorException) {
            when(ex.errorCode) {
                ServerCodes.MethodNotAllowed -> ProfileResult.SetProfilePicture.Failure(UIMessage(R.string.message_warning_two_fa), ex)
                else -> ProfileResult.SetProfilePicture.Failure(UIMessage(R.string.server_error_exception), ex)
            }
        }else {
            ProfileResult.SetProfilePicture.Failure(UIMessage(R.string.server_error_exception), ex)
        }
    }

    override fun work(reporter: ProgressReporter<ProfileResult.SetProfilePicture>): ProfileResult.SetProfilePicture? {
        val result =  workOperation()

        val sessionExpired = HttpErrorHandlingHelper.didFailBecauseInvalidSession(result)

        val finalResult = if(sessionExpired)
            newRetryWithNewSessionOperation()
        else
            result

        return when (finalResult) {
            is Result.Success -> ProfileResult.SetProfilePicture.Success()

            is Result.Failure -> catchException(finalResult.error)
        }
    }

    private fun workOperation() : Result<String, Exception> = Result.of {
        apiClient.deleteProfilePicture().body
    }

    private fun newRetryWithNewSessionOperation()
            : Result<String, Exception> {
        val refreshOperation =  HttpErrorHandlingHelper.newRefreshSessionOperation(apiClient, activeAccount, storage, accountDao)
                .mapError(HttpErrorHandlingHelper.httpExceptionsToNetworkExceptions)
        return when(refreshOperation){
            is Result.Success -> {
                val account = ActiveAccount.loadFromStorage(storage)!!
                apiClient.token = account.jwt
                workOperation()
            }
            is Result.Failure -> {
                Result.of { throw refreshOperation.error }
            }
        }
    }

    override fun cancel() {
        TODO("not implemented") //To change body of created functions use CRFile | Settings | CRFile Templates.
    }
}