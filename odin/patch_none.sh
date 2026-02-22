#!/bin/bash

cd odinroot/odin

git checkout -f e050ac80deca5c2f76633f0054b73c6cb7d2d251
git reset --hard e050ac80deca5c2f76633f0054b73c6cb7d2d251

git apply << 'EOF'
diff --git a/CMakeLists.txt b/CMakeLists.txt
index d11e3b6..733fd9d 100644
--- a/CMakeLists.txt
+++ b/CMakeLists.txt
@@ -53,29 +53,31 @@ mark_as_advanced(OIDN_WARN_AS_ERRORS)
 set(OIDN_WEIGHTS)
 
 if(OIDN_FILTER_RT)
-  list(APPEND OIDN_WEIGHTS
-    weights/rt_alb.tza
-    weights/rt_alb_large.tza
-    weights/rt_hdr.tza
-    weights/rt_hdr_small.tza
-    weights/rt_hdr_alb.tza
-    weights/rt_hdr_alb_small.tza
-    weights/rt_hdr_alb_nrm.tza
-    weights/rt_hdr_alb_nrm_small.tza
-    weights/rt_hdr_calb_cnrm.tza
-    weights/rt_hdr_calb_cnrm_small.tza
-    weights/rt_hdr_calb_cnrm_large.tza
-    weights/rt_ldr.tza
-    weights/rt_ldr_small.tza
-    weights/rt_ldr_alb.tza
-    weights/rt_ldr_alb_small.tza
-    weights/rt_ldr_alb_nrm.tza
-    weights/rt_ldr_alb_nrm_small.tza
-    weights/rt_ldr_calb_cnrm.tza
-    weights/rt_ldr_calb_cnrm_small.tza
-    weights/rt_nrm.tza
-    weights/rt_nrm_large.tza
-  )
+  if(NOT ANDROID)
+    list(APPEND OIDN_WEIGHTS
+      weights/rt_alb.tza
+      weights/rt_alb_large.tza
+      weights/rt_hdr.tza
+      weights/rt_hdr_small.tza
+      weights/rt_hdr_alb.tza
+      weights/rt_hdr_alb_small.tza
+      weights/rt_hdr_alb_nrm.tza
+      weights/rt_hdr_alb_nrm_small.tza
+      weights/rt_hdr_calb_cnrm.tza
+      weights/rt_hdr_calb_cnrm_small.tza
+      weights/rt_hdr_calb_cnrm_large.tza
+      weights/rt_ldr.tza
+      weights/rt_ldr_small.tza
+      weights/rt_ldr_alb.tza
+      weights/rt_ldr_alb_small.tza
+      weights/rt_ldr_alb_nrm.tza
+      weights/rt_ldr_alb_nrm_small.tza
+      weights/rt_ldr_calb_cnrm.tza
+      weights/rt_ldr_calb_cnrm_small.tza
+      weights/rt_nrm.tza
+      weights/rt_nrm_large.tza
+    )
+  endif()
 endif()
 
 if(OIDN_FILTER_RTLIGHTMAP)
diff --git a/apps/CMakeLists.txt b/apps/CMakeLists.txt
index 9caeec9..16a55e9 100644
--- a/apps/CMakeLists.txt
+++ b/apps/CMakeLists.txt
@@ -11,4 +11,7 @@ endmacro()
 
 oidn_add_app(oidnDenoise oidnDenoise.cpp)
 oidn_add_app(oidnBenchmark oidnBenchmark.cpp)
-oidn_add_app(oidnTest oidnTest.cpp "${PROJECT_SOURCE_DIR}/external/catch.hpp")
\ No newline at end of file
+oidn_add_app(oidnTest oidnTest.cpp "${PROJECT_SOURCE_DIR}/external/catch.hpp")
+if(ANDROID)
+  target_link_libraries(oidnTest PRIVATE log)
+endif()
\ No newline at end of file
diff --git a/core/rt_filter.cpp b/core/rt_filter.cpp
index 72ffb2d..5492b01 100644
--- a/core/rt_filter.cpp
+++ b/core/rt_filter.cpp
@@ -4,7 +4,7 @@
 #include "rt_filter.h"
 
 // Default weights
-#if defined(OIDN_FILTER_RT)
+#if defined(OIDN_FILTER_RT) && !defined(__ANDROID__)
   #include "weights/rt_hdr.h"
   #include "weights/rt_hdr_small.h"
   #include "weights/rt_hdr_alb.h"
