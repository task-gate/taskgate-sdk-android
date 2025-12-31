# TaskGate Android SDK

Enable your Android app to provide micro-tasks for TaskGate users.

---

## Quick Start

The SDK provides a built-in `TaskGateTrampolineActivity` that handles everything automatically.

### 1. Initialize the SDK

In your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Pass your main activity class - SDK will launch it automatically
        TaskGateSDK.initialize(
            context = this,
            providerId = "your_provider_id",
            taskActivityClass = MainActivity::class.java
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
```

> **Note:** You do NOT need a `taskgate://` scheme. The SDK handles communication with TaskGate internally.

### 3. Signal When Your App Is Ready

For **Flutter apps**, add a MethodChannel in your MainActivity:

```kotlin
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.yourapp/taskgate")
            .setMethodCallHandler { call, result ->
                if (call.method == "signalAppReady") {
                    TaskGateSDK.signalAppReady()
                    result.success(null)
                }
            }
    }
}
```

In Flutter (main.dart):

```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // ... your initialization ...
  
  // Signal when Flutter is ready
  const platform = MethodChannel('com.yourapp/taskgate');
  try { await platform.invokeMethod('signalAppReady'); } catch (_) {}
  
  runApp(MyApp());
}
```

For **native apps**, call `signalAppReady()` when your UI is ready:

```kotlin
class TaskActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)
        
        // UI is ready
        TaskGateSDK.signalAppReady()
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
| **Keep TaskGate visible**     | -                  | ✅ Automatic |
| **Launch your activity**      | -                  | ✅ Automatic |
| **Signal TaskGate when ready**| ✅ Call `signalAppReady()` | -     |
| **Show task UI**              | ✅ Your design     | -            |
| **Report completion**         | ✅ Call SDK method | -            |

---

## How It Works

```
┌──────────────────────────────────────────────────────────────────┐
│                     TIMELINE OF EVENTS                           │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. TaskGate shows redirect screen                               │
│                                                                  │
│  2. TaskGate launches deep link                                  │
│     └── TaskGateTrampolineActivity starts (invisible)            │
│     └── Stores task info                                         │
│     └── Launches your MainActivity                               │
│     └── Trampoline WAITS (TaskGate redirect still visible!)      │
│                                                                  │
│  3. Your app initializes (Flutter/Native)                        │
│     └── TaskGate redirect screen still visible                   │
│                                                                  │
│  4. You call signalAppReady()                                    │
│     └── Tells TaskGate to dismiss redirect screen                │
│     └── Trampoline finishes                                      │
│     └── Your app is now visible with task screen                 │
│                                                                  │
│  5. User completes task                                          │
│     └── reportCompletion() → TaskGate handles result             │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Why this pattern:**

- ✅ TaskGate's redirect screen stays visible during cold boot
- ✅ User sees smooth transition (redirect → your task screen)
- ✅ No black screen or "home screen flash"
- ✅ Your app (Flutter) can fully initialize before revealing UI
- ✅ Default 3-second timeout if `signalAppReady()` is not called

---

## Handling Warm Starts

When your app is already running and receives a TaskGate intent, use `handleNewIntent()`:

```kotlin
class MainActivity : FlutterActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        // SDK handles warm start automatically
        if (TaskGateSDK.handleNewIntent(intent)) {
            return // TaskGate handled it
        }
        // Handle other intents...
    }
}
```

---

## Getting Task Info

Read task info from intent extras or SharedPreferences:

```kotlin
// From intent extras (set by SDK)
val taskId = intent.getStringExtra("taskgate_task_id")
val appName = intent.getStringExtra("taskgate_app_name")

// Or from SDK (useful in Flutter before router is created)
val taskId = TaskGateSDK.getPendingTaskId()
val appName = TaskGateSDK.getPendingAppName()
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

## Complete Example (Flutter App)

**MyApplication.kt:**
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskGateSDK.initialize(this, "breathing_app", MainActivity::class.java)
    }
}
```

**MainActivity.kt:**
```kotlin
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.yourapp/taskgate")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "signalAppReady" -> {
                        TaskGateSDK.signalAppReady()
                        result.success(null)
                    }
                    "reportCompletion" -> {
                        val status = when (call.argument<String>("status")) {
                            "open" -> TaskGateSDK.CompletionStatus.OPEN
                            "focus" -> TaskGateSDK.CompletionStatus.FOCUS
                            else -> TaskGateSDK.CompletionStatus.CANCELLED
                        }
                        TaskGateSDK.reportCompletion(status)
                        result.success(null)
                    }
                }
            }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TaskGateSDK.handleNewIntent(intent)
    }
}
```

**main.dart:**
```dart
const _channel = MethodChannel('com.yourapp/taskgate');

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Your initialization...
  
  // Signal native that Flutter is ready
  try { await _channel.invokeMethod('signalAppReady'); } catch (_) {}
  
  runApp(MyApp());
}

// When task is completed:
Future<void> reportCompletion(String status) async {
  await _channel.invokeMethod('reportCompletion', {'status': status});
}
```

## Complete Example (Native App)

```kotlin
class TaskActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)

        // Get task info from intent
        val taskId = intent.getStringExtra("taskgate_task_id")
        val appName = intent.getStringExtra("taskgate_app_name")
        
        // Or from SharedPreferences
        // val taskId = TaskGateSDK.getPendingTaskId()
        // val appName = TaskGateSDK.getPendingAppName()
        
        // Show blocked app name
        findViewById<TextView>(R.id.tvBlockedApp).text =
            "Complete this task to open ${appName ?: "the app"}"

        // Setup UI
        findViewById<Button>(R.id.btnComplete).setOnClickListener {
            TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.OPEN)
            finish()
        }

        findViewById<Button>(R.id.btnStayFocused).setOnClickListener {
            TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.FOCUS)
            finish()
        }

        // UI is ready - signal SDK
        TaskGateSDK.signalAppReady()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        TaskGateSDK.handleNewIntent(intent)
    }

    override fun onBackPressed() {
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
| `initialize(context, providerId, taskActivityClass)` | Initialize SDK with your provider ID and activity to launch |
| `setWaitTimeout(timeoutMs)`       | Set timeout for waiting for signalAppReady() (default: 3000ms) |

### App Ready Signal

| Method                 | Description                                                           |
| ---------------------- | --------------------------------------------------------------------- |
| `signalAppReady()`     | **Call when your app is ready.** Dismisses TaskGate's redirect screen and finishes trampoline. |

### Handling Intents

| Method                 | Description                                                           |
| ---------------------- | --------------------------------------------------------------------- |
| `handleNewIntent(intent)` | Handle warm start - call in onNewIntent(). Returns true if handled. |
| `isTaskGateIntent(intent)` | Check if intent is from TaskGate |
| `isLaunchedByTaskGate(intent)` | Check if activity was launched by TaskGate SDK |

### Getting Task Info

| Method                 | Description                                                           |
| ---------------------- | --------------------------------------------------------------------- |
| `getPendingTaskId()`   | Get pending task ID from SharedPreferences |
| `getPendingAppName()`  | Get pending blocked app name from SharedPreferences |
| `hasPendingTask()`     | Check if there's a pending task |
| `clearPendingTask()`   | Clear pending task info |

Or read directly from intent extras:
- `intent.getStringExtra("taskgate_task_id")`
- `intent.getStringExtra("taskgate_app_name")`
- `intent.getStringExtra("taskgate_session_id")`

### Reporting Completion

| Method                     | Description                                                                                |
| -------------------------- | ------------------------------------------------------------------------------------------ |
| `reportCompletion(status)` | Report task outcome and return to TaskGate                               |
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

### App not receiving TaskGate intents

1. Check that your Activity handles `onNewIntent()` for warm starts
2. Make sure `TaskGateSDK.handleNewIntent(intent)` is called in `onNewIntent()`

### Task not completing

1. Verify you're calling `reportCompletion()` or `cancelTask()`
2. Check logcat for `TaskGateSDK` tag for debugging info

### Black screen before app appears

1. Make sure `signalAppReady()` is called when your app is ready
2. The SDK waits 3 seconds by default - customize with `setWaitTimeout()`

---

## Support

- Email: partners@taskgate.app
- Documentation: https://docs.taskgate.app/partners/android
- GitHub Issues: https://github.com/taskgate/taskgate-sdk-android/issues
