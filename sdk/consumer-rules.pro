# Consumer rules for apps that enable R8/ProGuard.
# Keep public SDK API contracts stable (callbacks, models, enums).

-keep class com.tjlabs.tjlabscommon_sdk_android.rfd.RFDGenerator { public *; }
-keep class com.tjlabs.tjlabscommon_sdk_android.rfd.RFDGenerator$RFDCallback { *; }
-keep class com.tjlabs.tjlabscommon_sdk_android.rfd.ReceivedForce { public *; }
-keep class com.tjlabs.tjlabscommon_sdk_android.rfd.RFDErrorCode { *; }
-keep enum com.tjlabs.tjlabscommon_sdk_android.rfd.ScanMode { *; }

-keep class com.tjlabs.tjlabscommon_sdk_android.uvd.UVDGenerator { public *; }
-keep class com.tjlabs.tjlabscommon_sdk_android.uvd.UVDGenerator$UVDCallback { *; }
-keep class com.tjlabs.tjlabscommon_sdk_android.uvd.UserVelocity { public *; }
-keep class com.tjlabs.tjlabscommon_sdk_android.uvd.UnitDistance { public *; }
-keep enum com.tjlabs.tjlabscommon_sdk_android.uvd.UserMode { *; }
-keep enum com.tjlabs.tjlabscommon_sdk_android.uvd.RmsStopThresholdUpdateType { *; }

-keep class com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataManager { public *; }
-keep class com.tjlabs.tjlabscommon_sdk_android.simulation.JupiterDataManager$JupiterEventCode { *; }

-keep class com.tjlabs.tjlabscommon_sdk_android.TJLabsErrorCodeManager { *; }
-keep class com.tjlabs.tjlabscommon_sdk_android.TJLabsErrorCode { public *; }
-keep enum com.tjlabs.tjlabscommon_sdk_android.TJLabsErrorDomain { *; }
