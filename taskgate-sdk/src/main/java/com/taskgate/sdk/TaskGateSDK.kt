package com.taskgate.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * TaskGate Partner SDK for Android
 * 
 * Allows partner apps to:
 * - Receive task requests from TaskGate
 * - Signal when app is ready (cold boot complete)
 * - Report task completion status
 * 
 * Usage:
 * ```kotlin
 * // Initialize in Application.onCreate()
 * TaskGateSDK.initialize(this, "your_provider_id")
 * 
 * // When your app is ready to show the task
 * TaskGateSDK.notifyReady()
 * 
 * // When task is completed
 * TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.OPEN)
 * ```
 */
object TaskGateSDK {
    private const val TAG = "TaskGateSDK"
    private const val TASKGATE_SCHEME = "taskgate"
    
    private var applicationContext: Context? = null
    private var providerId: String? = null
    private var currentSessionId: String? = null
    private var callbackUrl: String? = null
    private var currentTaskId: String? = null
    private var pendingTaskInfo: TaskInfo? = null
    
    /**
     * Task completion status
     */
    enum class CompletionStatus(val value: String) {
        /** User completed task and wants to open the blocked app */
        OPEN("open"),
        /** User completed task but wants to stay focused */
        FOCUS("focus"),
        /** User cancelled the task */
        CANCELLED("cancelled")
    }
    
    /**
     * Task information received from TaskGate
     */
    data class TaskInfo(
        val taskId: String,
        val sessionId: String,
        val callbackUrl: String,
        val appName: String?,
        val additionalParams: Map<String, String>
    )
    
    /**
     * Listener for TaskGate events
     */
    interface TaskGateListener {
        /** Called when a task request is received from TaskGate */
        fun onTaskReceived(taskInfo: TaskInfo)
        
        /** Called when TaskGate requests a specific task by ID */
        fun onTaskRequested(taskId: String, params: Map<String, String>)
    }
    
    private var listener: TaskGateListener? = null
    
    /**
     * Initialize the SDK. Call this in Application.onCreate()
     * 
     * @param context Application context
     * @param providerId Your unique provider ID (assigned by TaskGate)
     */
    fun initialize(context: Context, providerId: String) {
        this.applicationContext = context.applicationContext
        this.providerId = providerId
        Log.d(TAG, "TaskGate SDK initialized for provider: $providerId")
    }
    
    /**
     * Set the listener for TaskGate events
     */
    fun setListener(listener: TaskGateListener?) {
        this.listener = listener
    }
    
    /**
     * Parse an incoming deep link from TaskGate
     * Call this in your Activity's onCreate() and onNewIntent()
     * 
     * @param intent The incoming intent
     * @return true if the intent was handled by TaskGate SDK
     */
    fun handleIntent(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        return handleUri(uri)
    }
    
    /**
     * Parse an incoming URI from TaskGate
     * 
     * @param uri The incoming URI
     * @return true if the URI was handled by TaskGate SDK
     */
    fun handleUri(uri: Uri): Boolean {
        // Check if this is a TaskGate request
        // Expected format: https://yourdomain.com/taskgate/start?task_id=xxx&callback_url=xxx&session_id=xxx
        val path = uri.path ?: ""
        if (!path.contains("taskgate")) {
            return false
        }
        
        val taskId = uri.getQueryParameter("task_id")
        val callbackUrl = uri.getQueryParameter("callback_url")
        val sessionId = uri.getQueryParameter("session_id") ?: generateSessionId()
        val appName = uri.getQueryParameter("app_name")
        
        if (taskId == null || callbackUrl == null) {
            Log.w(TAG, "Missing required parameters: task_id or callback_url")
            return false
        }
        
        // Store session info
        this.currentTaskId = taskId
        this.currentSessionId = sessionId
        this.callbackUrl = callbackUrl
        
        Log.d(TAG, "[STEP 1] handleUri() - Received deep link: taskId=$taskId, sessionId=$sessionId")
        
        // Collect additional params
        val additionalParams = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { key ->
            if (key !in listOf("task_id", "callback_url", "session_id", "app_name")) {
                uri.getQueryParameter(key)?.let { additionalParams[key] = it }
            }
        }
        
        // Store task info - will be delivered when notifyReady() is called
        val taskInfo = TaskInfo(
            taskId = taskId,
            sessionId = sessionId,
            callbackUrl = callbackUrl,
            appName = appName,
            additionalParams = additionalParams
        )
        
        pendingTaskInfo = taskInfo
        Log.d(TAG, "[STEP 2] handleUri() - Task STORED in pendingTaskInfo. NOT delivered yet.")
        Log.d(TAG, "[STEP 2] Waiting for notifyReady() to be called...")
        
        return true
    }
    
