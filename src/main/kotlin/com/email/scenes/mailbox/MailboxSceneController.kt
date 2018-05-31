package com.email.scenes.mailbox

import com.email.IHostActivity
import com.email.R
import com.email.bgworker.WorkHandler
import com.email.db.MailFolders
import com.email.db.models.Contact
import com.email.db.models.Email
import com.email.db.models.Label
import com.email.db.typeConverters.LabelTextConverter
import com.email.scenes.ActivityMessage
import com.email.scenes.labelChooser.LabelDataHandler
import com.email.scenes.labelChooser.SelectedLabels
import com.email.scenes.SceneController
import com.email.scenes.composer.data.ComposerTypes
import com.email.scenes.mailbox.data.*
import com.email.scenes.mailbox.feed.FeedController
import com.email.scenes.mailbox.ui.EmailThreadAdapter
import com.email.scenes.mailbox.ui.MailboxUIObserver
import com.email.scenes.params.ComposerParams
import com.email.scenes.params.EmailDetailParams
import com.email.scenes.params.SearchParams
import com.email.utils.UIMessage
import com.email.websocket.WebSocketEventListener
import com.email.websocket.WebSocketEventPublisher

/**
 * Created by sebas on 1/30/18.
 */
class MailboxSceneController(private val scene: MailboxScene,
                             private val model: MailboxSceneModel,
                             private val host: IHostActivity,
                             private val dataSource: WorkHandler<MailboxRequest, MailboxResult>,
                             private val websocketEvents: WebSocketEventPublisher,
                             private val feedController : FeedController) : SceneController() {

    private val threadListController = ThreadListController(model, scene.virtualListView)
    private val dataSourceListener = { result: MailboxResult ->
        when (result) {
            is MailboxResult.GetSelectedLabels -> dataSourceController.onSelectedLabelsLoaded(result)
            is MailboxResult.UpdateMailbox -> dataSourceController.onMailboxUpdated(result)
            is MailboxResult.LoadEmailThreads -> dataSourceController.onLoadedMoreThreads(result)
            is MailboxResult.SendMail -> dataSourceController.onSendMailFinished(result)
            is MailboxResult.UpdateEmailThreadsLabelsRelations -> dataSourceController.onUpdatedLabels(result)
            is MailboxResult.GetMenuInformation -> dataSourceController.onGetMenuInformation(result)
            is MailboxResult.UpdateUnreadStatus -> dataSourceController.onUpdateUnreadStatus(result)
        }
    }

    private fun getTitleForMailbox() : String{
        return LabelTextConverter().parseLabelTextType(model.selectedLabel.text)
    }

    private val toolbarTitle: String
        get() = if (model.isInMultiSelect) model.selectedThreads.length().toString()
        else getTitleForMailbox()

    override val menuResourceId: Int?
        get() = when {
            !model.isInMultiSelect -> R.menu.mailbox_menu_normal_mode
            model.hasSelectedUnreadMessages -> R.menu.mailbox_menu_multi_mode_unread
            else -> R.menu.mailbox_menu_multi_mode_read
        }

    private fun getTotalUnreadThreads(): Int{
        return model.threads.fold(0, { total, next -> total + (if(next.unread) 1 else 0) })
    }


    private fun reloadMailboxThreads() {
        threadListController.clear()
        val req = MailboxRequest.LoadEmailThreads(
                label = model.selectedLabel.text,
                loadParams = LoadParams.Reset(size = threadsPerPage))
        dataSource.submitRequest(req)
    }

    private val threadEventListener = object : EmailThreadAdapter.OnThreadEventListener {
        override fun onApproachingEnd() {
                val req = MailboxRequest.LoadEmailThreads(
                        label = model.selectedLabel.text,
                        loadParams = LoadParams.NewPage(size = threadsPerPage,
                                oldestEmailThread = model.threads.lastOrNull()))
                dataSource.submitRequest(req)
        }

        override fun onGoToMail(emailThread: EmailThread) {

            if(emailThread.totalEmails == 1 &&
                    emailThread.labelsOfMail.contains(Label.defaultItems.draft)){
                return host.goToScene(ComposerParams(
                        fullEmail = emailThread.latestEmail,
                        composerType = ComposerTypes.CONTINUE_DRAFT
                ), true)
            }
            dataSource.submitRequest(MailboxRequest.UpdateUnreadStatus(listOf(emailThread), false))
            host.goToScene(EmailDetailParams(emailThread.threadId), true)
        }

        override fun onToggleThreadSelection(thread: EmailThread, position: Int) {
            if (!model.isInMultiSelect) {
                changeMode(multiSelectON = true, silent = false)
            }

            val selectedThreads = model.selectedThreads

            if (thread.isSelected) {
                threadListController.unselect(thread, position)
            } else {
                threadListController.select(thread, position)
            }

            if (selectedThreads.isEmpty()) {
                changeMode(multiSelectON = false, silent = false)
            }

            scene.updateToolbarTitle(toolbarTitle)
        }
    }
    private val onDrawerMenuItemListener = object: DrawerMenuItemListener {
        override fun onNavigationItemClick(navigationMenuOptions: NavigationMenuOptions) {
            scene.hideDrawer()

            when(navigationMenuOptions) {
                NavigationMenuOptions.INBOX,
                NavigationMenuOptions.SENT,
                NavigationMenuOptions.DRAFT,
                NavigationMenuOptions.STARRED,
                NavigationMenuOptions.SPAM,
                NavigationMenuOptions.TRASH,
                NavigationMenuOptions.ALL_MAIL -> {
                    scene.showRefresh()
                    model.selectedLabel = navigationMenuOptions.toLabel()!!
                    threadListController.clear()
                    scene.toggleNoMailsView(false)
                    reloadMailboxThreads()
                }
                else -> { /* do nothing */ }
            }

        }
    }

    private val dataSourceController = DataSourceController(dataSource)
    private val observer = object : MailboxUIObserver {

        override fun onBackButtonPressed() {
            if(model.isInMultiSelect){
                changeMode(multiSelectON = false, silent = false)
                threadListController.reRenderAll()
            }
        }

        override fun onRefreshMails() {
            scene.showRefresh()
            dataSourceController.updateMailbox(
                    mailboxLabel = model.selectedLabel)
        }

        override fun onOpenComposerButtonClicked() {
            host.goToScene(ComposerParams(), true)
        }
    }

    val menuClickListener = {
        scene.openNotificationFeed()
    }


    fun changeMode(multiSelectON: Boolean, silent: Boolean) {

        if (!multiSelectON) {
            model.selectedThreads.clear()
        }
        model.isInMultiSelect = multiSelectON
        threadListController.toggleMultiSelectMode(multiSelectON, silent)
        scene.refreshToolbarItems()
        toggleMultiModeBar()
        scene.updateToolbarTitle(toolbarTitle)
    }

    private fun handleActivityMessage(activityMessage: ActivityMessage?): Boolean {
        return when (activityMessage) {
            is ActivityMessage.SendMail -> {
                val newRequest = MailboxRequest.SendMail(activityMessage.emailId, activityMessage.threadId,
                        activityMessage.composerInputData)
                dataSource.submitRequest(newRequest)
                true
            }
            else -> false
        }
    }

    override fun onStart(activityMessage: ActivityMessage?): Boolean {
        dataSourceController.setDataSourceListener()
        scene.attachView(
                mailboxLabel = model.selectedLabel.text,
                threadEventListener = threadEventListener,
                onDrawerMenuItemListener = onDrawerMenuItemListener,
                observer = observer,
                threadList = VirtualEmailThreadList(model))
        scene.initDrawerLayout()

        dataSource.submitRequest(MailboxRequest.GetMenuInformation())
        if (model.threads.isEmpty()) reloadMailboxThreads()

        toggleMultiModeBar()
        scene.setToolbarNumberOfEmails(getTotalUnreadThreads())
        feedController.onStart()

        websocketEvents.listener =  webSocketEventListener

        return handleActivityMessage(activityMessage)
    }

    override fun onStop() {
        websocketEvents.listener = null
        feedController.onStop()
    }

    private fun archiveSelectedEmailThreads() {
        dataSourceController.updateEmailThreadsLabelsRelations(
                selectedLabels = null,
                chosenLabel = null)
    }

    private fun deleteSelectedEmailThreads() {
        dataSourceController.updateEmailThreadsLabelsRelations(
                selectedLabels = null,
                chosenLabel = MailFolders.TRASH)
    }

    private fun toggleReadSelectedEmailThreads(unreadStatus: Boolean) {
        val emailThreads = model.selectedThreads.toList()
        dataSource.submitRequest(MailboxRequest.UpdateUnreadStatus(emailThreads, !unreadStatus))
        changeMode(multiSelectON = false, silent = false)
    }

    private fun showMultiModeBar() {
        val selectedThreadsQuantity: Int = model.selectedThreads.length()
        scene.showMultiModeBar(selectedThreadsQuantity)
    }

    private fun hideMultiModeBar() {
        scene.hideMultiModeBar()
        scene.updateToolbarTitle(toolbarTitle)
    }

    override fun onBackPressed(): Boolean {
        if(model.isInMultiSelect){
            changeMode(multiSelectON = false, silent = false)
            threadListController.reRenderAll()
            return false
        }
        return scene.onBackPressed()
    }

    private fun toggleMultiModeBar() {
        if (model.isInMultiSelect) {
            showMultiModeBar()
        } else {
            hideMultiModeBar()
        }
    }

    override fun onOptionsItemSelected(itemId: Int) {
        when (itemId) {
            R.id.mailbox_search -> host.goToScene(SearchParams(), true)
            R.id.mailbox_archive_selected_messages -> archiveSelectedEmailThreads()
            R.id.mailbox_delete_selected_messages -> deleteSelectedEmailThreads()
            R.id.mailbox_message_toggle_read -> {
                val unreadStatus = model.isInUnreadMode
                toggleReadSelectedEmailThreads(unreadStatus = unreadStatus)
            }
            R.id.mailbox_move_to -> {
                scene.showDialogMoveTo(onMoveThreadsListener)
            }
            R.id.mailbox_add_labels -> {
                showDialogLabelChooser()
            }
        }
    }

    private fun showDialogLabelChooser() {
        val threadIds = model.selectedThreads.toList().map {
            it.threadId
        }
        val req = MailboxRequest.GetSelectedLabels(
                threadIds = threadIds
        )

        dataSource.submitRequest(req)
        scene.showDialogLabelsChooser(LabelDataHandler(this))
    }

    fun createRelationSelectedEmailLabels(selectedLabels: SelectedLabels): Boolean {
        dataSourceController.updateEmailThreadsLabelsRelations(
                selectedLabels,
                null)

        return false
    }

    private val onMoveThreadsListener = object : OnMoveThreadsListener {
        override fun moveToSpam() {
            dataSourceController.updateEmailThreadsLabelsRelations(
                    selectedLabels = null,
                    chosenLabel = MailFolders.SPAM)
        }

        override fun moveToTrash() {
            dataSourceController.updateEmailThreadsLabelsRelations(
                    selectedLabels = null,
                    chosenLabel = MailFolders.TRASH)
        }

    }

    private inner class DataSourceController(
            private val dataSource: WorkHandler<MailboxRequest, MailboxResult>) {

        fun setDataSourceListener() {
            dataSource.listener = dataSourceListener
        }

        fun updateEmailThreadsLabelsRelations(
                selectedLabels: SelectedLabels?,
                chosenLabel: MailFolders?
        ): Boolean {
                val req = MailboxRequest.UpdateEmailThreadsLabelsRelations(
                        selectedEmailThreads = model.selectedThreads.toList(),
                        selectedLabels = selectedLabels,
                        chosenLabel = chosenLabel)

                dataSource.submitRequest(req)
                return true
            }

        fun updateMailbox(mailboxLabel: Label) {
            scene.hideDrawer()
            val req = MailboxRequest.UpdateMailbox(
                    label = mailboxLabel,
                    loadedThreadsCount = model.threads.size
            )
            dataSource.submitRequest(req)
        }

        private fun handleSuccessfulMailboxUpdate(resultData: MailboxResult.UpdateMailbox.Success) {
            model.lastSync = System.currentTimeMillis()
            if (resultData.mailboxThreads != null) {
                threadListController.populateThreads(resultData.mailboxThreads)
                scene.toggleNoMailsView(resultData.mailboxThreads.isEmpty())
                scene.setToolbarNumberOfEmails(getTotalUnreadThreads())
                scene.updateToolbarTitle(toolbarTitle)
                dataSource.submitRequest(MailboxRequest.GetMenuInformation())
            }
        }

        private fun handleFailedMailboxUpdate(resultData: MailboxResult.UpdateMailbox.Failure) {
            scene.showMessage(resultData.message)
        }

        fun onMailboxUpdated(resultData: MailboxResult.UpdateMailbox) {
            scene.clearRefreshing()
            when (resultData) {
                is MailboxResult.UpdateMailbox.Success ->
                    handleSuccessfulMailboxUpdate(resultData)
                is MailboxResult.UpdateMailbox.Failure ->
                    handleFailedMailboxUpdate(resultData)
            }
        }

        fun onLoadedMoreThreads(result: MailboxResult.LoadEmailThreads) {
            scene.clearRefreshing()
            scene.updateToolbarTitle(toolbarTitle)
            when(result) {
                is MailboxResult.LoadEmailThreads.Success -> {
                    val hasReachedEnd = result.emailThreads.size < threadsPerPage
                    if (model.threads.isNotEmpty() && result.isReset)
                        threadListController.populateThreads(result.emailThreads)
                    else
                        threadListController.appendAll(result.emailThreads, hasReachedEnd)
                    scene.setToolbarNumberOfEmails(getTotalUnreadThreads())
                    scene.toggleNoMailsView(result.emailThreads.isEmpty())
                    if (shouldSync)
                        updateMailbox(model.selectedLabel)
                }
            }
        }

        fun onUpdatedLabels(result: MailboxResult.UpdateEmailThreadsLabelsRelations) {
            changeMode(multiSelectON = false, silent = false)
            when(result) {
                is MailboxResult.UpdateEmailThreadsLabelsRelations.Success ->  {
                    reloadMailboxThreads()
                    dataSource.submitRequest(MailboxRequest.GetMenuInformation())
                } else -> {
                    scene.showMessage(UIMessage(R.string.error_updating_labels))
                }
            }
        }

        fun onSendMailFinished(result: MailboxResult.SendMail) {
            when (result) {
                is MailboxResult.SendMail.Success -> {
                    scene.showMessage(UIMessage(R.string.mail_sent_success))
                    dataSource.submitRequest(MailboxRequest.GetMenuInformation())
                }
                is MailboxResult.SendMail.Failure -> scene.showMessage(result.message)
            }
        }

        fun onSelectedLabelsLoaded(result: MailboxResult.GetSelectedLabels) {
            when (result) {
                is MailboxResult.GetSelectedLabels.Success -> {
                    scene.onFetchedSelectedLabels(result.selectedLabels,
                            result.allLabels)
                }
                is MailboxResult.GetSelectedLabels.Failure -> {
                    scene.showMessage(UIMessage(R.string.error_getting_labels))
                }
            }
        }

        fun onGetMenuInformation(result: MailboxResult){
            when (result) {
                is MailboxResult.GetMenuInformation.Success -> {
                    scene.initNavHeader(result.account.name, "${result.account.recipientId}@${Contact.mainDomain}")
                    scene.setCounterLabel(NavigationMenuOptions.INBOX, result.totalInbox)
                    scene.setCounterLabel(NavigationMenuOptions.DRAFT, result.totalDraft)
                    scene.setCounterLabel(NavigationMenuOptions.SPAM, result.totalSpam)
                }
                is MailboxResult.GetMenuInformation.Failure -> {
                    scene.showMessage(UIMessage(R.string.error_getting_counters))
                }
            }
        }

        fun onUpdateUnreadStatus(result: MailboxResult){
            when (result) {
                is MailboxResult.UpdateUnreadStatus.Success -> {
                    reloadMailboxThreads()
                }
                is MailboxResult.UpdateUnreadStatus.Failure -> {
                    scene.showMessage(UIMessage(R.string.error_updating_status))
                }
            }
        }
    }

    private val webSocketEventListener = object : WebSocketEventListener {
        override fun onNewEmail(email: Email) {
            if (model.selectedLabel == Label.defaultItems.inbox) {
                // just reset mailbox
                val req = MailboxRequest.LoadEmailThreads(
                        label = model.selectedLabel.text,
                        loadParams = LoadParams.Reset(size = threadsPerPage))
                dataSource.submitRequest(req)
            }
        }

        override fun onError(uiMessage: UIMessage) {
            scene.showMessage(uiMessage)
        }
    }

    val shouldSync: Boolean
            get() = System.currentTimeMillis() - model.lastSync > minimumIntervalBetweenSyncs

    companion object {
        val threadsPerPage = 20
        val minimumIntervalBetweenSyncs = 60000L
    }

}
