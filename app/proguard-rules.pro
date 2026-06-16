# --- Harper grammar engine (JNI) ---
# libharper_jni.so binds by the mangled Java name
# (Java_com_wasimaster_wmkeyboard_core_grammar_HarperNative_*), so neither the
# class name nor the external method names may be renamed or stripped.
-keep class com.wasimaster.wmkeyboard.core.grammar.HarperNative { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# --- kotlinx.serialization ---
# The library ships consumer rules, but the grammar lint models are decoded
# from JSON produced by native code — keep their serializers explicitly so a
# rules regression can never silently break offline grammar checking.
-keep,includedescriptorclasses class com.wasimaster.wmkeyboard.core.grammar.**$$serializer { *; }
-keepclassmembers class com.wasimaster.wmkeyboard.core.grammar.* {
    *** Companion;
}
-keepclasseswithmembers class com.wasimaster.wmkeyboard.core.grammar.* {
    kotlinx.serialization.KSerializer serializer(...);
}
