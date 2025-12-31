# TaskGate Android SDK

Enable your Android app to provide micro-tasks for TaskGate users.

---

## Quick Start (Recommended)

The SDK provides a built-in `TaskGateTrampolineActivity` that handles everything automatically.

### 1. Initialize the SDK

In your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Pass your task activity class - SDK will launch it automatically
        TaskGateSDK.initialize(
            context = this,
            providerId = "your_provider_id",
            taskActivityClass = TaskActivity::class.java
        )
    }
}
```

### 2. Add SDK Trampoline to Manifest

```xml
<!-- SDK's trampoline - handles deep link automatically -->
<activity
    android:name="com.taskgate.sdk.TaskGateTrampolineActivity"
    android:exported="true"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:noHistory="true"
    android:excludeFromRecents="true">

    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="yourdomain.com"
            android:pathPrefix="/taskgate" />
    </intent-filter>
</activity>

<!-- Your task activity - launched by SDK -->
<activity
    android:name=".TaskActivity"
    android:exported="false" />
```

### 3. Handle Tasks in Your Activity

```kotlin
class TaskActivity : AppCompatActivity(), TaskGateSDK.TaskGateListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)

        TaskGateSDK.setListener(this)

        // Check if launched by TaskGate
        if (TaskGateSDK.isLaunchedByTaskGate(intent)) {
            // Deliver task when ready
            TaskGateSDK.showTask()
        }
    }

    override fun onTaskReceived(taskInfo: TaskGateSDK.TaskInfo) {
        // Display your task UI
        Log.d("Task", "Received: ${taskInfo.taskId}, blocked app: ${taskInfo.appName}")
        showTask(taskInfo)
    }

    override fun onTaskRequested(taskId: String, params: Map<String, String>) {
        // Alternative callback with just task ID
    }
}
```

### 4. Report Completion

```kotlin
// User completed task and wants to open the blocked app
TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.OPEN)

// User wants to stay focused
TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.FOCUS)

// User cancelled
TaskGateSDK.cancelTask()
```

---

## What the SDK Handles

| Component                     | You Handle         | SDK Handles  |
| ----------------------------- | ------------------ | ------------ |
| **Trampoline activity**       | ❌ Not needed      | ✅ Provided  |
| **Deep link parsing**         | -                  | ✅ Automatic |
| **Signal TaskGate ready**     | -                  | ✅ Automatic |
| **Launch your task activity** | -                  | ✅ Automatic |
| **Show task UI**              | ✅ Your design     | -            |
| **Report completion**         | ✅ Call SDK method | -            |

---

## How It Works: Trampoline Pattern

```
┌──────────────────────────────────────────────────────────────────┐
│                     TIMELINE OF EVENTS                           │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. TaskGate shows redirect screen (TaskGate = foreground)       │
│                                                                  │
│  2. TaskGate launches your deep link                             │
│     └── TaskGateTrampolineActivity starts (invisible)            │
│     └── handleIntent() stores task info                          │
│     └── notifyReady() signals TaskGate "I got it"                │
│     └── Launches your TaskActivity                               │
│     └── finish() → Trampoline disappears                         │
│     └── TaskGate redirect screen stays visible briefly           │
│                                                                  │
│  3. Your TaskActivity.onCreate()                                 │
│     └── isLaunchedByTaskGate() returns true                      │
│     └── showTask() delivers task to your listener                │
│     └── Your app is now foreground with task screen              │
│                                                                  │
│  4. User completes task                                          │
│     └── reportCompletion() → TaskGate handles result             │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Why the trampoline pattern:**

- ✅ TaskGate's redirect screen stays visible during cold boot
- ✅ User sees smooth transition (redirect → your task screen)
- ✅ No jarring "home screen flash" from your app
- ✅ Your app can fully initialize before showing task UI

---

## Advanced: Custom Trampoline Activity

If you need custom initialization logic, create your own trampoline:

```kotlin
class MyTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (TaskGateSDK.handleIntent(intent)) {
            // Signal TaskGate we received the link
            TaskGateSDK.notifyReady()

            // Custom: wait for initialization before showing task
            MyAppInitializer.onReady {
                TaskGateSDK.showTask()
                startActivity(Intent(this, TaskActivity::class.java))
            }
        }

        // Finish immediately - TaskGate stays visible
        finish()
    }
}
```

**Manifest for custom trampoline:**

```xml
<activity
    android:name=".MyTrampolineActivity"
    android:exported="true"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:noHistory="true"
    android:excludeFromRecents="true">
    <!-- intent-filter here -->
</activity>
```

---

## Installation

### Gradle (JitPack)

Add JitPack to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.taskgate:taskgate-sdk-android:1.0.0")
}
```

### Manual Installation

Copy `TaskGateSDK.kt` and `TaskGateTrampolineActivity.kt` to your project under `com.taskgate.sdk` package.

---

## Complete Example

```kotlin
class BreathingTaskActivity : AppCompatActivity(), TaskGateSDK.TaskGateListener {

