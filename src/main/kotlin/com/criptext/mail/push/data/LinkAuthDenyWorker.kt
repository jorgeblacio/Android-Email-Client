package com.criptext.mail.push.data

import com.criptext.mail.R
import com.criptext.mail.api.HttpClient
import com.criptext.mail.api.models.UntrustedDeviceInfo
import com.criptext.mail.bgworker.BackgroundWorker
import com.criptext.mail.bgworker.ProgressReporter
import com.criptext.mail.db.models.ActiveAccount
import com.criptext.mail.utils.UIMessage
import com.criptext.mail.utils.generaldatasource.data.GeneralAPIClient
import com.criptext.mail.utils.generaldatasource.data.GeneralResult
import com.criptext.mail.utils.sha256
import com.github.kittinunf.result.Result

class LinkAuthDenyWorker(private val deviceId: String,
                         private val notificationId: Int,
                         private val activeAccount: ActiveAccount,
                         private val httpClient: HttpClient,
                         override val publishFn: (PushResult.LinkDeny) -> Unit
                          ) : BackgroundWorker<PushResult.LinkDeny> {

    override val canBeParallelized = true

    private val apiClient = GeneralAPIClient(httpClient, activeAccount.jwt)

    override fun catchException(ex: Exception): PushResult.LinkDeny {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun work(reporter: ProgressReporter<PushResult.LinkDeny>)
            : PushResult.LinkDeny? {

        val operation = Result.of {
            apiClient.postLinkDeny(deviceId)
        }

        return when (operation){
            is Result.Success -> {
                PushResult.LinkDeny.Success(notificationId)
            }
            is Result.Failure -> {
                PushResult.LinkDeny.Failure(UIMessage(R.string.server_error_exception))
            }
        }
    }

    override fun cancel() {
        TODO("not implemented")
    }
}