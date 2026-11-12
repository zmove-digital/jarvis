# Keep default Android optimizations; add project specific rules here.

# sherpa-onnx acessa as classes e os métodos nativos via JNI.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclassmembers class com.k2fsa.sherpa.onnx.** { *; }
