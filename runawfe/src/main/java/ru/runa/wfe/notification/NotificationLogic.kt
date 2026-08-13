package ru.runa.wfe.notification

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import ru.runa.wfe.R
import ru.runa.wfe.data.PreferencesManager
import ru.runa.wfe.notification.NotificationHelpers.NotificationType
import ru.runa.wfe.rest.ApiClient
import ru.runa.wfe.rest.TokenManager
import ru.runa.wfe.restapi.model.MessageAddedBroadcast
import ru.runa.wfe.restapi.model.WfChatRoom
import ru.runa.wfe.restapi.model.WfePagedListFilter
import ru.runa.wfe.restapi.model.WfeTask
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class NotificationLogic(
    private val context: Context,
    private val notificationHelpers: NotificationHelpers) {

    suspend fun collectNotificationData() {
        if (NotificationHelpers.isChannelEnabled(NotificationType.TASK, context)) {
            checkNewTasksAndNotify()
        }
        if (NotificationHelpers.isChannelEnabled(NotificationType.MESSAGE, context)) {
            checkNewChatMessagesAndNotify()
        }
    }

    suspend fun checkNewChatMessagesAndNotify() {
        val chatRooms: List<WfChatRoom>? = ApiClient.chatService.getChatRoomsUsingGET().body()
        if (!chatRooms.isNullOrEmpty()) {
            for (room in chatRooms) {
                val newMessagesCount = room.newMessagesCount?.toInt() ?: 0
                if (newMessagesCount == 0) continue
                val newMessages = checkNewMessages(room, newMessagesCount)
                if (!newMessages.isNullOrEmpty()) {
                    val content = newChatMessagesNotificationContent(room.id, newMessages)
                    notificationHelpers.showNotification(
                        content.title,
                        content.message,
                        NotificationType.MESSAGE
                    )
                }
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    suspend fun checkNewMessages(
        room: WfChatRoom,
        newMessagesCount: Int
    ): List<MessageAddedBroadcast>? {
        try {
            val chatRoomMessages =
                room.id?.let {
                    ApiClient.chatService.getChatMessagesUsingGET(it).body()
                }
            if (chatRoomMessages.isNullOrEmpty() || newMessagesCount > chatRoomMessages.size) {
                return null
            }
            val newMessages = ArrayList<MessageAddedBroadcast>()
            for (i in 0 until newMessagesCount) {
                val message = chatRoomMessages[i]
                if (message.author?.name != TokenManager.sub) {
                    newMessages.add(message)
                }
            }
            return newMessages
        } catch (ex: Exception) {
            Log.e(this::class.simpleName, ex.message.toString())
            return null
        }
    }

    /*
    * Null and empty checks of list are performed in the caller function
    * */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun newChatMessagesNotificationContent(
        roomId: Long?,
        newMessages: List<MessageAddedBroadcast>
    ): NotificationContent {
        val title: String
        val notificationMessage: String
        val basicTitle = if (roomId != null) {
            "${context.getString(R.string.messages_chat_id_template, roomId)}:"
        } else {
            context.getString(R.string.new_data_notifications)
        }

        if (newMessages.size > 1) {
            title = "$basicTitle ${
                context.resources.getQuantityString(
                    R.plurals.messages_count,
                    newMessages.size,
                    newMessages.size
                )
            }"
            notificationMessage = StringBuilder().apply {
                for (newMessage in newMessages) {
                    appendLine("${newMessage.author?.name}: ${newMessage.text}")
                }
            }.toString()
        } else {
            title = basicTitle
            notificationMessage = newMessages[0].text.toString()
        }

        return NotificationContent(title, notificationMessage)
    }

    suspend fun checkNewTasksAndNotify() {
        val tasks: List<WfeTask>? = ApiClient.taskService.getMyTasksUsingPOST(
            WfePagedListFilter()
        ).body()?.data

        val newTasks = checkNewTasks(tasks)
        if (!newTasks.isNullOrEmpty()) {
            val content = newTasksNotificationContent(newTasks)
            notificationHelpers.showNotification(
                content.title, content.message,
                NotificationType.TASK
            )
        }
        lastTasksCheck = OffsetDateTime.now(ZoneOffset.UTC)
        PreferencesManager.getInstance(context).setKey(
            PreferencesManager.LAST_CHECK,
            lastTasksCheck.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun checkNewTasks(tasks: List<WfeTask>?): List<WfeTask>? {
        if (!tasks.isNullOrEmpty()) {
            val newTasks = ArrayList<WfeTask>()
            for (task in tasks) {
                // New variable because smartcast won't work with custom getter
                val assignDate = task.assignDate
                if (assignDate != null &&
                    lastTasksCheck.isBefore(assignDate)
                ) {
                    newTasks.add(task)
                }
            }
            return newTasks
        }
        return null
    }

    /*
    * Null and empty checks of list are performed in the caller function
    * */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun newTasksNotificationContent(newTasks: List<WfeTask>): NotificationContent {
        val title = "${context.getString(R.string.new_data_notifications)} ${
            context.resources.getQuantityString(
                R.plurals.tasks_count,
                newTasks.size,
                newTasks.size
            )
        }"

        val notificationMessage: String = if (newTasks.size > 1) {
            StringBuilder().apply {
                for (newTask in newTasks) {
                    appendLine("${newTask.name}")
                }
            }.toString()
        } else {
            newTasks[0].name.toString()
        }

        return NotificationContent(title, notificationMessage)
    }

    data class NotificationContent(
        val title: String,
        val message: String
    )

    companion object {
        var lastTasksCheck: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
    }
}