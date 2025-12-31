package com.taskgate.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * TaskGate Partner SDK for Android
 * 
 * Allows partner apps to:
 * - Receive task requests from TaskGate via deep links
 * - Report task completion status
 * 
 * ## Usage
 * 
 * 1. Initialize in Application.onCreate():
 * ```kotlin
 * TaskGateSDK.initialize(this, "your_provider_id")
 * ```
 * 
 * 2. Handle deep links in MainActivity:
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     TaskGateSDK.handleIntent(intent)  // SDK receives and parses the deep link
 * }
 * 
 * override fun onNewIntent(intent: Intent) {
 *     super.onNewIntent(intent)
 *     TaskGateSDK.handleNewIntent(intent)  // For warm start
 * }
 * ```
 * 
 * 3. Check for pending task (optional, SDK auto-notifies Flutter):
 * ```kotlin
 * if (TaskGateSDK.hasPendingTask()) {
 *     val task = TaskGateSDK.getPendingTaskInfo()
 *     // Navigate to task screen
 * }
 * ```
 * 
 * 4. Report completion when done:
 * ```kotlin
 * TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.OPEN)
 * ```
 */
object TaskGateSDK {
    private const val TAG = "TaskGateSDK"
    private const val PREFS_NAME = "TaskGateSDKPrefs"
    private const val KEY_PENDING_TASK_ID = "pending_task_id"
    private const val KEY_PENDING_SESSION_ID = "pending_session_id"
    private const val KEY_PENDING_CALLBACK_URL = "pending_callback_url"
    private const val KEY_PENDING_APP_NAME = "pending_app_name"
    private const val KEY_PENDING_TIMESTAMP = "pending_timestamp"
    
    // Channel method names for Flutter integration
    const val METHOD_ON_TASK_RECEIVED = "onTaskReceived"
    const val METHOD_GET_PENDING_TASK = "getPendingTask"
    const val METHOD_REPORT_COMPLETION = "reportCompletion"
    
    private var applicationContext: Context? = null
    private var providerId: String? = null
    private var currentSessionId: String? = null
    private var callbackUrl: String? = null
    private var currentTaskId: String? = null
    private var pendingTaskInfo: TaskInfo? = null
    
    // Flutter channel for sending events
    private var flutterChannel: FlutterChannel? = null
    
    /**
     * Interface for sending messages to Flutter.
     * Implement this with your MethodChannel.
     */
    interface FlutterChannel {
        fun invokeMethod(method: String, arguments: Map<String, Any?>)
    }
    
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
     * Initialize the SDK. Call this in Application.onCreate()
     * 
     * @param context Application context
     * @param providerId Your unique provider ID (assigned by TaskGate)
     */
    @JvmStatic
    fun initialize(context: Context, providerId: String) {
        this.applicationContext = context.applicationContext
        this.providerId = providerId
        Log.d(TAG, "TaskGate SDK initialized for provider: $providerId")
    }
    
    /**
     * Set the Flutter channel for SDK-to-Flutter communication.
     * 
     * The SDK will automatically send "onTaskReceived" events to Flutter
     * when a task arrives during warm start.
     * 
     * @param channel The channel to use for sending events to Flutter
     */
    @JvmStatic
    fun setFlutterChannel(channel: FlutterChannel?) {
        this.flutterChannel = channel
        Log.d(TAG, "Flutter channel ${if (channel != null) "set" else "cleared"}")
    }
    
    /**
     * Notify Flutter of a task received event.
     * Called internally when a task arrives during warm start.
     */
    private fun notifyFlutterTaskReceived(taskInfo: TaskInfo) {
        flutterChannel?.let { channel ->
            Log.d(TAG, "Sending onTaskReceived to Flutter: taskId=${taskInfo.taskId}")
            channel.invokeMethod(METHOD_ON_TASK_RECEIVED, mapOf(
                "taskId" to taskInfo.taskId,
                "appName" to taskInfo.appName,
                "sessionId" to taskInfo.sessionId
            ))
        } ?: run {
            Log.w(TAG, "Flutter channel not set - cannot notify Flutter of task received")
        }
    }
    
    /**
     * Parse an incoming deep link from TaskGate.
     * Call this in your Activity's onCreate().
     * 
     * @param intent The incoming intent
     * @return true if the intent was handled by TaskGate SDK
     */
    @JvmStatic
    fun handleIntent(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        return handleUri(uri)
    }
    
    /**
     * Handle onNewIntent for warm start scenarios.
     * 
     * Call this in your MainActivity's onNewIntent() to handle TaskGate intents
     * when your app is already running (warm start).
     * 
     * @param intent The new intent received
     * @return true if the intent was handled by TaskGate SDK
     */
    @JvmStatic
    fun handleNewIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        
        Log.d(TAG, "handleNewIntent() - Checking intent")
        
        val uri = intent.data
        if (uri != null && handleUri(uri)) {
            Log.d(TAG, "handleNewIntent() - Deep link handled")
            // Notify Flutter for warm start
            pendingTaskInfo?.let { taskInfo ->
                notifyFlutterTaskReceived(taskInfo)
            }
            return true
        }
        
        return false
    }
    
    /**
     * Parse an incoming URI from TaskGate
     * 
     * @param uri The incoming URI
     * @return true if the URI was handled by TaskGate SDK
     */
    @JvmStatic
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
        
        Log.d(TAG, "handleUri() - Received deep link: taskId=$taskId, sessionId=$sessionId")
        
        // Collect additional params
        val additionalParams = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { key ->
            if (key !in listOf("task_id", "callback_url", "session_id", "app_name")) {
                uri.getQueryParameter(key)?.let { additionalParams[key] = it }
            }
        }
        
        // Store task info
        val taskInfo = TaskInfo(
            taskId = taskId,
            sessionId = sessionId,
            callbackUrl = callbackUrl,
            appName = appName,
            additionalParams = additionalParams
        )
        
        pendingTaskInfo = taskInfo
        
        // Persist to SharedPreferences so Flutter can check on startup
        savePendingTaskToPrefs(taskInfo)
        
        Log.d(TAG, "handleUri() - Task stored. Flutter should check getPendingTaskId() before creating router.")
        
        return true
    }
    
    /**
     * Check if there's a pending task waiting to be shown
     */
    @JvmStatic
    fun hasPendingTask(): Boolean = pendingTaskInfo != null
    
    /**
     * Clear the pending task without delivering it.
     * Call this after you've used getPendingTaskId() to set the initial route.
     */
    @JvmStatic
    fun clearPendingTask() {
        pendingTaskInfo = null
        clearPendingTaskFromPrefs()
        Log.d(TAG, "Pending task cleared")
    }
    
    /**
     * Report task completion to TaskGate
     * 
     * @param status The completion status (OPEN, FOCUS, or CANCELLED)
     */
    @JvmStatic
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
    @JvmStatic
    fun cancelTask() {
        reportCompletion(CompletionStatus.CANCELLED)
    }
    
    /**
     * Get the current session ID if a task is active
     */
    @JvmStatic
    fun getCurrentSessionId(): String? = currentSessionId
    
    /**
     * Get the current task ID if a task is active
     */
    @JvmStatic
    fun getCurrentTaskId(): String? = currentTaskId
    
    /**
     * Check if there's an active task session
     */
    @JvmStatic
    fun hasActiveSession(): Boolean = currentSessionId != null
    
    private fun savePendingTaskToPrefs(taskInfo: TaskInfo) {
        val context = applicationContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PENDING_TASK_ID, taskInfo.taskId)
            .putString(KEY_PENDING_SESSION_ID, taskInfo.sessionId)
            .putString(KEY_PENDING_CALLBACK_URL, taskInfo.callbackUrl)
            .putString(KEY_PENDING_APP_NAME, taskInfo.appName)
            .putLong(KEY_PENDING_TIMESTAMP, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Saved pending task to SharedPreferences: ${taskInfo.taskId}")
    }
    
    private fun clearPendingTaskFromPrefs() {
        val context = applicationContext ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PENDING_TASK_ID)
            .remove(KEY_PENDING_SESSION_ID)
            .remove(KEY_PENDING_CALLBACK_URL)
            .remove(KEY_PENDING_APP_NAME)
            .remove(KEY_PENDING_TIMESTAMP)
            .apply()
        Log.d(TAG, "Cleared pending task from SharedPreferences")
    }
    
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
