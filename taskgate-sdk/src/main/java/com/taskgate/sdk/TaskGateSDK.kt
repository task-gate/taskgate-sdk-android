package com.taskgate.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * TaskGate Partner SDK for Android
 * 
 * Allows partner apps to:
 * - Receive task requests from TaskGate
 * - Signal when app is ready (cold boot complete)
 * - Report task completion status
 * 
 * ## Quick Setup (Recommended)
 * 
 * 1. Initialize in Application.onCreate():
 * ```kotlin
 * TaskGateSDK.initialize(this, "your_provider_id", YourTaskActivity::class.java)
 * ```
 * 
 * 2. Add to AndroidManifest.xml:
 * ```xml
 * <activity
 *     android:name="com.taskgate.sdk.TaskGateTrampolineActivity"
 *     android:theme="@android:style/Theme.Translucent.NoTitleBar"
 *     android:noHistory="true"
 *     android:excludeFromRecents="true"
 *     android:exported="true">
 *     <intent-filter android:autoVerify="true">
 *         <action android:name="android.intent.action.VIEW" />
 *         <category android:name="android.intent.category.DEFAULT" />
 *         <category android:name="android.intent.category.BROWSABLE" />
 *         <data android:scheme="https" android:host="yourdomain.com" android:pathPrefix="/taskgate" />
 *     </intent-filter>
 * </activity>
 * ```
 * 
 * 3. In your TaskActivity, call showTask() when ready:
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     // Your initialization...
 *     TaskGateSDK.showTask() // Delivers task info to listener
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
    private const val TASKGATE_SCHEME = "taskgate"
    internal const val EXTRA_FROM_TASKGATE = "com.taskgate.FROM_TASKGATE"
    private const val PREFS_NAME = "TaskGateSDKPrefs"
    private const val KEY_PENDING_TASK_ID = "pending_task_id"
    private const val KEY_PENDING_SESSION_ID = "pending_session_id"
    private const val KEY_PENDING_CALLBACK_URL = "pending_callback_url"
    private const val KEY_PENDING_APP_NAME = "pending_app_name"
    private const val KEY_PENDING_TIMESTAMP = "pending_timestamp"
    
    private var applicationContext: Context? = null
    private var providerId: String? = null
    private var currentSessionId: String? = null
    private var callbackUrl: String? = null
    private var currentTaskId: String? = null
    private var pendingTaskInfo: TaskInfo? = null
    private var taskActivityClass: Class<out Activity>? = null
    
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
     * @param taskActivityClass The Activity class to launch for handling tasks.
     *                          This activity will be started after the trampoline signals TaskGate.
     *                          The activity should call showTask() when ready.
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context, 
        providerId: String,
        taskActivityClass: Class<out Activity>? = null
    ) {
        this.applicationContext = context.applicationContext
        this.providerId = providerId
        this.taskActivityClass = taskActivityClass
        Log.d(TAG, "TaskGate SDK initialized for provider: $providerId")
        if (taskActivityClass != null) {
            Log.d(TAG, "Task activity configured: ${taskActivityClass.simpleName}")
        }
    }
    
    /**
     * Check if this activity was launched by TaskGate SDK
     * Use this in your task activity to know if it should call showTask()
     */
    @JvmStatic
    fun isLaunchedByTaskGate(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_FROM_TASKGATE, false) == true
    }
    
    /**
     * Check if there's a pending task waiting to be shown
     */
    @JvmStatic
    fun hasPendingTask(): Boolean = pendingTaskInfo != null
    
    /**
     * Set the listener for TaskGate events
     */
    @JvmStatic
    fun setListener(listener: TaskGateListener?) {
        this.listener = listener
    }
    
    /**
     * Parse an incoming deep link from TaskGate
     * Call this in your Activity's onCreate() and onNewIntent()
     * 
     * NOTE: If using TaskGateTrampolineActivity, you don't need to call this manually.
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
        
        Log.d(TAG, "[STEP 1] handleUri() - Received deep link: taskId=$taskId, sessionId=$sessionId")
        
        // Collect additional params
        val additionalParams = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { key ->
            if (key !in listOf("task_id", "callback_url", "session_id", "app_name")) {
                uri.getQueryParameter(key)?.let { additionalParams[key] = it }
            }
        }
        
        // Store task info - will be delivered when showTask() is called
        val taskInfo = TaskInfo(
            taskId = taskId,
            sessionId = sessionId,
            callbackUrl = callbackUrl,
            appName = appName,
            additionalParams = additionalParams
        )
        
        pendingTaskInfo = taskInfo
        
        // Also persist to SharedPreferences so Flutter can check on startup
        // This allows Flutter to set the correct initial route BEFORE the router is created
        savePendingTaskToPrefs(taskInfo)
        
        Log.d(TAG, "[STEP 2] handleUri() - Task STORED in pendingTaskInfo AND SharedPreferences.")
        Log.d(TAG, "[STEP 2] Flutter should check getPendingTaskId() before creating router.")
        
        return true
    }
    
    /**
     * Get the pending task ID from SharedPreferences.
     * Call this in Flutter BEFORE creating the router to set the correct initial location.
     * 
     * @return The pending task ID, or null if no task is pending
     */
    @JvmStatic
    fun getPendingTaskId(): String? {
        val context = applicationContext ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val taskId = prefs.getString(KEY_PENDING_TASK_ID, null)
        val timestamp = prefs.getLong(KEY_PENDING_TIMESTAMP, 0)
        
        // Ignore stale tasks (older than 30 seconds)
        val ageMs = System.currentTimeMillis() - timestamp
        if (taskId != null && ageMs > 30000) {
            Log.d(TAG, "getPendingTaskId() - Task is stale (${ageMs}ms old), clearing")
            clearPendingTaskFromPrefs()
            return null
        }
        
        Log.d(TAG, "getPendingTaskId() - Found: $taskId (${ageMs}ms old)")
        return taskId
    }
    
    /**
     * Get the pending app name from SharedPreferences.
     */
    @JvmStatic
    fun getPendingAppName(): String? {
        val context = applicationContext ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PENDING_APP_NAME, null)
    }
    
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
    
    /**
     * Signal TaskGate that your app received the deep link.
     * 
     * NOTE: If using TaskGateTrampolineActivity, this is called automatically.
     * 
     * Call this IMMEDIATELY in your trampoline activity, then finish() the activity.
     * This keeps TaskGate's redirect screen visible while your app initializes.
     * After your app is ready, call showTask() to bring your task UI to foreground.
     */
    @JvmStatic
    fun notifyReady() {
        val sessionId = currentSessionId ?: run {
            Log.w(TAG, "No active session - cannot notify ready")
            return
        }
        
        Log.d(TAG, "[STEP 3] notifyReady() called - Signaling TaskGate we received the link")
        
        // Signal TaskGate to keep showing redirect screen
        // Partner's trampoline activity should finish() after this
        val uri = Uri.Builder()
            .scheme(TASKGATE_SCHEME)
            .authority("partner-ready")
            .appendQueryParameter("session_id", sessionId)
            .appendQueryParameter("provider_id", providerId)
            .build()
        
        launchUri(uri)
        Log.d(TAG, "[STEP 3] TaskGate signaled. Now finish() your trampoline activity.")
    }
    
    /**
     * Launch the configured task activity.
     * Called internally by TaskGateTrampolineActivity after signaling TaskGate.
     */
    internal fun launchTaskActivity() {
        val context = applicationContext ?: run {
            Log.e(TAG, "SDK not initialized - cannot launch task activity")
            return
        }
        
        val activityClass = taskActivityClass ?: run {
            Log.w(TAG, "No task activity configured - partner must handle navigation manually")
            return
        }
        
        Log.d(TAG, "[STEP 4] Launching task activity: ${activityClass.simpleName}")
        
        try {
            val intent = Intent(context, activityClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_FROM_TASKGATE, true)
                // Pass task info as extras for convenience
                pendingTaskInfo?.let { task ->
                    putExtra("taskgate_task_id", task.taskId)
                    putExtra("taskgate_session_id", task.sessionId)
                    putExtra("taskgate_app_name", task.appName)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch task activity", e)
        }
    }
    
    /**
     * Deliver the task info and bring your task UI to foreground.
     * Call this when your app is fully initialized and ready to show the task screen.
     * 
     * This will:
     * 1. Deliver the pending task info to your listener via onTaskReceived()
     * 2. Your app should then navigate to the task screen
     * 
     * NOTE: If you already used getPendingTaskId() to set the initial route,
     * you may not need to call this - the task info is already known.
     * 
     * @return true if a task was delivered, false if no pending task
     */
    @JvmStatic
    fun showTask(): Boolean {
        Log.d(TAG, "[STEP 5] showTask() called - App is ready to show task")
        
        // Deliver pending task to listener
        val taskInfo = pendingTaskInfo
        if (taskInfo != null) {
            Log.d(TAG, "[STEP 6] NOW delivering task to listener: taskId=${taskInfo.taskId}")
            listener?.onTaskReceived(taskInfo)
            listener?.onTaskRequested(taskInfo.taskId, taskInfo.additionalParams)
            pendingTaskInfo = null
            clearPendingTaskFromPrefs()  // Also clear from SharedPreferences
            Log.d(TAG, "[STEP 6] onTaskReceived() completed. Navigate to your task screen now.")
            return true
        } else {
            Log.w(TAG, "[STEP 5] No pending task to deliver")
            return false
        }
    }
    
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
