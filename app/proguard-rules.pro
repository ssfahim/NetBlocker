# NetBlocker ProGuard rules

# Keep model classes for Gson serialization
-keep class com.netblocker.models.** { *; }

# Keep service and receiver classes
-keep class com.netblocker.services.** { *; }
-keep class com.netblocker.receivers.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
