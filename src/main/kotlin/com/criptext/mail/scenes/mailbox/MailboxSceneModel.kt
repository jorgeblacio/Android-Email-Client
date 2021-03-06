package com.criptext.mail.scenes.mailbox

import com.criptext.mail.db.models.Account
import com.criptext.mail.db.models.Label
import com.criptext.mail.email_preview.EmailPreview
import com.criptext.mail.scenes.SceneModel
import com.criptext.mail.scenes.mailbox.feed.FeedModel
import com.google.api.services.drive.Drive

/**
 * Created by sebas on 1/25/18.
 */

class MailboxSceneModel(var showWelcome: Boolean = false, var askForRestoreBackup: Boolean = false) : SceneModel {
    var selectedLabel: Label = Label.defaultItems.inbox // default label
    val threads : ArrayList<EmailPreview> = ArrayList()
    val selectedThreads = SelectedThreads()
    val hasSelectedUnreadMessages: Boolean
        get() = selectedThreads.hasUnreadThreads
    val feedModel = FeedModel()
    val isInUnreadMode: Boolean
        get() = selectedThreads.isInUnreadMode
    var isInMultiSelect = false
    var hasReachedEnd = true
    var lastSync = 0L
    var lastSyncBackground = 0L
    var showOnlyUnread = false
    var extraAccounts = listOf<Account>()
    var waitForAccountSwitch = false
    var mDriveServiceHelper: Drive? = null
    var extraAccountHasUnreads = false
    var isCreateBackup = false
}
