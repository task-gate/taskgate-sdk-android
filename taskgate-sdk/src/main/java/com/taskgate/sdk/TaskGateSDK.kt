package com.taskgate.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * TaskGate Partner SDK
 * 
 * Usage:
 * 1. TaskGateSDK.initialize(context, "provider_id") - in Application
 * 2. TaskGateSDK.handleIntent(intent) - in MainActivity onCreate/onNewIntent
 * 3. TaskGateSDK.getPendingTask() - returns TaskInfo if TaskGate launch, null if normal
 * 4. TaskGateSDK.reportCompletion(status) - when done
 */
object TaskGateSDK {
    private const val TAG = "TaskGateSDK"
    
    private var context: Context? = null
    private var providerId: String? = null
    private var pendingTask: TaskInfo? = null
    
    data class TaskInfo(
        val taskId: String,
        val appName: String?,
        val sessionId: String,
        val callbackUrl: String
    )
    
    enum class CompletionStatus(val value: String) {
        OPEN("open"),
        FOCUS("focus"),
        CANCELLED("cancelled")
    }
    
    @JvmStatic
    fun initialize(context: Context, providerId: String) {
        this.context = context.applicationContext
        this.providerId = providerId
    }
    
    @JvmStatic
    fun handleIntent(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.path?.contains("taskgate") != true) return false
        
        val taskId = uri.getQueryParameter("task_id") ?: return false
        val callbackUrl = uri.getQueryParameter("callback_url") ?: return false
        
        pendingTask = TaskInfo(
            taskId = taskId,
            appName = uri.getQueryParameter("app_name"),
            sessionId = uri.getQueryParameter("session_id") ?: java.util.UUID.randomUUID().toString().take(8),
            callbackUrl = callbackUrl
        )
        Log.d(TAG, "Task received: $taskId")
        return true
    }
    
    @JvmStatic
    fun getPendingTask(): TaskInfo? = pendingTask
    
    @JvmStatic
    fun reportCompletion(status: CompletionStatus) {
        val task = pendingTask ?: return
        
        val uri = Uri.parse(task.callbackUrl).buildUpon()
            .appendQueryParameter("status", status.value)
            .appendQueryParameter("provider_id", providerId)
            .appendQueryParameter("session_id", task.sessionId)
            .appendQueryParameter("task_id", task.taskId)
            .build()
        
        context?.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        
        pendingTask = null
        Log.d(TAG, "Completed: ${status.value}")
    }
}
