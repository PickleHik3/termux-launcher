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
