// JNI shim behind com.termux.ai.TaiGpuProbe: two read-only questions about the phone's GPU, asked
// without a GL context and without linking a graphics library.
//
// 1. Does an OpenCL driver exist? LiteRT-LM's GPU path on Android is OpenCL, and a failed GPU load
//    has no automatic CPU fallback, so a phone with no usable libOpenCL.so must never be offered the
//    GPU (tai-device-tiers spec §2.2). OpenCL lives in the vendor partition on most phones; apps
//    reach it either through the platform's public-library list (a plain dlopen) or, where the
//    vendor keeps it private, through android_load_sphal_library from libvndksupport.so. Pixels ship
//    the driver as libOpenCL-pixel.so.
// 2. Which GPU is it? The Vulkan vendorID names the family (0x5143 Qualcomm, 0x13B5 ARM, 0x1010
//    Imagination, 0x144D Samsung) without any permission or context.
//
// Everything is dlopen / dlsym, so a phone lacking either library answers "no" instead of failing to
// load this one. Each step is guarded, and every failure is a quiet "no answer". The libraries are
// left open on purpose: unloading a vendor graphics driver is a known source of crashes.
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <android/log.h>
#include <vulkan/vulkan.h>

#define TAG "TaiGpuProbe"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// OpenCL's headers are not in the NDK; clGetPlatformIDs takes only these two plain types.
typedef int32_t cl_int;
typedef uint32_t cl_uint;
typedef struct _cl_platform_id *cl_platform_id;
typedef cl_int (*clGetPlatformIDs_t)(cl_uint num_entries, cl_platform_id *platforms, cl_uint *num_platforms);
typedef void *(*android_load_sphal_library_t)(const char *name, int flags);

typedef struct _cl_device_id *cl_device_id;
typedef uint64_t cl_device_type;
typedef uint32_t cl_device_info;
typedef cl_int (*clGetDeviceIDs_t)(cl_platform_id platform, cl_device_type type, cl_uint num_entries,
                                   cl_device_id *devices, cl_uint *num_devices);
typedef cl_int (*clGetDeviceInfo_t)(cl_device_id device, cl_device_info param, size_t value_size,
                                    void *value, size_t *value_size_ret);

#define CL_SUCCESS 0
#define CL_DEVICE_TYPE_GPU (1ULL << 2)
#define CL_DEVICE_NAME 0x102B
#define CL_DRIVER_VERSION 0x102D

// Opens libOpenCL.so with the app's own namespace first, then through the vendor (sphal) loader,
// then the Pixel name. Returns NULL when every route fails.
static void *open_opencl(void) {
    void *lib = dlopen("libOpenCL.so", RTLD_NOW | RTLD_LOCAL);
    if (lib != NULL) return lib;
    void *vndk = dlopen("libvndksupport.so", RTLD_NOW | RTLD_LOCAL);
    if (vndk == NULL) return NULL;
    android_load_sphal_library_t sphal =
        (android_load_sphal_library_t) dlsym(vndk, "android_load_sphal_library");
    if (sphal == NULL) return NULL;
    lib = sphal("libOpenCL.so", RTLD_NOW);
    if (lib != NULL) return lib;
    return sphal("libOpenCL-pixel.so", RTLD_NOW);
}

JNIEXPORT jboolean JNICALL
Java_com_termux_ai_TaiGpuProbe_nativeOpenClAvailable(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    void *lib = open_opencl();
    if (lib == NULL) {
        LOGI("no OpenCL library");
        return JNI_FALSE;
    }
    clGetPlatformIDs_t getPlatforms = (clGetPlatformIDs_t) dlsym(lib, "clGetPlatformIDs");
    if (getPlatforms == NULL) {
        LOGI("OpenCL library has no clGetPlatformIDs");
        return JNI_FALSE;
    }
    cl_uint count = 0;
    cl_int status = getPlatforms(0, NULL, &count);
    LOGI("clGetPlatformIDs status=%d platforms=%u", (int) status, (unsigned) count);
    return (status == CL_SUCCESS && count > 0) ? JNI_TRUE : JNI_FALSE;
}

