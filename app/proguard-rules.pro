# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep AppSearch entities
-keep @androidx.appsearch.annotation.Document class * { *; }

# AppSearch loads generated converters by class name and invokes their no-arg constructor.
# Keeping the annotated document alone leaves that constructor removable in optimized builds.
-keep class ** implements androidx.appsearch.app.DocumentClassFactory { *; }