    private var currentTaskInfo: TaskGateSDK.TaskInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_breathing)

        TaskGateSDK.setListener(this)

        // Handle intent (stores task internally)
        TaskGateSDK.handleIntent(intent)

        // Setup UI
        findViewById<Button>(R.id.btnComplete).setOnClickListener {
            onTaskCompleted(openApp = true)
        }

        findViewById<Button>(R.id.btnStayFocused).setOnClickListener {
            onTaskCompleted(openApp = false)
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            onTaskCancelled()
        }

        // UI is ready - now trigger task delivery
        TaskGateSDK.notifyReady()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        TaskGateSDK.handleIntent(intent)
        TaskGateSDK.notifyReady()  // Warm start - ready immediately
    }

    override fun onTaskReceived(taskInfo: TaskGateSDK.TaskInfo) {
        // Called after notifyReady() - app is guaranteed ready
        currentTaskInfo = taskInfo

        // Show blocked app name to user
        findViewById<TextView>(R.id.tvBlockedApp).text =
            "Complete this task to open ${taskInfo.appName ?: "the app"}"

        // Start your task (e.g., breathing exercise)
        startBreathingExercise()
    }

    override fun onTaskRequested(taskId: String, params: Map<String, String>) {
        // Handle specific task types
        when {
            taskId.contains("breathing") -> startBreathingExercise()
            taskId.contains("affirmation") -> showAffirmation()
            else -> startDefaultTask()
        }
    }

    private fun onTaskCompleted(openApp: Boolean) {
        if (openApp) {
            TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.OPEN)
        } else {
            TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.FOCUS)
        }
        finish()
    }

    private fun onTaskCancelled() {
        TaskGateSDK.cancelTask()
        finish()
    }

    override fun onBackPressed() {
        // Treat back press as cancellation
        TaskGateSDK.cancelTask()
        super.onBackPressed()
    }
}
```

---

## API Reference

### Initialization

| Method                            | Description                           |
| --------------------------------- | ------------------------------------- |
| `initialize(context, providerId)` | Initialize SDK with your provider ID  |
| `setListener(listener)`           | Set callback listener for task events |

### Handling Requests

| Method                 | Description                                                           |
| ---------------------- | --------------------------------------------------------------------- |
| `handleIntent(intent)` | Parse and **store** incoming task from TaskGate (doesn't deliver yet) |
| `handleUri(uri)`       | Parse and **store** incoming URI directly (doesn't deliver yet)       |

### Task Lifecycle

| Method                     | Description                                                                                |
| -------------------------- | ------------------------------------------------------------------------------------------ |
| `notifyReady()`            | **Triggers delivery** of stored task via `onTaskReceived()`. TaskGate stays in background. |
| `reportCompletion(status)` | Report task outcome and bring TaskGate back to handle result                               |
| `cancelTask()`             | Shorthand for `reportCompletion(CANCELLED)`                                                |

### Completion Status

| Status      | User Action                                           |
| ----------- | ----------------------------------------------------- |
| `OPEN`      | Completed task, wants to open the blocked app         |
| `FOCUS`     | Completed task, wants to stay focused (no app launch) |
| `CANCELLED` | Skipped or cancelled the task                         |

### TaskInfo Properties

| Property           | Type    | Description                                    |
| ------------------ | ------- | ---------------------------------------------- |
| `taskId`           | String  | Unique task identifier (e.g., "breathing_30s") |
| `sessionId`        | String  | Session ID for this task attempt               |
| `callbackUrl`      | String  | URL to call when task completes                |
| `appName`          | String? | Name of the blocked app (for display)          |
| `additionalParams` | Map     | Extra parameters from TaskGate                 |

### Utility Methods

| Method                  | Description                             |
| ----------------------- | --------------------------------------- |
| `hasActiveSession()`    | Check if there's an active task session |
| `getCurrentTaskId()`    | Get the current task ID                 |
| `getCurrentSessionId()` | Get the current session ID              |

---

## URL Format

TaskGate will launch your app with URLs like:

```
https://yourdomain.com/taskgate/start
    ?task_id=breathing_30s
    &callback_url=https://taskgate-app-links.onrender.com/task/completed
    &session_id=abc12345
    &app_name=Instagram
```

Your SDK calls back to TaskGate with:

```
# Ready signal
taskgate://partner-ready?session_id=abc12345&provider_id=your_provider

# Completion
https://taskgate-app-links.onrender.com/task/completed
    ?status=open
    &provider_id=your_provider
    &session_id=abc12345
    &task_id=breathing_30s
```

---

## ProGuard Rules

If using ProGuard/R8, add these rules:

```proguard
-keep class com.taskgate.sdk.** { *; }
-keepclassmembers class com.taskgate.sdk.** { *; }
```

---

## Troubleshooting

### Deep links not working

1. Verify your `intent-filter` in AndroidManifest.xml
2. Test with: `adb shell am start -a android.intent.action.VIEW -d "https://yourdomain.com/taskgate/start?task_id=test"`
3. Check if your domain is verified for App Links

### SDK not receiving callbacks

1. Ensure `TaskGateSDK.setListener()` is called before `handleIntent()`
2. Check that your Activity handles `onNewIntent()` for when app is already running

### Task not completing

1. Verify you're calling `reportCompletion()` or `cancelTask()`
2. Check logcat for `TaskGateSDK` tag for debugging info

---

## Support

- Email: partners@taskgate.app
- Documentation: https://docs.taskgate.app/partners/android
- GitHub Issues: https://github.com/taskgate/taskgate-sdk-android/issues
