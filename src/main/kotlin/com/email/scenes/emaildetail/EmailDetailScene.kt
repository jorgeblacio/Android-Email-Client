package com.email.scenes.emaildetail

import android.support.v7.widget.RecyclerView
import android.view.View
import com.email.IHostActivity
import com.email.R
import com.email.db.models.FullEmail
import com.email.scenes.emaildetail.ui.FullEmailListAdapter
import com.email.scenes.emaildetail.ui.FullEmailRecyclerView
import com.email.utils.VirtualList

/**
 * Created by sebas on 3/12/18.
 */

interface EmailDetailScene {

    fun attachView(
            fullEmailEventListener: FullEmailListAdapter.OnFullEmailEventListener,
            fullEmailList : VirtualList<FullEmail>)

    class EmailDetailSceneView(
            private val emailDetailView: View,
            val hostActivity: IHostActivity)
        : EmailDetailScene {

        private val context = emailDetailView.context

        private lateinit var fullEmailsRecyclerView: FullEmailRecyclerView

        private val recyclerView: RecyclerView by lazy {
            emailDetailView.findViewById<RecyclerView>(R.id.emails_detail_recycler)
        }

        override fun attachView(
                fullEmailEventListener: FullEmailListAdapter.OnFullEmailEventListener,
                fullEmailList : VirtualList<FullEmail> ){
            fullEmailsRecyclerView = FullEmailRecyclerView(
                    recyclerView,
                    fullEmailEventListener,
                    fullEmailList)
        }
    }
}
