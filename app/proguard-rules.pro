# Keep libv2ray (Go-generated bindings) and the callback interface intact.
-keep class libv2ray.** { *; }
-keep interface libv2ray.** { *; }
-keep class go.** { *; }

# Room / Compose handled by their own consumer rules.