@@ -33,7 +33,7 @@ OIDN_NAMESPACE_BEGIN
   RTFilter::RTFilter(const Ref<Device>& device)
     : UNetFilter(device)
   {
-  #if defined(OIDN_FILTER_RT)
+  #if defined(OIDN_FILTER_RT) && !defined(__ANDROID__)
     models.hdr           = {blobs::weights::rt_hdr,
                             blobs::weights::rt_hdr_small};
     models.hdr_alb       = {blobs::weights::rt_hdr_alb,
diff --git a/core/thread.cpp b/core/thread.cpp
index 70abe42..a7886a4 100644
--- a/core/thread.cpp
+++ b/core/thread.cpp
@@ -8,6 +8,11 @@
 #if defined(__linux__)
   #include <sched.h>
   #include <unordered_set>
+  #if defined(__ANDROID__)
+    #include <unistd.h>
+    #include <sys/syscall.h>
+    static inline pid_t gettid_compat() { return (pid_t)syscall(SYS_gettid); }
+  #endif
 #elif defined(__APPLE__)
   #include <mach/thread_act.h>
   #include <mach/mach_init.h>
@@ -193,8 +198,20 @@ OIDN_NAMESPACE_BEGIN
     if (threadIndex < 0 || threadIndex >= (int)affinities.size())
       return;
 
+#if defined(__ANDROID__)
+    const pid_t tid = gettid_compat();
+    // Save the current affinity
+    if (sched_getaffinity(tid, sizeof(cpu_set_t), &oldAffinities[threadIndex]) != 0)
+    {
+      printWarning("sched_getaffinity failed");
+      oldAffinities[threadIndex] = affinities[threadIndex];
+      return;
+    }
+    // Set the new affinity
+    if (sched_setaffinity(tid, sizeof(cpu_set_t), &affinities[threadIndex]) != 0)
+      printWarning("sched_setaffinity failed");
+#else
     const pthread_t thread = pthread_self();
-
     // Save the current affinity
     if (pthread_getaffinity_np(thread, sizeof(cpu_set_t), &oldAffinities[threadIndex]) != 0)
     {
@@ -202,10 +219,10 @@ OIDN_NAMESPACE_BEGIN
       oldAffinities[threadIndex] = affinities[threadIndex];
       return;
     }
-
     // Set the new affinity
     if (pthread_setaffinity_np(thread, sizeof(cpu_set_t), &affinities[threadIndex]) != 0)
       printWarning("pthread_setaffinity_np failed");
+#endif
   }
 
   void ThreadAffinity::restore(int threadIndex)
@@ -213,11 +230,17 @@ OIDN_NAMESPACE_BEGIN
     if (threadIndex < 0 || threadIndex >= (int)affinities.size())
       return;
 
+#if defined(__ANDROID__)
+    const pid_t tid = gettid_compat();
+    // Restore the original affinity
+    if (sched_setaffinity(tid, sizeof(cpu_set_t), &oldAffinities[threadIndex]) != 0)
+      printWarning("sched_setaffinity failed");
+#else
     const pthread_t thread = pthread_self();
-
     // Restore the original affinity
     if (pthread_setaffinity_np(thread, sizeof(cpu_set_t), &oldAffinities[threadIndex]) != 0)
       printWarning("pthread_setaffinity_np failed");
+#endif
   }
 
   std::vector<int> ThreadAffinity::parseList(const std::string& filename)
diff --git a/devices/cpu/cpu_device.cpp b/devices/cpu/cpu_device.cpp
index 8963307..1cfcf42 100644
--- a/devices/cpu/cpu_device.cpp
+++ b/devices/cpu/cpu_device.cpp
@@ -47,24 +47,28 @@ OIDN_NAMESPACE_BEGIN
 
   std::vector<Ref<PhysicalDevice>> CPUDevice::getPhysicalDevices()
   {
-    CPUArch arch = getNativeArch();
-    if (arch == CPUArch::Unknown)
-      return {};
+    #if defined(__ANDROID__)
+      int score = (1 << 16) + 61;
+      return {makeRef<CPUPhysicalDevice>(score)};
+    #else
+      CPUArch arch = getNativeArch();
+      if (arch == CPUArch::Unknown)
+        return {};
 
-    int score = 0;
-    switch (arch)
-    {
-    case CPUArch::AVX512_AMXFP16:
-      score = (15 << 16);
-      break;
+      int score = 0;
+      switch (arch)
+      {
+      case CPUArch::AVX512_AMXFP16:
+        score = (15 << 16);
+        break;
 
-    default:
-      // Prefer the CPU over some low-power integrated GPUs
-      score = (1 << 16) + 61;
-      break;
-    }
+      default:
+        score = (1 << 16) + 61;
+        break;
+      }
 
-    return {makeRef<CPUPhysicalDevice>(score)};
+      return {makeRef<CPUPhysicalDevice>(score)};
+    #endif
   }
 
   std::string CPUDevice::getName()
@@ -103,7 +107,13 @@ OIDN_NAMESPACE_BEGIN
     case ispc::CPUArch_AVX512:         return CPUArch::AVX512;
     case ispc::CPUArch_AVX512_AMXFP16: return CPUArch::AVX512_AMXFP16;
     case ispc::CPUArch_NEON:           return CPUArch::NEON;
-    default:                           return CPUArch::Unknown;
+    default:
+      // On Android ARM64, ISPC detection may fail but NEON is available
+      #if defined(__ANDROID__) && defined(__aarch64__)
+        return CPUArch::NEON;
+      #else
+        return CPUArch::Unknown;
+      #endif
     }
   }
 

EOF
