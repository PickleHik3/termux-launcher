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
