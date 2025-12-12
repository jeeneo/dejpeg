# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses

-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }

-keep class ai.onnxruntime.TensorInfo { *; }
-keep class ai.onnxruntime.OnnxTensor { *; }
-keep class ai.onnxruntime.OrtEnvironment { *; }
-keep class ai.onnxruntime.OrtSession { *; }
-keep class ai.onnxruntime.OrtSession$Result { *; }
-keep class ai.onnxruntime.OrtSession$SessionOptions { *; }
-keep class ai.onnxruntime.OnnxValue { *; }
-keep class ai.onnxruntime.OrtException { *; }

-keepclasseswithmembers class * {
    public <init>(long, ai.onnxruntime.OrtEnvironment);
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keep class ai.onnxruntime.OrtEnvironment {
    static ai.onnxruntime.OrtEnvironment getEnvironment();
}

-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# -optimizationpasses 5
# -allowaccessmodification
# -mergeinterfacesaggressively

# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
#     public static *** w(...);
#     public static *** e(...);
# }

# -dontwarn kotlin.**
# -dontwarn kotlinx.**