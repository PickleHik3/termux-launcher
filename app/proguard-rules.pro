# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontobfuscate
#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable

# Settings sub-screens are instantiated reflectively via app:fragment="..." strings in
# res/xml preference screens, which AAPT2 does not emit keep rules for. shrinkResources
# alone can drop an unreferenced fragment class before that string is resolved at runtime.
-keep class com.termux.app.fragments.settings.** extends androidx.fragment.app.Fragment

# ShizukuBackend releases finished remote processes by hand — the library links a binder death
# recipient and caches the process in a static Set, and releases neither, so every privileged
# command otherwise leaks a binder proxy and two file descriptors for the life of the app. The
# release reaches these members by name.
-keepclassmembers class rikka.shizuku.ShizukuRemoteProcess {
    private moe.shizuku.server.IRemoteProcess remote;
    private java.io.InputStream is;
    private java.io.OutputStream os;
    static java.util.Set CACHE;
}

# The privileged lane's Shizuku user service is instantiated by name, reflectively, in a
# process Shizuku starts from this APK as the shell uid; nothing in the app calls its
# constructors, so without this the shrinker would drop them.
-keep class com.termux.privileged.lane.PrivilegedLaneService { *; }

# Release builds keep every class of this app and its vendored modules whole and unrenamed:
# a lot of it is reached by name from outside the dex — app_process entry points (the X11
# server's CmdEntryPoint, the am library), JNI, Shizuku user services, intents and prefs that
# name classes — so R8 only trims the third-party libraries. The point of the release build
# type here is a non-debuggable runtime (AOT code, no JDWP), not a smaller dex.
-dontobfuscate
-keep class com.termux.** { *; }
-keep class juloo.keyboard2.** { *; }

# The on-device model runtimes' native code looks up Java methods and fields by name (LiteRT-LM
# reads its config objects' getters from nativeCreateConversation), and LiteRT-LM ships no
# consumer rules: a getter only native code calls is dropped, and every local chat aborts the
# tai_runtime process with "JNI DETECTED ERROR: mid == null". The MNN bridge lives in the app
# but outside com.termux.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.alibaba.mnnllm.** { *; }

# Hidden platform classes the X11 server's CmdEntryPoint links against; present at runtime.
-dontwarn android.app.ActivityThread
-dontwarn android.app.ContextImpl
-dontwarn android.app.IActivityManager
-dontwarn android.content.IIntentReceiver$Stub
-dontwarn android.content.IIntentReceiver
-dontwarn android.content.IIntentSender
-dontwarn android.content.pm.IPackageManager
