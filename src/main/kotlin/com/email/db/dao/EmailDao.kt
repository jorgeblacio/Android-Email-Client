package com.email.db.dao

import android.arch.persistence.room.*
import com.email.db.DeliveryTypes
import com.email.db.models.Email
import java.util.*

/**
 * Created by sebas on 1/24/18.
 */

@Dao interface EmailDao {

    @Insert
    fun insertAll(emails : List<Email>)

    @Query("SELECT * FROM email")
    fun getAll() : List<Email>

    @Query("""SELECT * from email e
            WHERE date=(SELECT MAX(date) FROM email d
            WHERE d.threadId=e.threadId) GROUP BY threadId
            ORDER BY date DESC
            """)
    fun getLatestEmails() : List<Email>

    @Query("""
            SELECT * FROM email e
            WHERE date=(SELECT MAX(date) FROM email d
            WHERE d.threadId=e.threadId) AND id
            IN (SELECT DISTINCT emailId
            FROM email_label) GROUP BY threadId
            ORDER BY date DESC
            """)
    fun getNotArchivedEmailThreads() : List<Email>

    @Query("""
        select email.*,CASE WHEN email.threadId = "" THEN email.id ELSE email.threadId END as uniqueId,
        group_concat(email_label.labelId) as allLabels,
        max(email.unread) as unread, max(email.date)
        from email
        left join email_label on email.id = email_label.emailId
        and date < :starterDate
        where not exists
        (select * from email_label where email_label.emailId = email.id and email_label.labelId in (:rejectedLabels))
        group by uniqueId
        having coalesce(allLabels, "") like :selectedLabel
        order by date DESC limit :limit
            """)
    fun getEmailThreadsFromMailboxLabel(
            starterDate: Date,
            rejectedLabels: List<Long>,
            selectedLabel: String,
            limit: Int ): List<Email>

    @Query("""
        select email.*,CASE WHEN email.threadId = "" THEN email.id ELSE email.threadId END as uniqueId,
        max(email.unread) as unread, max(email.date),
        group_concat(distinct(contact.name)) as contactNames,
        group_concat(distinct(contact.email)) as contactEmails
        from email
        inner join email_label on email.id = email_label.emailId
        left join email_contact on email.id = email_contact.emailId
        left join contact on email_contact.contactId = contact.id
        and date < :starterDate
        where not exists
        (select * from email_label where email_label.emailId = email.id and email_label.labelId in (:rejectedLabels))
        group by uniqueId
        having contactNames like :queryText
        or contactEmails like :queryText
        or preview like :queryText
        or content like :queryText
        or subject like :queryText
        order by date DESC limit :limit
        """)
    fun searchEmailThreads(
            starterDate: Date,
            queryText: String,
            rejectedLabels: List<Long>,
            limit: Int): List<Email>

    @Query("""
        SELECT * FROM email e
        WHERE threadId=:threadId AND date=(SELECT MAX(date)
        FROM email d WHERE d.threadId=:threadId)
        GROUP BY threadId
        LIMIT 1
            """)
    fun getLatestEmailFromThreadId(
            threadId: String): Email

    @Delete
    fun deleteAll(emails: List<Email>)

    @Query("""UPDATE email
            SET unread=:unread
            where id in (:ids)""")
    fun toggleRead(ids: List<Long>, unread: Boolean)

    @Query("""UPDATE email
            SET threadId=:threadId,
            messageId=:messageId,
            date=:date,
            delivered=:status
            where id=:id""")
    fun updateEmail(id: Long, threadId: String, messageId: String, date: Date, status: DeliveryTypes)

    @Update
    fun update(emails: List<Email>)

    @Query("""SELECT * FROM email
            left join email_label on email.id = email_label.emailId
            WHERE threadId=:threadId
            AND NOT EXISTS
            (SELECT * FROM email_label WHERE email_label.emailId = email.id and email_label.labelId IN (:rejectedLabels))
            GROUP BY email.messageId,email.threadId
            ORDER BY date ASC""")
    fun getEmailsFromThreadId(threadId: String, rejectedLabels: List<Long>): List<Email>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(email: Email): Long

    @Query("""UPDATE email
            SET delivered=:deliveryType
            where id=:id""")
    fun changeDeliveryType(id: Long, deliveryType: DeliveryTypes)

    @Query("""
        select email.*,CASE WHEN email.threadId = "" THEN email.id ELSE email.threadId END as uniqueId,
        group_concat(email_label.labelId) as allLabels,
        max(email.unread) as unread, max(email.date)
        from email
        left join email_label on email.id = email_label.emailId
        where not exists
        (select * from email_label where email_label.emailId = email.id and email_label.labelId in (:rejectedLabels))
        group by uniqueId
        having coalesce(allLabels, "") like :selectedLabel
        order by date DESC limit :limit
        """)
    fun getInitialEmailThreadsFromMailboxLabel(
            rejectedLabels: List<Long>,
            selectedLabel: String,
            limit: Int): List<Email>

    @Query("""
        select email.*,CASE WHEN email.threadId = "" THEN email.id ELSE email.threadId END as uniqueId,
        max(email.unread) as unread, max(email.date),
        group_concat(distinct(contact.name)) as contactNames,
        group_concat(distinct(contact.email)) as contactEmails
        from email
        inner join email_label on email.id = email_label.emailId
        left join email_contact on email.id = email_contact.emailId
        left join contact on email_contact.contactId = contact.id
        where not exists
        (select * from email_label where email_label.emailId = email.id and email_label.labelId in (:rejectedLabels))
        group by uniqueId
        having contactNames like :queryText
        or contactEmails like :queryText
        or preview like :queryText
        or content like :queryText
        or subject like :queryText
        order by date DESC limit :limit
        """)
    fun searchInitialEmailThreads(
            queryText: String,
            rejectedLabels: List<Long>,
            limit: Int): List<Email>

    @Query("""
        select email.*, CASE WHEN email.threadId = "" THEN email.id ELSE email.threadId END as uniqueId,
        group_concat(email_label.labelId) as allLabels,
        max(email.unread) as unread
        from email
        left join email_label on email.id = email_label.emailId
        where not exists
        (select * from email_label where email_label.emailId = email.id and email_label.labelId in (:rejectedLabels))
        and unread = 1
        group by uniqueId
        having coalesce(allLabels, "") like :selectedLabel
        """)
    fun getTotalUnreadThreads(rejectedLabels: List<Int>, selectedLabel: String): List<Email>

    @Query("""
        select email.*, CASE WHEN email.threadId = "" THEN email.id ELSE email.threadId END as uniqueId,
        group_concat(email_label.labelId) as allLabels,
        max(email.unread) as unread
        from email
        left join email_label on email.id = email_label.emailId
        group by uniqueId
        having coalesce(allLabels, "") like :selectedLabel
        """)
    fun getTotalThreads(selectedLabel: String): List<Email>

    @Query("""
        select count(distinct(email.id)) from email
        left join email_label on email.id = email_label.emailId
        where threadId=:threadId
        and not exists
        (select * from email_label where email_label.emailId = email.id and email_label.labelId in (:rejectedLabels))
        """)
    fun getTotalEmailsByThread(threadId: String, rejectedLabels: List<Long>): Int

    @Query("DELETE from email WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE from email WHERE threadId in (:threadIds)")
    fun deleteThreads(threadIds: List<String>)

}
