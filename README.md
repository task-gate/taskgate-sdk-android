# TaskGate Android SDK

Enable your Android app to provide micro-tasks for TaskGate users.

**[Download TaskGate on Google Play](https://play.google.com/store/apps/details?id=com.tkg.taskgate)** | **[Partnership Info](https://taskgate.co/partnership)**

---

## How It Works

TaskGate helps users break phone addiction by requiring them to complete a mindful task before accessing distracting apps.

1. **User blocks distracting apps** - Instagram, TikTok, games, etc.
2. **User tries to open a blocked app** - TaskGate intercepts the launch
3. **TaskGate redirects to your partner app** - Your app receives a deep link with task info
4. **User completes your micro-task** - Breathing exercise, meditation, quiz, etc.
5. **Your app reports completion** - TaskGate unlocks the blocked app (or user stays focused)

This creates a **win-win**: users build better habits, and your app gains engaged users who are primed for mindful activities.

---

## Quick Start (Native Android)

### 1. Initialize the SDK

```kotlin
// Application.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TaskGateSDK.initialize(this, "your_provider_id")
    }
}
```

### 2. Add Deep Link Intent Filter

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask">

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

### 3. Handle Deep Links

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TaskGateSDK.handleIntent(intent)

        val task = TaskGateSDK.getPendingTask()
        if (task != null) {
            // TaskGate launch - show task screen
            showTaskScreen(task.taskId, task.appName)
        } else {
            // Normal launch
            setContentView(R.layout.activity_main)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TaskGateSDK.handleIntent(intent)

        TaskGateSDK.getPendingTask()?.let { task ->
            showTaskScreen(task.taskId, task.appName)
        }
    }
}
```

### 4. Report Completion

```kotlin
TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.OPEN)       // User wants to open blocked app
TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.FOCUS)      // User wants to stay focused
TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.CANCELLED)  // User cancelled the task
```

---

## Flutter Integration

For Flutter apps, use `setTaskCallback()` to receive warm start notifications:

```kotlin
// MainActivity.kt
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "taskgate")

        // Callback for warm start (app already running)
        TaskGateSDK.setTaskCallback { task ->
            channel.invokeMethod("onTaskReceived", mapOf(
                "taskId" to task.taskId,
                "appName" to task.appName
            ))
        }

        // Methods for Flutter to call
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getPendingTask" -> {
                    val task = TaskGateSDK.getPendingTask()
                    result.success(task?.let {
                        mapOf("taskId" to it.taskId, "appName" to it.appName)
                    })
                }
                "reportCompletion" -> {
                    val status = TaskGateSDK.CompletionStatus.valueOf(
                        call.argument<String>("status")!!.uppercase()
                    )
                    TaskGateSDK.reportCompletion(status)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TaskGateSDK.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        TaskGateSDK.handleIntent(intent)  // Triggers callback
    }
}
```

```dart
// In Flutter
class TaskGateService {
  static const _channel = MethodChannel('taskgate');

  static void init() {
    // Listen for warm start tasks
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onTaskReceived') {
        final taskId = call.arguments['taskId'];
        final appName = call.arguments['appName'];
        // Navigate to task screen
      }
    });
  }

  // Check on cold start
  static Future<Map?> getPendingTask() async {
    return await _channel.invokeMethod('getPendingTask');
  }

  static Future<void> reportCompletion(String status) async {
    await _channel.invokeMethod('reportCompletion', {'status': status});
  }
}
```

---

## API Reference

| Method                      | Description                                    |
| --------------------------- | ---------------------------------------------- |
| `initialize(context, id)`   | Initialize SDK (call in Application)           |
| `handleIntent(intent)`      | Parse deep link, returns true if TaskGate link |
| `setTaskCallback(callback)` | Set callback for warm start notifications      |
| `getPendingTask()`          | Returns `TaskInfo?` (null = normal launch)     |
| `reportCompletion(status)`  | Report result and redirect back to TaskGate    |

### TaskInfo

```kotlin
data class TaskInfo(
    val taskId: String,
    val appName: String?,
    val sessionId: String,
    val callbackUrl: String
)
```

### CompletionStatus

| Status      | Description                    |
| ----------- | ------------------------------ |
| `OPEN`      | User wants to open blocked app |
| `FOCUS`     | User wants to stay focused     |
| `CANCELLED` | User cancelled the task        |

---

## Installation

### Gradle (Maven Central) - Recommended

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("co.taskgate:sdk:1.0.15")
}
```

### Gradle (JitPack)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.taskgate:taskgate-sdk-android:1.0.0")
}
```

---

## Becoming a Partner

Visit **[taskgate.co](https://taskgate.co)** to learn more about partnership opportunities.

**[Contact us](https://taskgate.co/contact-us)** to register and get your `providerId`.

---

## Support

- Website: [taskgate.co](https://taskgate.co)
- Contact: [taskgate.co/contact-us](https://taskgate.co/contact-us)
- Docs: [taskgate.co/partnership](https://taskgate.co/partnership)
