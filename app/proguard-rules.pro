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

-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**
# -keepclassmembers class * extends androidx.datastore.preferences.core.Preferences {
#     <fields>;
# }

# -keepclassmembers class kotlin.Metadata {
#     public <methods>;
# }
