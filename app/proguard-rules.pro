# Keep JNI callback interface (called from native code)
-keep class io.github.jqssun.airplay.bridge.RaopCallbackHandler { *; }
-keep class * implements io.github.jqssun.airplay.bridge.RaopCallbackHandler { *; }
-keep class io.github.jqssun.airplay.bridge.LogListener { *; }
-keep class * implements io.github.jqssun.airplay.bridge.LogListener { *; }

# Keep NativeBridge native methods
-keep class io.github.jqssun.airplay.bridge.NativeBridge { *; }


# HiCar HUKS JNI entry points are resolved by name.
-keep class local.hicarlab.NativeAuth { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Manifest receivers/services are kept by AGP; keep reflective page lookups safe.
-keep class local.hicarlab.** { *; }
