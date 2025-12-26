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

# Native methods
# https://www.guardsquare.com/en/products/proguard/manual/examples#native
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# App
-keep class com.dismal.files.** implements androidx.appcompat.view.CollapsibleActionView { *; }
-keep class com.dismal.files.provider.common.ByteString { *; }
-keep class com.dismal.files.provider.linux.syscall.** { *; }
-keepnames class * extends java.lang.Exception
# For Class.getEnumConstants()
-keepclassmembers enum * {
    public static **[] values();
}
-keepnames class com.dismal.files.** implements android.os.Parcelable

# Apache FtpServer
-keepclassmembers class * implements org.apache.mina.core.service.IoProcessor {
    public <init>(java.util.concurrent.ExecutorService);
    public <init>(java.util.concurrent.Executor);
    public <init>();
}

# Bouncy Castle
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

# Sora Editor and TextMate support - COMPREHENSIVE RULES
-keep class io.github.rosemoe.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-keep class org.eclipse.tm4e.core.** { *; }
-keep class org.eclipse.tm4e.languageconfiguration.** { *; }

# FileProviderRegistry and asset loading - CRITICAL
-keep class io.github.rosemoe.sora.langs.textmate.registry.** { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.provider.** { *; }
-keep class io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver { *; }

# TextMate JSON models - prevent Gson issues
-keep class * implements org.eclipse.tm4e.core.model.** { *; }
-keep class * extends org.eclipse.tm4e.core.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all TextMate grammar and theme related classes
-keep class org.eclipse.tm4e.core.grammar.** { *; }
-keep class org.eclipse.tm4e.core.theme.** { *; }
-keep class org.eclipse.tm4e.core.registry.** { *; }

# TextMate language and color scheme classes
-keep class io.github.rosemoe.sora.langs.textmate.TextMateLanguage { *; }
-keep class io.github.rosemoe.sora.langs.textmate.TextMateColorScheme { *; }
-keep class io.github.rosemoe.sora.widget.schemes.** { *; }

# Asset loading and file providers
-keep class * implements io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver { *; }
-keep class * extends io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver { *; }

# Prevent obfuscation of methods used by reflection
-keepclassmembers class io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry {
    public *;
    private *;
}
-keepclassmembers class io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry {
    public *;
    private *;
}
-keepclassmembers class io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry {
    public *;
    private *;
}

# Keep all registry model classes
-keep class io.github.rosemoe.sora.langs.textmate.registry.model.** { *; }

# Oniguruma regex engine - CRITICAL for TextMate
-keep class org.eclipse.tm4e.core.internal.oniguruma.** { *; }
-keep class * extends org.eclipse.tm4e.core.internal.oniguruma.** { *; }
-keepclassmembers class org.eclipse.tm4e.core.internal.oniguruma.** {
    public *;
    private *;
    static *;
}

# Native library loading for Oniguruma
-keep class * {
    static <methods>;
}

# Prevent obfuscation of native method signatures
-keepclasseswithmembernames class * {
    native <methods>;
}

# Gson serialization for TextMate
-keep class * implements com.google.gson.JsonSerializer { *; }
-keep class * implements com.google.gson.JsonDeserializer { *; }

# Ignore missing classes that are not available on Android
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn java.lang.invoke.MethodHandleProxies
-dontwarn java.rmi.**
-dontwarn javax.activation.**
-dontwarn javax.security.auth.**
-dontwarn javax.security.sasl.**
-dontwarn javax.servlet.**
-dontwarn org.springframework.**

# Keep classes that might be referenced by reflection
-keep class * extends java.lang.Exception
-keep class * implements java.io.Serializable

# Additional rules for libraries that reference desktop classes
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.Native$AWT
-dontwarn org.apache.commons.lang3.function.MethodInvokers
-dontwarn com.rapid7.client.dcerpc.**
-dontwarn jcifs.**
-dontwarn net.schmizz.sshj.**
-dontwarn org.apache.mina.**
-dontwarn org.apache.ftpserver.**
-keep class * implements com.google.gson.InstanceCreator { *; }

# Keep TextMate source interfaces and implementations
-keep class org.eclipse.tm4e.core.registry.IThemeSource { *; }
-keep class * implements org.eclipse.tm4e.core.registry.IThemeSource { *; }
-keep class org.eclipse.tm4e.core.grammar.IGrammarSource { *; }
-keep class * implements org.eclipse.tm4e.core.grammar.IGrammarSource { *; }

# Prevent obfuscation of reflection-used classes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# Keep asset manager related classes
-keep class android.content.res.AssetManager { *; }
-keep class android.content.res.AssetFileDescriptor { *; }

# Additional safety rules for Sora Editor
-dontwarn io.github.rosemoe.**
-dontwarn org.eclipse.tm4e.**

# Keep all public methods in Sora Editor classes
-keepclassmembers class io.github.rosemoe.** {
    public <methods>;
}

# Keep constructors for model classes
-keepclassmembers class * {
    public <init>(...);
}

# Critical: Keep all methods that might be called via reflection
-keepclassmembers class io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry {
    *** tryGetInputStream(...);
    *** addFileProvider(...);
    *** getInstance(...);
}

-keepclassmembers class io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry {
    *** loadGrammars(...);
    *** findGrammar(...);
    *** getInstance(...);
}

-keepclassmembers class io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry {
    *** setTheme(...);
    *** loadTheme(...);
    *** getInstance(...);
}

-keepclassmembers class io.github.rosemoe.sora.langs.textmate.TextMateLanguage {
    *** create(...);
    *** setCompleterKeywords(...);
}

-keepclassmembers class io.github.rosemoe.sora.langs.textmate.TextMateColorScheme {
    *** create(...);
}

# Keep AssetsFileResolver methods
-keepclassmembers class io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver {
    public <init>(...);
    *** *;
}

# SMBJ
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn sun.security.x509.X509Key

# SMBJ-RPC
-dontwarn java.rmi.UnmarshalException

# SnakeYAML (used by SoraEditor/TextMate)
-dontwarn java.beans.**

# SoraEditor and TextMate
-keep class io.github.rosemoe.** { *; }
-keep class org.eclipse.tm4e.** { *; }

