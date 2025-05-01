#include <jni.h>
#include <string>
#include <android/log.h>
#include <asm-generic/fcntl.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>

#define SET_HEATING     _IOW('myhumiture', 0, int)
#define SET_HUMIDIFYING _IOW('myhumiture', 1, int)
#define SET_FAN         _IOW('myhumiture', 2, int)
#define SET_PUMP        _IOW('myhumiture', 3, int)

#define GET_TEMPERATURE _IOR('myhumiture', 0, int)
#define GET_HUMIDITY _IOR('myhumiture', 1, int)
#define GET_WATER_LEVEL _IOR('myhumiture', 2, int)

int fd;
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_humiturejni_MainActivity_MyDeviceOpen(
        JNIEnv* env,
        jobject /* this */) {
    fd = open("/dev/myhumiture", O_RDWR | O_NDELAY | O_NOCTTY);

    if (fd < 0) {
        __android_log_print(ANDROID_LOG_INFO, "serial", "open error");
    }else {
        __android_log_print(ANDROID_LOG_INFO, "serial", "open success fd=%d",fd);
    }
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_humiturejni_MainActivity_MyDeviceClose(
        JNIEnv* env,
        jobject /* this */) {
    if (fd != -1) {
        __android_log_print(ANDROID_LOG_INFO, "serial", "Closing device fd=%d", fd); // 添加关闭日志
        close(fd);
        fd = -1;
    }
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_humiturejni_MainActivity_CmdIoctl(JNIEnv *env, jobject, jint cmd, jint arg) {
    if(cmd == 0){
        ioctl(fd, SET_HEATING, arg);
    }else if(cmd == 1){
        ioctl(fd, SET_HUMIDIFYING, arg);
    }else if(cmd == 2){
        ioctl(fd, SET_FAN, arg);
    }else if(cmd == 3){
        ioctl(fd, SET_PUMP, arg);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_humiturejni_MainActivity_getTemperature(JNIEnv *env, jobject) {
    int temperature;
    ioctl(fd, GET_TEMPERATURE, &temperature);
    __android_log_print(ANDROID_LOG_INFO, "serial", "c_temperature=%d", temperature);
    return temperature;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_humiturejni_MainActivity_getHumidity(JNIEnv *env, jobject) {
    int humidity;
    ioctl(fd, GET_HUMIDITY, &humidity);
    __android_log_print(ANDROID_LOG_INFO, "serial", "c_humidity=%d", humidity);
    return humidity;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_humiturejni_MainActivity_getWaterLevel(JNIEnv *env, jobject) {
    int water_level;
    ioctl(fd, GET_WATER_LEVEL, &water_level);
    __android_log_print(ANDROID_LOG_INFO, "serial", "c_water_level=%d", water_level);
    return water_level;
}


