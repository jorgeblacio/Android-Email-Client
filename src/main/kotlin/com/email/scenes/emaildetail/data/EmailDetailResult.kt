package com.email.scenes.emaildetail.data

import com.email.db.models.FullEmail
import com.email.db.models.Label
import com.email.scenes.mailbox.data.MailboxResult
import com.email.utils.UIMessage

/**
 * Created by sebas on 3/12/18.
 */

sealed class EmailDetailResult {

    sealed class GetSelectedLabels : EmailDetailResult() {
        class Success(val allLabels: List<Label>,
                      val selectedLabels: List<Label>): GetSelectedLabels()
        class Failure : GetSelectedLabels()
    }

    sealed class LoadFullEmailsFromThreadId: EmailDetailResult() {
        data class Success(val fullEmailList: List<FullEmail>): LoadFullEmailsFromThreadId()
        class Failure: LoadFullEmailsFromThreadId()
    }

    sealed class UnsendFullEmailFromEmailId: EmailDetailResult() {
        data class Success(val position: Int): UnsendFullEmailFromEmailId()
        data class Failure(
                val position: Int,
                val message: UIMessage,
                val exception: Exception): UnsendFullEmailFromEmailId()
    }

    sealed class UpdateUnreadStatus: EmailDetailResult(){
        data class Success(val threadId: String, val unread: Boolean): UpdateUnreadStatus()
        class Failure: UpdateUnreadStatus()
    }

    sealed class ReadEmails: EmailDetailResult(){
        class Success: ReadEmails()
        class Failure: ReadEmails()
    }
    sealed class UpdateEmailThreadsLabelsRelations: EmailDetailResult() {
        data class Success(val threadId: String, val selectedLabelIds: List<Long>): UpdateEmailThreadsLabelsRelations()
        data class Failure(
                val message: UIMessage,
                val exception: Exception) : UpdateEmailThreadsLabelsRelations()
    }

    sealed class MoveEmailThread: EmailDetailResult() {
        data class Success(val threadId: String?): MoveEmailThread()
        data class Failure(
                val message: UIMessage,
                val exception: Exception) : MoveEmailThread()
    }
}
