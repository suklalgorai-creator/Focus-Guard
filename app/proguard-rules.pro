# FocusGuard ProGuard / R8 Rules
# Keep Room database and entity/DAO classes used by generated _Impl code
-keep class com.focusguard.app.persistence.FocusGuardDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Keep Android manifest component entry points (AAPT2 also generates rules for manifest classes)
-keep public class com.focusguard.app.detection.AppDetectorService
-keep public class com.focusguard.app.service.GuardForegroundService
-keep public class com.focusguard.app.service.FallbackBlockActivity
-keep public class com.focusguard.app.service.BootReceiver
-keep public class com.focusguard.app.service.WatchdogReceiver
-keep public class com.focusguard.app.antibypass.GuardAdminReceiver
-keep public class com.focusguard.app.integration.studyflow.StudyFlowSyncReceiver

# Keep WorkManager Workers instantiated via reflection by WorkManager
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Strip verbose/debug/info logs in release builds while keeping warnings and errors
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
