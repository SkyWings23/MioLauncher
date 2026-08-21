LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := pojavexec
LOCAL_LDLIBS := -ldl -llog -landroid
LOCAL_CFLAGS := -DADRENO_POSSIBLE
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/environ \
    $(LOCAL_PATH)/ctxbridges
LOCAL_SRC_FILES := \
    jre_launcher.c \
    utils.c \
    stdio_is.c \
    environ/environ.c
include $(BUILD_SHARED_LIBRARY)
