package com.zzp.blepractise.prototype

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.zzp.blepractise.R

/**
 * 发送通知栏消息的工具
 *
 * Created by zp.zhu on 2020/11/20
 */
object NotificationTool {
    private const val TAG = "NotificationTool"

    private fun isNotificationChannelEnabled(context: Context,channelId: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(channelId)
            channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE &&
                    manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    //前台服务通知，可取消
    fun foregroundNotify(c:Service, isCancel:Boolean, title: String,text: String, id:Int) {
        val CHANNEL_ONE_ID = "CHANNEL_ONE_ID"
        val notificationChannel: NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannel = NotificationChannel(
                CHANNEL_ONE_ID, CHANNEL_ONE_ID, NotificationManager.IMPORTANCE_HIGH
            )
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            val manager = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(notificationChannel)
            val intent = Intent(c, null)
            val pendingIntent = PendingIntent.getActivity(c, 0, intent, 0)
            val notification = Notification.Builder(c).setChannelId(CHANNEL_ONE_ID)
                .setTicker("Nature")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentIntent(pendingIntent)
                .setContentText(text)
                .build()
            notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
            c.startForeground(id, notification)
            if(isCancel)
                c.stopForeground(true)
        }
    }
}