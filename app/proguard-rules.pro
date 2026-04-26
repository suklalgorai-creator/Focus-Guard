# FocusGuard ProGuard Rules
# Keep Room entities
-keep class com.focusguard.app.persistence.** { *; }

# Keep Accessibility Service
-keep class com.focusguard.app.detection.AppDetectorService { *; }

# Keep BroadcastReceivers
-keep class com.focusguard.app.service.BootReceiver { *; }
-keep class com.focusguard.app.service.WatchdogReceiver { *; }
