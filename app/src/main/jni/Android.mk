LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE:= libtai_xnnpack
LOCAL_SRC_FILES:= tai_xnnpack.c
LOCAL_LDLIBS := -ldl -llog
include $(BUILD_SHARED_LIBRARY)
