
-dontwarn javax.annotation.**
-dontwarn kotlin.**
-dontnote kotlin.**

# 필요한 클래스만 유지
-repackageclasses com.tjlabs.tjlabscommon.obf

-keep class com.tjlabs.tjlabscommon_sdk_android.model.** {
    public <methods>;
    public <fields>;
}

-keep class com.tjlabs.tjlabscommon_sdk_android.rfd.** {
    public <methods>;
    public <fields>;
}

-keep class com.tjlabs.tjlabscommon_sdk_android.utils.** {
    public <methods>;
    public <fields>;
}

-keep class com.tjlabs.tjlabscommon_sdk_android.uvd.** {
    public <methods>;
    public <fields>;
}