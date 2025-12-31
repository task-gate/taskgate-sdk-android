# TaskGate Android SDK

Enable your Android app to provide micro-tasks for TaskGate users.

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

### 2. Add Deep Link Intent Filter

Add the intent filter directly on your `MainActivity`:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask">

    <!-- Your existing intent filters... -->

    <!-- TaskGate deep link -->
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

> **Note:** Use `android:launchMode="singleTask"` to ensure warm starts use `onNewIntent()`.

### 3. Handle Deep Links in MainActivity

```kotlin
class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Handle cold start deep link
        TaskGateSDK.handleIntent(intent)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val methodChannel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "com.yourapp/taskgate"
        )

        // Register channel with SDK for warm start notifications
        TaskGateSDK.setFlutterChannel(object : TaskGateSDK.FlutterChannel {
            override fun invokeMethod(method: String, arguments: Map<String, Any?>) {
                methodChannel.invokeMethod(method, arguments)
            }
        })

        // Handle calls FROM Flutter
        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getPendingTask" -> {
                    val taskId = TaskGateSDK.getPendingTaskId()
                    val appName = TaskGateSDK.getPendingAppName()
                    if (taskId != null) {
                        result.success(mapOf("taskId" to taskId, "appName" to appName))
                    } else {
                        result.success(null)
                    }
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
                else -> result.notImplemented()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle warm start - SDK automatically notifies Flutter
        TaskGateSDK.handleNewIntent(intent)
    }
}
```

### 4. Handle TaskGate Tasks in Flutter

Handle **both cold starts and warm starts**:

```dart
const _channel = MethodChannel('com.yourapp/taskgate');

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // COLD START: Check for pending TaskGate task BEFORE creating router
  Map<String, dynamic>? initialTask;
  try {
    final result = await _channel.invokeMethod('getPendingTask');
    if (result != null) {
      initialTask = Map<String, dynamic>.from(result);
    }
  } catch (_) {}

  runApp(MyApp(initialTask: initialTask));
}

class MyApp extends StatefulWidget {
  final Map<String, dynamic>? initialTask;
  const MyApp({super.key, this.initialTask});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  static const _channel = MethodChannel('com.yourapp/taskgate');
  final _navigatorKey = GlobalKey<NavigatorState>();

  @override
  void initState() {
    super.initState();

    // WARM START: Listen for incoming tasks when app is already running
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'onTaskReceived') {
        final taskInfo = Map<String, dynamic>.from(call.arguments);
        // Navigate to task screen
        _navigatorKey.currentState?.pushNamed('/task', arguments: taskInfo);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      navigatorKey: _navigatorKey,
      // Route to task screen if there's a pending task (cold start)
      initialRoute: widget.initialTask != null ? '/task' : '/',
      routes: {
        '/': (_) => HomeScreen(),
        '/task': (context) {
          final args = ModalRoute.of(context)?.settings.arguments;
          final taskInfo = widget.initialTask ??
            (args is Map<String, dynamic> ? args : null);
          return TaskScreen(taskInfo: taskInfo);
        },
      },
    );
  }
}
```

### 5. Report Completion

When the user completes the task:

```dart
class TaskScreen extends StatelessWidget {
  final Map<String, dynamic>? taskInfo;
  const TaskScreen({super.key, this.taskInfo});

  static const _channel = MethodChannel('com.yourapp/taskgate');

  Future<void> _onComplete(BuildContext context, String status) async {
    await _channel.invokeMethod('reportCompletion', {'status': status});
    if (context.mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final appName = taskInfo?['appName'] ?? 'the app';
    return Scaffold(
      appBar: AppBar(title: Text('Complete Task')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('Complete this task to open $appName'),
            SizedBox(height: 32),
            ElevatedButton(
              onPressed: () => _onComplete(context, 'open'),
              child: Text('Complete & Open App'),
            ),
            TextButton(
              onPressed: () => _onComplete(context, 'focus'),
              child: Text('Stay Focused'),
            ),
          ],
        ),
      ),
    );
  }
}
```

---

## How It Works

