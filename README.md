# TaskGate Android SDK

Enable your Android app to provide micro-tasks for TaskGate users.

---

## Quick Start

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
// When user completes task
fun onTaskComplete() {
    TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.OPEN)
}

// When user wants to stay focused
fun onStayFocused() {
    TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.FOCUS)
}

// When user cancels
fun onCancel() {
    TaskGateSDK.reportCompletion(TaskGateSDK.CompletionStatus.CANCELLED)
}
```

---

## API Reference

| Method                            | Description                                |
| --------------------------------- | ------------------------------------------ |
| `initialize(context, providerId)` | Initialize SDK                             |
| `handleIntent(intent)`            | Parse deep link                            |
| `getPendingTask()`                | Returns `TaskInfo?` (null = normal launch) |
| `reportCompletion(status)`        | Report result and clear state              |

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

## Support

- Email: partners@taskgate.app
- Docs: https://docs.taskgate.app/partners/android
