package com.email.scenes

import com.email.scenes.composer.data.ComposerInputData

/**
 * Created by gabriel on 4/3/18.
 */

sealed class ActivityMessage {
    data class SendMail(val emailId: Long,
                        val threadId: String?,
                        val composerInputData: ComposerInputData): ActivityMessage()
    data class AddAttachments(val filepaths: List<String>): ActivityMessage()
    data class UpdateUnreadStatusThread(val threadId: String, val unread: Boolean): ActivityMessage()
    data class UpdateLabelsThread(val threadId: String, val selectedLabelIds: List<Long>): ActivityMessage()
    data class MoveThread(val threadId: String?): ActivityMessage()
}