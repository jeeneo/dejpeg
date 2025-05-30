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

# --- ONNX Runtime: Keep all required classes and native methods ---
-keepclasseswithmembernames class * {
    native <methods>;
}

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses

# Keep the entire ONNX Runtime package
-keep class ai.onnxruntime.** { *; }
-keep class com.microsoft.onnxruntime.** { *; }

# Specifically protect core ONNX classes
-keep class ai.onnxruntime.TensorInfo { *; }
-keep class ai.onnxruntime.OnnxTensor { *; }
-keep class ai.onnxruntime.OrtEnvironment { *; }
-keep class ai.onnxruntime.OrtSession { *; }
-keep class ai.onnxruntime.OrtSession$Result { *; }
-keep class ai.onnxruntime.OrtSession$SessionOptions { *; }
-keep class ai.onnxruntime.OnnxValue { *; }
-keep class ai.onnxruntime.OrtException { *; }

# Keep constructors that are called from native
-keepclasseswithmembers class * {
    public <init>(long, ai.onnxruntime.OrtEnvironment);
}

# Keep all native method names and signatures
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep ONNX Runtime initialization
-keep class ai.onnxruntime.OrtEnvironment {
    static ai.onnxruntime.OrtEnvironment getEnvironment();
}

# Prevent obfuscation of JNI interfaces
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

# --- End ONNX Runtime rules ---