# R8 rules for the release build (isMinifyEnabled / isShrinkResources).
# Libraries that ship their own consumer rules (Compose, AndroidX, Media3, WorkManager, OkHttp, ML Kit, Material) need
# nothing here. What follows covers the code that is reached through JNI or reflection, or that references classes
# which do not exist on Android.

# Readable crash logs: the app writes uncaught exceptions to a local text file, so keep file names and line numbers.
# Keep the mapping.txt of every release (CI uploads it with the APK) to translate the renamed names back.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*
-renamesourcefileattribute SourceFile

# --- sherpa-onnx (on-device speech recognition, VAD, speaker separation) ---
# The native library reads and writes these Kotlin classes by name (JNI field and method lookups).
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# --- LAME MP3 encoder (JNI) ---
-keep class com.github.axet.lamejni.** { *; }

# --- NewPipeExtractor (YouTube audio) ---
# Its own generated protobuf messages, JSON/timeago pattern lookups and the bundled Rhino JavaScript engine rely on
# names and reflection. Keep the whole extractor and Rhino; they are only a few MB.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn com.google.re2j.**
-dontwarn org.jsoup.**
-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**

# --- PDFBox for Android (reading PDFs) ---
# Loads fonts, glyph lists and filters by name; its optional JPEG2000 decoder is not bundled.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.bouncycastle.**

# --- Apache Commons Compress (tar + bzip2 for the downloaded speech models) ---
# Compress mentions many optional codecs (xz, zstd, brotli, asm ...) that are not on the class path.
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.apache.commons.lang3.**
-dontwarn org.osgi.**
-dontwarn aQute.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# --- Other optional dependencies of OkHttp and friends ---
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
