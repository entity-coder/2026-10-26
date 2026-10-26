# Add project specific ProGuard rules here.
# Keep Room entities and DAOs (Room generates code at compile time, but
# keeping the model classes avoids surprises if minification is enabled).
-keep class com.billbook.app.data.** { *; }
