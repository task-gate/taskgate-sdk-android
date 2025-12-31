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
 * ## Quick Setup
 * 
 * 1. Initialize in Application.onCreate():
 * ```kotlin
 * TaskGateSDK.initialize(this, "your_provider_id", MainActivity::class.java)
 * ```
 * 
 * 2. Add TrampolineActivity to AndroidManifest.xml:
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
 * 3. Signal when your app is ready:
 * ```kotlin
 * TaskGateSDK.signalAppReady()
 * ```
 * 
 * 4. Get task info from intent extras:
 * ```kotlin
 * val taskId = intent.getStringExtra("taskgate_task_id")
 * val appName = intent.getStringExtra("taskgate_app_name")
 * // Or use: TaskGateSDK.getPendingTaskId(), TaskGateSDK.getPendingAppName()
 * ```
 * 
 * 5. Report completion when done:
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
    
    // Channel method names
    const val METHOD_ON_TASK_RECEIVED = "onTaskReceived"
    const val METHOD_GET_PENDING_TASK = "getPendingTask"
    const val METHOD_SIGNAL_APP_READY = "signalAppReady"
    const val METHOD_REPORT_COMPLETION = "reportCompletion"
    
    private var applicationContext: Context? = null
    private var providerId: String? = null
    private var currentSessionId: String? = null
    private var callbackUrl: String? = null
    private var currentTaskId: String? = null
    private var pendingTaskInfo: TaskInfo? = null
    private var taskActivityClass: Class<out Activity>? = null
    
    // Wait for app ready configuration (always enabled by default)
    private var waitTimeoutMs: Long = 3000L  // 3 second default timeout
    private var appReadyCallback: AppReadyCallback? = null
    
    // Flutter channel for sending events
    private var flutterChannel: FlutterChannel? = null
    
    /**
     * Interface for sending messages to Flutter.
     * Implement this with your MethodChannel.
     * 
     * Example:
     * ```kotlin
     * TaskGateSDK.setFlutterChannel(object : TaskGateSDK.FlutterChannel {
     *     override fun invokeMethod(method: String, arguments: Map<String, Any?>) {
     *         methodChannel.invokeMethod(method, arguments)
     *     }
     * })
     * ```
     */
    interface FlutterChannel {
        fun invokeMethod(method: String, arguments: Map<String, Any?>)
    }
    
    /**
     * Callback interface for app ready signal
     */
    interface AppReadyCallback {
        fun onAppReady()
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
     * Set the timeout for waiting for app ready signal.
     * 
     * By default, the SDK waits 3 seconds for signalAppReady() to be called.
     * Use this to customize the timeout if needed.
     * 
     * @param timeoutMs Maximum time to wait before finishing anyway (default: 3000ms)
     */
    @JvmStatic
    fun setWaitTimeout(timeoutMs: Long) {
        this.waitTimeoutMs = timeoutMs
        Log.d(TAG, "Wait timeout set to: ${timeoutMs}ms")
    }
    
    /**
     * Set the Flutter channel for SDK-to-Flutter communication.
     * 
     * The SDK will automatically send events to Flutter:
     * - "onTaskReceived" when a task arrives during warm start
     * 
     * Example:
     * ```kotlin
     * class MainActivity : FlutterActivity() {
     *     override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
     *         super.configureFlutterEngine(flutterEngine)
     *         
     *         val methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.yourapp/taskgate")
     *         
     *         // Register channel with SDK - it will automatically send onTaskReceived
     *         TaskGateSDK.setFlutterChannel(object : TaskGateSDK.FlutterChannel {
     *             override fun invokeMethod(method: String, arguments: Map<String, Any?>) {
     *                 methodChannel.invokeMethod(method, arguments)
     *             }
     *         })
     *         
     *         // Handle calls FROM Flutter
     *         methodChannel.setMethodCallHandler { call, result ->
     *             when (call.method) {
     *                 "getPendingTask" -> {
     *                     val taskId = TaskGateSDK.getPendingTaskId()
     *                     val appName = TaskGateSDK.getPendingAppName()
     *                     if (taskId != null) {
     *                         result.success(mapOf("taskId" to taskId, "appName" to appName))
     *                     } else {
     *                         result.success(null)
     *                     }
     *                 }
     *                 "signalAppReady" -> {
     *                     TaskGateSDK.signalAppReady()
     *                     result.success(null)
     *                 }
     *                 "reportCompletion" -> {
     *                     val status = when (call.argument<String>("status")) {
     *                         "open" -> TaskGateSDK.CompletionStatus.OPEN
     *                         "focus" -> TaskGateSDK.CompletionStatus.FOCUS
     *                         else -> TaskGateSDK.CompletionStatus.CANCELLED
     *                     }
     *                     TaskGateSDK.reportCompletion(status)
     *                     result.success(null)
     *                 }
     *                 else -> result.notImplemented()
     *             }
     *         }
     *     }
     * }
     * ```
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
     * Signal that your app is ready.
     * 
     * Call this when your app (e.g., Flutter) has fully initialized and is ready
     * to show content. This will:
     * 1. Tell TaskGate to dismiss its redirect screen
     * 2. Finish the trampoline activity, revealing your app's UI
     * 
     * For Flutter apps, call this via MethodChannel after Flutter is initialized:
     * ```dart
     * const platform = MethodChannel('com.yourapp/taskgate');
     * await platform.invokeMethod('signalAppReady');
     * ```
     * 
     * In your MainActivity:
     * ```kotlin
     * MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.yourapp/taskgate")
     *     .setMethodCallHandler { call, result ->
     *         if (call.method == "signalAppReady") {
     *             TaskGateSDK.signalAppReady()
     *             result.success(null)
     *         }
     *     }
     * ```
     */
    @JvmStatic
    fun signalAppReady() {
        Log.d(TAG, "signalAppReady() called - notifying TaskGate and finishing trampoline")
        
        // Tell TaskGate to dismiss its redirect screen
        notifyTaskGate()
        
        // Tell trampoline to finish (try both methods for reliability)
        val callbackResult = appReadyCallback?.let { 
            it.onAppReady()
            true 
        } ?: false
        
        if (!callbackResult) {
            Log.d(TAG, "Callback was null, using static instance to finish trampoline")
            // Fallback: use static reference to finish trampoline directly
            TaskGateTrampolineActivity.finishActiveTrampoline()
        }
    }
    
    /**
     * Notify TaskGate that partner app is ready.
     * This tells TaskGate to dismiss its redirect screen.
     */
    private fun notifyTaskGate() {
        val sessionId = currentSessionId ?: run {
            Log.w(TAG, "No active session - cannot notify TaskGate")
            return
        }
        
        Log.d(TAG, "Notifying TaskGate to dismiss redirect screen")
        
        val uri = Uri.Builder()
            .scheme(TASKGATE_SCHEME)
            .authority("partner-ready")
            .appendQueryParameter("session_id", sessionId)
            .appendQueryParameter("provider_id", providerId)
            .build()
        
        launchUri(uri)
    }
    
    /**
     * Get the wait timeout in milliseconds
     */
    @JvmStatic
    internal fun getWaitTimeout(): Long = waitTimeoutMs
    
    /**
     * Set the callback for app ready signal (used internally by TrampolineActivity)
     */
    @JvmStatic
    internal fun setAppReadyCallback(callback: AppReadyCallback?) {
        this.appReadyCallback = callback
    }
    
    /**
     * Clear the app ready callback
     */
    @JvmStatic
    internal fun clearAppReadyCallback() {
        this.appReadyCallback = null
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
     * Check if the intent is a TaskGate intent (either deep link or FROM_TASKGATE extra).
     * This is useful for checking if your app was launched/resumed by TaskGate.
     * 
     * @param intent The intent to check
     * @return true if this is a TaskGate-related intent
     */
    @JvmStatic
    fun isTaskGateIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        
        // Check for FROM_TASKGATE extra (set by TrampolineActivity)
        if (intent.getBooleanExtra(EXTRA_FROM_TASKGATE, false)) {
            return true
        }
        
        // Check for TaskGate deep link
        val uri = intent.data
        if (uri != null && uri.path?.contains("taskgate") == true) {
            return true
        }
        
        return false
    }
    
    /**
     * Handle onNewIntent for warm start scenarios.
     * 
     * Call this in your MainActivity's onNewIntent() to handle TaskGate intents
     * when your app is already running (warm start).
     * 
     * This will:
     * 1. Check if the intent is from TaskGate
     * 2. Parse the task info if it's a deep link
     * 3. Signal TaskGate that we received the intent
     * 4. Call showTask() to deliver the task to your listener
     * 
     * Usage:
     * ```kotlin
     * override fun onNewIntent(intent: Intent) {
     *     super.onNewIntent(intent)
     *     if (TaskGateSDK.handleNewIntent(intent)) {
     *         // TaskGate handled it, no further action needed
     *         return
     *     }
     *     // Handle other intents...
     * }
     * ```
     * 
     * @param intent The new intent received
     * @return true if the intent was handled by TaskGate SDK
     */
    @JvmStatic
    fun handleNewIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        
        Log.d(TAG, "handleNewIntent() - Checking intent")
        
        // Case 1: FROM_TASKGATE extra (warm start via TrampolineActivity)
        if (intent.getBooleanExtra(EXTRA_FROM_TASKGATE, false)) {
            Log.d(TAG, "handleNewIntent() - FROM_TASKGATE detected (warm start)")
            
            // Extract task info from intent extras
            val taskId = intent.getStringExtra("taskgate_task_id")
            val appName = intent.getStringExtra("taskgate_app_name")
            val sessionId = intent.getStringExtra("taskgate_session_id")
            
            Log.d(TAG, "handleNewIntent() - Task info: taskId=$taskId, appName=$appName")
            
            // Notify Flutter of the incoming task (warm start)
            pendingTaskInfo?.let { taskInfo ->
                notifyFlutterTaskReceived(taskInfo)
            }
            
            return true
        }
        
        // Case 2: Direct deep link (bypass trampoline scenario)
        val uri = intent.data
        if (uri != null && handleUri(uri)) {
            Log.d(TAG, "handleNewIntent() - Deep link handled")
            // Also notify Flutter for direct deep links
            pendingTaskInfo?.let { taskInfo ->
                notifyFlutterTaskReceived(taskInfo)
            }
            return true
        }
        
        return false
    }
    
    /**
     * Check if there's a pending task waiting to be shown
     */
    @JvmStatic
    fun hasPendingTask(): Boolean = pendingTaskInfo != null
    
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