    /**
     * Notify TaskGate that the app is ready (cold boot complete)
     * Call this when your task UI is ready to be displayed.
     * 
     * This will:
     * 1. Deliver the pending task info to your listener via onTaskReceived()
     * 2. Signal TaskGate to dismiss the redirect screen
     */
    fun notifyReady() {
        val sessionId = currentSessionId ?: run {
            Log.w(TAG, "No active session - cannot notify ready")
            return
        }
        
        Log.d(TAG, "[STEP 3] notifyReady() called - App says it's ready")
        
        // Deliver pending task to listener
        pendingTaskInfo?.let { taskInfo ->
            Log.d(TAG, "[STEP 4] NOW delivering task to listener: taskId=${taskInfo.taskId}")
            Log.d(TAG, "[STEP 4] Calling onTaskReceived() NOW (after notifyReady)")
            listener?.onTaskReceived(taskInfo)
            listener?.onTaskRequested(taskInfo.taskId, taskInfo.additionalParams)
            pendingTaskInfo = null
            Log.d(TAG, "[STEP 4] onTaskReceived() completed")
        } ?: run {
            Log.d(TAG, "[STEP 3] No pending task to deliver")
        }
        
        Log.d(TAG, "Notifying TaskGate: app ready (session=$sessionId)")
        
        // Signal TaskGate to dismiss redirect screen
        val uri = Uri.Builder()
            .scheme(TASKGATE_SCHEME)
            .authority("partner-ready")
            .appendQueryParameter("session_id", sessionId)
            .appendQueryParameter("provider_id", providerId)
            .build()
        
        launchUri(uri)
    }
    
    /**
     * Report task completion to TaskGate
     * 
     * @param status The completion status (OPEN, FOCUS, or CANCELLED)
     */
    fun reportCompletion(status: CompletionStatus) {
        val callback = callbackUrl ?: run {
            Log.w(TAG, "No callback URL - cannot report completion")
            return
        }
        
        val sessionId = currentSessionId
        
        Log.d(TAG, "Reporting completion: status=${status.value}, session=$sessionId")
        
        // Build completion URL
        val uri = Uri.parse(callback).buildUpon()
            .appendQueryParameter("status", status.value)
            .appendQueryParameter("provider_id", providerId)
            .apply {
                sessionId?.let { appendQueryParameter("session_id", it) }
                currentTaskId?.let { appendQueryParameter("task_id", it) }
            }
            .build()
        
        launchUri(uri)
        
        // Clear session
        clearSession()
    }
    
    /**
     * Cancel the current task and notify TaskGate
     */
    fun cancelTask() {
        reportCompletion(CompletionStatus.CANCELLED)
    }
    
    /**
     * Get the current session ID if a task is active
     */
    fun getCurrentSessionId(): String? = currentSessionId
    
    /**
     * Get the current task ID if a task is active
     */
    fun getCurrentTaskId(): String? = currentTaskId
    
    /**
     * Check if there's an active task session
     */
    fun hasActiveSession(): Boolean = currentSessionId != null
    
    private fun launchUri(uri: Uri) {
        val context = applicationContext ?: run {
            Log.e(TAG, "SDK not initialized - call initialize() first")
            return
        }
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch URI: $uri", e)
        }
    }
    
    private fun clearSession() {
        currentSessionId = null
        currentTaskId = null
        callbackUrl = null
        pendingTaskInfo = null
    }
    
    private fun generateSessionId(): String {
        return java.util.UUID.randomUUID().toString().take(8)
    }
}
