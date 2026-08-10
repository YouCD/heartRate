# Add project specific ProGuard rules here.

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# Kotlinx Coroutines
-dontwarn kotlinx.coroutines.**
