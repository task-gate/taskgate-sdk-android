package com.taskgate.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * TaskGate Partner SDK
 * 
 * ## Usage
 * 
 * ### 1. Initialize (Application.kt)
 * ```kotlin
 * TaskGateSDK.initialize(this, "provider_id")
 * ```
 * 
 * ### 2. Handle Intents (MainActivity.kt)
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     TaskGateSDK.handleIntent(intent)
 * }
 * 
 * override fun onNewIntent(intent: Intent) {
 *     super.onNewIntent(intent)
 *     TaskGateSDK.handleIntent(intent)
 * }
 * ```
 * 
 * ### 3. Set Callback for Warm Start (Flutter apps)
 * ```kotlin
 * // In configureFlutterEngine:
 * TaskGateSDK.setTaskCallback { task ->
 *     // Notify Flutter via MethodChannel
 *     channel.invokeMethod("onTaskReceived", mapOf(
 *         "taskId" to task.taskId,
 *         "appName" to task.appName
 *     ))
 * }
 * ```
 * 
 * ### 4. Check for Task on Cold Start (Flutter)
 * ```dart
 * final task = await channel.invokeMethod('getPendingTask');
 * if (task != null) { /* navigate to task */ }
 * ```
 * 
 * ### 5. Report Completion
 * ```kotlin
 * TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.OPEN)
 * ```
 */
object TaskGateSDK {
    private const val TAG = "TaskGateSDK"
    
    private var context: Context? = null
    private var providerId: String? = null
    private var pendingTask: TaskInfo? = null
    private var taskCallback: ((TaskInfo) -> Unit)? = null
    
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
        Log.d(TAG, "Initialized for provider: $providerId")
    }
    
    /**
     * Set callback for warm start task notifications.
     * Called when a task arrives while app is already running.
     * 
     * If a task is already pending (received before callback was set),
     * the callback will be invoked immediately with that task.
     * 
     * For Flutter apps, use this to notify Flutter via MethodChannel.
     */
    @JvmStatic
    fun setTaskCallback(callback: ((TaskInfo) -> Unit)?) {
        this.taskCallback = callback
        Log.d(TAG, "Task callback ${if (callback != null) "set" else "cleared"}")
        
        // If there's a pending task and callback was just set, deliver it now
        if (callback != null && pendingTask != null) {
            Log.d(TAG, "Delivering pending task to newly registered callback: ${pendingTask!!.taskId}")
            callback.invoke(pendingTask!!)
        }
    }
    
    /**
     * Handle intent from onCreate() or onNewIntent().
     * Returns true if this was a TaskGate deep link.
     * 
     * On warm start (onNewIntent), automatically calls the task callback if set.
     */
    @JvmStatic
    fun handleIntent(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.path?.contains("taskgate") != true) return false
        
        val taskId = uri.getQueryParameter("task_id") ?: return false
        val callbackUrl = uri.getQueryParameter("callback_url") ?: return false
        
        val task = TaskInfo(
            taskId = taskId,
            appName = uri.getQueryParameter("app_name"),
            sessionId = uri.getQueryParameter("session_id") ?: java.util.UUID.randomUUID().toString().take(8),
            callbackUrl = callbackUrl
        )
        
        pendingTask = task
        Log.d(TAG, "Task received: $taskId")
        
        // Notify callback (for warm start)
        taskCallback?.invoke(task)
        
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