// "deviceName|driverVersion" of the first OpenCL GPU device, or null when OpenCL is absent or has no
// GPU device. Adreno's driver string carries the shader compiler ("... Compiler E031.47.12.03"), which
// LiteRT-LM itself warns about on the 8xx series. Reads two strings; creates no context.
JNIEXPORT jstring JNICALL
Java_com_termux_ai_TaiGpuProbe_nativeOpenClDevice(JNIEnv *env, jclass clazz) {
    (void) clazz;
    void *lib = open_opencl();
    if (lib == NULL) return NULL;
    clGetPlatformIDs_t getPlatforms = (clGetPlatformIDs_t) dlsym(lib, "clGetPlatformIDs");
    clGetDeviceIDs_t getDevices = (clGetDeviceIDs_t) dlsym(lib, "clGetDeviceIDs");
    clGetDeviceInfo_t getInfo = (clGetDeviceInfo_t) dlsym(lib, "clGetDeviceInfo");
    if (getPlatforms == NULL || getDevices == NULL || getInfo == NULL) return NULL;

    cl_platform_id platform = NULL;
    cl_uint platforms = 0;
    if (getPlatforms(1, &platform, &platforms) != CL_SUCCESS || platforms == 0 || platform == NULL) return NULL;
    cl_device_id device = NULL;
    cl_uint devices = 0;
    if (getDevices(platform, CL_DEVICE_TYPE_GPU, 1, &device, &devices) != CL_SUCCESS
        || devices == 0 || device == NULL) {
        return NULL;
    }
    char name[256];
    char driver[256];
    memset(name, 0, sizeof(name));
    memset(driver, 0, sizeof(driver));
    // A string that will not read stays empty; the Java side treats an empty one as unknown.
    if (getInfo(device, CL_DEVICE_NAME, sizeof(name) - 1, name, NULL) != CL_SUCCESS) name[0] = '\0';
    if (getInfo(device, CL_DRIVER_VERSION, sizeof(driver) - 1, driver, NULL) != CL_SUCCESS) driver[0] = '\0';
    char buffer[sizeof(name) + sizeof(driver) + 2];
    snprintf(buffer, sizeof(buffer), "%s|%s", name, driver);
    LOGI("opencl device %s", buffer);
    return (*env)->NewStringUTF(env, buffer);
}

// "vendorID|deviceID|deviceName" of the first Vulkan physical device (IDs in hex, "0x" prefix), or
// null when there is no Vulkan driver, no instance or no device. The name is last, since a name may
// hold any character.
JNIEXPORT jstring JNICALL
Java_com_termux_ai_TaiGpuProbe_nativeVulkanDevice(JNIEnv *env, jclass clazz) {
    (void) clazz;
    void *lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (lib == NULL) return NULL;
    PFN_vkCreateInstance createInstance = (PFN_vkCreateInstance) dlsym(lib, "vkCreateInstance");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance) dlsym(lib, "vkDestroyInstance");
    PFN_vkEnumeratePhysicalDevices enumerate =
        (PFN_vkEnumeratePhysicalDevices) dlsym(lib, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties getProperties =
        (PFN_vkGetPhysicalDeviceProperties) dlsym(lib, "vkGetPhysicalDeviceProperties");
    if (createInstance == NULL || destroyInstance == NULL || enumerate == NULL || getProperties == NULL) {
        return NULL;
    }

    VkApplicationInfo app;
    memset(&app, 0, sizeof(app));
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "TaiGpuProbe";
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo info;
    memset(&info, 0, sizeof(info));
    info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    info.pApplicationInfo = &app;

    VkInstance instance = VK_NULL_HANDLE;
    if (createInstance(&info, NULL, &instance) != VK_SUCCESS || instance == VK_NULL_HANDLE) return NULL;

    jstring result = NULL;
    VkPhysicalDevice devices[8];
    uint32_t count = 8;
    VkResult listed = enumerate(instance, &count, devices);
    // VK_INCOMPLETE only means there were more than 8 devices; the first is all that is read.
    if ((listed == VK_SUCCESS || listed == VK_INCOMPLETE) && count > 0) {
        VkPhysicalDeviceProperties props;
        memset(&props, 0, sizeof(props));
        getProperties(devices[0], &props);
        char buffer[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE + 48];
        snprintf(buffer, sizeof(buffer), "0x%04x|0x%x|%s", (unsigned) props.vendorID,
                 (unsigned) props.deviceID, props.deviceName);
        LOGI("vulkan device %s", buffer);
        result = (*env)->NewStringUTF(env, buffer);
    }
    destroyInstance(instance, NULL);
    return result;
}
