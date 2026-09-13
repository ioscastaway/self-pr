# Minification is off for this experiment. Kept so the release build type has a file to point at.
# Anthropic Java SDK uses Jackson reflection for (de)serialization.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.fasterxml.jackson.**
