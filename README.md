# TaskGate Android SDK

Enable your Android app to provide micro-tasks for TaskGate users.

---

## Full Integration Guide

### What You Need vs What SDK Handles

| Component                              | You Handle          | SDK Handles   |
| -------------------------------------- | ------------------- | ------------- |
| **AndroidManifest deep link config**   | ✅ Required         | -             |
| **Pass URL to SDK**                    | ✅ One line         | -             |
| **Parse URL parameters**               | -                   | ✅ Automatic  |
| **Router setup for `/taskgate/start`** | ❌ Not needed       | ✅ SDK parses |
| **Show task UI**                       | ✅ Your design      | -             |
| **Signal ready / completion**          | ✅ Call SDK methods | -             |

### Integration Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        YOUR APP SETUP                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. AndroidManifest.xml (Platform Config)                       │
│     └── intent-filter for your scheme/domain                    │
│                                                                 │
│  2. Application.onCreate()                                      │
│     └── TaskGateSDK.initialize(this, "your_provider_id")        │
│                                                                 │
│  3. TaskActivity.onCreate() / onNewIntent()                     │
│     └── TaskGateSDK.handleIntent(intent)  ← One line only!      │
│                                                                 │
│  4. TaskGateSDK.TaskGateListener callback                       │
│     └── onTaskReceived(taskInfo) → Show your task UI            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

                              ↓

┌─────────────────────────────────────────────────────────────────┐
│                      SDK HANDLES FOR YOU                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✅ Checks if URL path contains "taskgate"                      │
│  ✅ Parses task_id, callback_url, session_id, app_name          │
│  ✅ Parses additional custom parameters                         │
│  ✅ Stores session state for completion callbacks               │
│  ✅ Sends ready signal to TaskGate                              │
│  ✅ Sends completion with proper URL format                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Understanding the Flow: Cold Boot Handling

The SDK is designed for **cold boot scenarios** where your app may take time to initialize:

```
┌──────────────────────────────────────────────────────────────────┐
│                     TIMELINE OF EVENTS                           │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Deep link arrives                                            │
│     └── handleIntent() / handleUri()                             │
│     └── SDK STORES task info internally (does NOT call listener)│
│     └── Returns true (URL was handled)                           │
│                                                                  │
│  2. Your app initializes                                         │
│     └── Load UI, initialize services, etc.                       │
│     └── Task info is safely stored, waiting...                   │
│                                                                  │
│  3. App is ready                                                 │
│     └── You call notifyReady()                                   │
│     └── SDK NOW delivers task via onTaskReceived()               │
│     └── SDK signals TaskGate to dismiss redirect screen          │
│                                                                  │
│  4. User completes task                                          │
│     └── You call reportCompletion()                              │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Why this matters:**

- ✅ Your app can fully initialize before receiving the task
- ✅ No race condition between deep link and app startup
- ✅ `onTaskReceived()` is guaranteed to fire only when you're ready
- ✅ TaskGate knows exactly when to dismiss its redirect screen

### You DON'T Need

❌ **No router/navigation setup for TaskGate paths:**

```kotlin
// NOT NEEDED - SDK handles URL parsing
GoRoute(path: "/taskgate/start", ...)  // ← Don't need this
```

❌ **No manual URL parameter parsing:**

```kotlin
// NOT NEEDED - SDK does this
val taskId = intent.data?.getQueryParameter("task_id")  // ← Don't need this
val callbackUrl = intent.data?.getQueryParameter("callback_url")  // ← Don't need this
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

Copy `TaskGateSDK.kt` to your project under `com.taskgate.sdk` package.

---

## Quick Start

### 1. Initialize the SDK

In your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskGateSDK.initialize(this, "your_provider_id")
    }
}
```

### 2. Handle Incoming Deep Links

In your task Activity:

```kotlin
class TaskActivity : AppCompatActivity(), TaskGateSDK.TaskGateListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)

        // Set listener for task events
        TaskGateSDK.setListener(this)

        // Handle the incoming intent (stores task, doesn't deliver yet)
        TaskGateSDK.handleIntent(intent)

        // ... your initialization code ...

        // When your UI is ready, call notifyReady()
        // This triggers onTaskReceived() and signals TaskGate
        TaskGateSDK.notifyReady()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle when app is already running (warm start)
        TaskGateSDK.handleIntent(intent)
        TaskGateSDK.notifyReady()  // Already initialized, so ready immediately
    }

    override fun onTaskReceived(taskInfo: TaskGateSDK.TaskInfo) {
        // This is called AFTER notifyReady() - your app is guaranteed ready
        Log.d("Task", "Received task: ${taskInfo.taskId}")
        Log.d("Task", "Blocked app: ${taskInfo.appName}")

        // Show your task based on taskInfo.taskId
        showTask(taskInfo)
    }

    override fun onTaskRequested(taskId: String, params: Map<String, String>) {
        // Alternative callback with just task ID
    }
}
```

### 3. Configure Deep Links

Add to your `AndroidManifest.xml`:

```xml
<activity
    android:name=".TaskActivity"
    android:exported="true">

    <!-- HTTPS Deep Links (recommended) -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data
            android:scheme="https"
            android:host="yourdomain.com"
            android:pathPrefix="/taskgate" />
    </intent-filter>

    <!-- Custom URL Scheme (fallback) -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="yourapp" />
    </intent-filter>
</activity>
```

### 4. Report Task Completion

When the user completes (or cancels) the task:

```kotlin
// User completed task and wants to open the blocked app
TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.OPEN)

// User completed task but wants to stay focused
TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.FOCUS)

// User cancelled the task
TaskGateSDK.cancelTask()
```

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

| Method                     | Description                                                                              |
| -------------------------- | ---------------------------------------------------------------------------------------- |
| `notifyReady()`            | **Triggers delivery** of stored task via `onTaskReceived()` + signals TaskGate app ready |
| `reportCompletion(status)` | Report task outcome                                                                      |
| `cancelTask()`             | Shorthand for `reportCompletion(CANCELLED)`                                              |

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
