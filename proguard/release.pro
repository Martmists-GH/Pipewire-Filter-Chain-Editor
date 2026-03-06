# Required for FileKit
-keep class org.freedesktop.dbus.** { *; }
-keep class io.github.vinceglb.filekit.dialogs.platform.xdg.** { *; }
-keepattributes Signature, InnerClasses, RuntimeVisibleAnnotations

# Optional logback dependencies
-dontwarn jakarta.servlet.**
-dontwarn jakarta.mail.**
-dontwarn org.codehaus.janino.**
-dontwarn org.codehaus.commons.compiler.**
-dontwarn org.tukaani.xz.**

# Logging
-keep class ch.qos.logback.** { *; }
-keep interface ch.qos.logback.** { *; }
-keep class org.slf4j.** { *; }