```
┌──────────────────────────────────────────────────────────────────┐
│                     TIMELINE OF EVENTS                           │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. User tries to open blocked app                               │
│                                                                  │
│  2. TaskGate shows redirect screen (its own splash UI)           │
│                                                                  │
│  3. TaskGate launches your app's deep link                       │
│     └── TaskGate immediately moves to background                 │
│                                                                  │
│  4. Your app starts (shows Android native splash screen)         │
│     └── SDK stores task info in SharedPreferences                │
│     └── Flutter initializes                                      │
│     └── Flutter checks getPendingTask() before creating router   │
│     └── Router starts at task screen                             │
│                                                                  │
│  5. User sees your task screen                                   │
│                                                                  │
│  6. User completes task                                          │
│     └── reportCompletion() → TaskGate handles result             │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Key points:**

- ✅ TaskGate immediately moves to background after launching your app
- ✅ Your app's native splash screen is visible during Flutter boot
- ✅ Check `getPendingTask()` BEFORE creating Flutter router
- ✅ For warm starts, SDK sends `onTaskReceived` to Flutter automatically

---

## What the SDK Handles

| Component              | You Handle         | SDK Handles  |
| ---------------------- | ------------------ | ------------ |
| **Deep link parsing**  | -                  | ✅ Automatic |
| **Store task info**    | -                  | ✅ Automatic |
| **Warm start events**  | -                  | ✅ Automatic |
| **Native splash**      | ✅ Your design     | -            |
| **Show task UI**       | ✅ Your design     | -            |
| **Report completion**  | ✅ Call SDK method | -            |

---

## Native App Integration

For native apps (no Flutter):

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle deep link
        TaskGateSDK.handleIntent(intent)

        // Check for pending task
        val taskId = TaskGateSDK.getPendingTaskId()
        val appName = TaskGateSDK.getPendingAppName()

        if (taskId != null) {
            // Show task UI
            showTaskScreen(taskId, appName)
        } else {
            // Normal app flow
            setContentView(R.layout.activity_main)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (TaskGateSDK.handleNewIntent(intent)) {
            // Task received during warm start
            val taskId = TaskGateSDK.getPendingTaskId()
            val appName = TaskGateSDK.getPendingAppName()
            showTaskScreen(taskId, appName)
        }
    }

    private fun showTaskScreen(taskId: String?, appName: String?) {
        // Your task UI implementation
    }

    fun onTaskComplete(status: TaskGateSDK.CompletionStatus) {
        TaskGateSDK.reportCompletion(status)
        // Clear task and return to normal flow
    }
}
```

---

## API Reference

### Initialization

| Method                           | Description                                         |
| -------------------------------- | --------------------------------------------------- |
| `initialize(context, providerId)` | Initialize SDK with your provider ID               |
| `setFlutterChannel(channel)`     | Register Flutter MethodChannel for warm start events |

### Handling Intents

| Method                    | Description                                                         |
| ------------------------- | ------------------------------------------------------------------- |
| `handleIntent(intent)`    | Handle cold start deep link. Call in onCreate().                    |
| `handleNewIntent(intent)` | Handle warm start. Call in onNewIntent(). Returns true if handled.  |
| `handleUri(uri)`          | Manually parse a TaskGate URI                                       |

### Getting Task Info

| Method                | Description                                           |
| --------------------- | ----------------------------------------------------- |
| `getPendingTaskId()`  | Get pending task ID (null if none or expired)         |
| `getPendingAppName()` | Get pending blocked app name                          |
| `hasPendingTask()`    | Check if there's a pending task in memory             |
| `clearPendingTask()`  | Clear pending task info after handling                |

> **Note:** Task info expires after 30 seconds to prevent stale tasks.

### Reporting Completion

| Method                     | Description                                 |
| -------------------------- | ------------------------------------------- |
| `reportCompletion(status)` | Report task outcome and return to TaskGate  |
| `cancelTask()`             | Shorthand for `reportCompletion(CANCELLED)` |

### Completion Status

| Status      | User Action                                           |
| ----------- | ----------------------------------------------------- |
| `OPEN`      | Completed task, wants to open the blocked app         |
| `FOCUS`     | Completed task, wants to stay focused (no app launch) |
| `CANCELLED` | Skipped or cancelled the task                         |

### Session Info

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
https://taskgate-app-links.onrender.com/task/completed
    ?status=open
    &provider_id=your_provider
    &session_id=abc12345
    &task_id=breathing_30s
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
2. Test with: `adb shell am start -a android.intent.action.VIEW -d "https://yourdomain.com/taskgate/start?task_id=test&callback_url=https://example.com"`
3. Check if your domain is verified for App Links

### App not receiving TaskGate intents

1. Ensure `android:launchMode="singleTask"` on your MainActivity
2. Make sure `TaskGateSDK.handleNewIntent(intent)` is called in `onNewIntent()`

### Task not completing

1. Verify you're calling `reportCompletion()` or `cancelTask()`
2. Check logcat for `TaskGateSDK` tag for debugging info

### Task info is null

1. Task info expires after 30 seconds - check timing
2. Make sure `TaskGateSDK.handleIntent(intent)` is called in onCreate()
3. Call `getPendingTask()` BEFORE creating your Flutter router

---

## Support

- Email: partners@taskgate.app
- Documentation: https://docs.taskgate.app/partners/android
- GitHub Issues: https://github.com/taskgate/taskgate-sdk-android/issues
