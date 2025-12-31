package com.taskgate.sdk

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * SDK-provided trampoline activity that handles TaskGate deep links.
 * 
 * This activity:
 * 1. Receives the deep link from TaskGate
 * 2. Parses and stores task info
 * 3. Signals TaskGate that the link was received (keeps TaskGate visible)
 * 4. Launches your configured task activity
 * 5. Finishes itself (invisible, no flash)
 * 
 * ## Setup
 * 
 * 1. Configure your task activity in Application.onCreate():
 * ```kotlin
 * TaskGateSDK.initialize(this, "your_provider_id", YourTaskActivity::class.java)
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
 * 3. In your task activity, call showTask() when ready:
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     
 *     // Check if launched by TaskGate
 *     if (TaskGateSDK.isLaunchedByTaskGate(intent)) {
 *         // Your initialization...
 *         TaskGateSDK.showTask() // Delivers task info to your listener
 *     }
 * }
 * ```
 */
open class TaskGateTrampolineActivity : Activity() {
    
    companion object {
        private const val TAG = "TaskGateTrampoline"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "[TRAMPOLINE] onCreate - Processing deep link")
        
        // Step 1: Parse the deep link
        val handled = TaskGateSDK.handleIntent(intent)
        
        if (handled) {
            Log.d(TAG, "[TRAMPOLINE] Deep link handled successfully")
            
            // Step 2: Signal TaskGate that we received the link
            // This keeps TaskGate's redirect screen visible
            TaskGateSDK.notifyReady()
            
            // Step 3: Launch the partner's task activity
            TaskGateSDK.launchTaskActivity()
            
            Log.d(TAG, "[TRAMPOLINE] Task activity launched, finishing trampoline")
        } else {
            Log.w(TAG, "[TRAMPOLINE] Deep link not handled - not a TaskGate request")
        }
        
        // Step 4: Finish immediately (this activity is invisible)
        finish()
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
