package com.taskgate.sdk

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * SDK-provided trampoline activity that handles TaskGate deep links.
 * 
 * This activity:
 * 1. Receives the deep link from TaskGate
 * 2. Parses and stores task info
 * 3. Signals TaskGate that the link was received (keeps TaskGate visible)
 * 4. Launches your configured task activity
 * 5. Waits for app to signal "ready" (or timeout after 3 seconds)
 * 6. Finishes itself (invisible, no flash)
 * 
 * ## Setup
 * 
 * 1. Configure your task activity in Application.onCreate():
 * ```kotlin
 * TaskGateSDK.initialize(this, "your_provider_id", MainActivity::class.java)
 * ```
 * 
 * 2. Add this activity to your AndroidManifest.xml:
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
 * 3. Signal when your app is ready (e.g., in Flutter via MethodChannel):
 * ```kotlin
 * // In your MainActivity
 * TaskGateSDK.signalAppReady()
 * ```
 * 
 * This keeps TaskGate's redirect screen visible until your app is ready.
 * If signalAppReady() is not called within 3 seconds, the trampoline finishes anyway.
 */
open class TaskGateTrampolineActivity : Activity() {
    
    companion object {
        private const val TAG = "TaskGateTrampoline"
        
        // Static reference to the active trampoline instance
        // This allows signalAppReady() to finish it directly
        @Volatile
        private var activeInstance: TaskGateTrampolineActivity? = null
        
        /**
         * Finish the active trampoline instance (if any).
         * Called by TaskGateSDK.signalAppReady()
         */
        internal fun finishActiveTrampoline() {
            activeInstance?.let { instance ->
                Log.d(TAG, "[TRAMPOLINE] Finishing active trampoline instance")
                instance.finishTrampoline()
            } ?: run {
                Log.d(TAG, "[TRAMPOLINE] No active trampoline instance to finish")
            }
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var isWaiting = false
    
    private val readyCallback = object : TaskGateSDK.AppReadyCallback {
        override fun onAppReady() {
            Log.d(TAG, "[TRAMPOLINE] App signaled ready, finishing trampoline")
            finishTrampoline()
        }
    }
    
    private val timeoutRunnable = Runnable {
        Log.w(TAG, "[TRAMPOLINE] Timeout waiting for app ready, finishing anyway")
        finishTrampoline()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "[TRAMPOLINE] onCreate - Processing deep link")
        
        // Register this instance as the active trampoline
        activeInstance = this
        
        // Step 1: Parse the deep link
        val handled = TaskGateSDK.handleIntent(intent)
        
        if (handled) {
            Log.d(TAG, "[TRAMPOLINE] Deep link handled successfully")
            
            // Step 2: Launch the partner's task activity
            // TaskGate's redirect screen stays visible until signalAppReady() is called
            TaskGateSDK.launchTaskActivity()
            
            // Step 3: Wait for app to signal ready (with timeout)
            Log.d(TAG, "[TRAMPOLINE] Waiting for app to signal ready...")
            isWaiting = true
            TaskGateSDK.setAppReadyCallback(readyCallback)
            
            // Set timeout (default 3 seconds)
            val timeout = TaskGateSDK.getWaitTimeout()
            handler.postDelayed(timeoutRunnable, timeout)
        } else {
            Log.w(TAG, "[TRAMPOLINE] Deep link not handled - not a TaskGate request")
            finish()
        }
    }
    
    private fun finishTrampoline() {
        if (!isFinishing) {
            Log.d(TAG, "[TRAMPOLINE] finishTrampoline() called")
            handler.removeCallbacks(timeoutRunnable)
            TaskGateSDK.clearAppReadyCallback()
            activeInstance = null
            isWaiting = false
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "[TRAMPOLINE] onDestroy")
        handler.removeCallbacks(timeoutRunnable)
        if (isWaiting) {
            TaskGateSDK.clearAppReadyCallback()
        }
        // Clear static reference if this is the active instance
        if (activeInstance == this) {
            activeInstance = null
        }
    }
    
    /**
     * Override this method if you need to perform custom initialization
     * before the task activity is launched.
     * 
     * @return true to continue with normal flow, false to handle manually
     */
    protected open fun onTaskReceived(): Boolean {
        return true
    }
}
