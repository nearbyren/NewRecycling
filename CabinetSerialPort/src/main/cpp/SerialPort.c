/*
 * Copyright 2009-2011 Cedric Priscal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <termios.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <jni.h>

#include "SerialPort.h"

#include "android/log.h"

static const char *TAG = "serial_port";
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)

static speed_t getBaudrate(jint baudrate) {
    switch (baudrate) {
        case 0:
            return B0;
        case 50:
            return B50;
        case 75:
            return B75;
        case 110:
            return B110;
        case 134:
            return B134;
        case 150:
            return B150;
        case 200:
            return B200;
        case 300:
            return B300;
        case 600:
            return B600;
        case 1200:
            return B1200;
        case 1800:
            return B1800;
        case 2400:
            return B2400;
        case 4800:
            return B4800;
        case 9600:
            return B9600;
        case 19200:
            return B19200;
        case 38400:
            return B38400;
        case 57600:
            return B57600;
        case 115200:
            return B115200;
        case 230400:
            return B230400;
        case 460800:
            return B460800;
        case 500000:
            return B500000;
        case 576000:
            return B576000;
        case 921600:
            return B921600;
        case 1000000:
            return B1000000;
        case 1152000:
            return B1152000;
        case 1500000:
            return B1500000;
        case 2000000:
            return B2000000;
        case 2500000:
            return B2500000;
        case 3000000:
            return B3000000;
        case 3500000:
            return B3500000;
        case 4000000:
            return B4000000;
        default:
            return -1;
    }
}

/*
 * Class:     android_serialport_SerialPort
 * Method:    open
 * Signature: (Ljava/lang/String;II)Ljava/io/FileDescriptor;
 */
JNIEXPORT jobject JNICALL Java_com_serial_port_SerialPort_open
        (JNIEnv *env, jclass thiz, jstring path, jint baudrate, jint flags) {
    int fd;
    speed_t speed;
    jobject mFileDescriptor;

    /* Check arguments */
    {
        speed = getBaudrate(baudrate);
        if (speed == -1) {
            /* TODO: throw an exception */
            LOGE("授权成功 Invalid baudrate");
            return NULL;
        }
    }

    /* Opening device */
    {
        jboolean iscopy;
        const char *path_utf = (*env)->GetStringUTFChars(env, path, &iscopy);
        LOGD("授权成功 Opening serial port %s with flags 0x%x", path_utf, O_RDWR | flags);
        fd = open(path_utf, O_RDWR | flags);
        LOGD("授权成功 open() fd = %d", fd);
        (*env)->ReleaseStringUTFChars(env, path, path_utf);
        if (fd == -1) {
            /* Throw an exception */
            LOGE("授权成功 Cannot open port");
            /* TODO: throw an exception */
            return NULL;
        }
    }

    /* Configure device */
    {
        struct termios cfg;
        LOGD("授权成功 Configuring serial port");
        if (tcgetattr(fd, &cfg)) {
            LOGE("授权成功 tcgetattr() failed");
            close(fd);
            /* TODO: throw an exception */
            return NULL;
        }

        cfmakeraw(&cfg);
        cfsetispeed(&cfg, speed);
        cfsetospeed(&cfg, speed);

        if (tcsetattr(fd, TCSANOW, &cfg)) {
            LOGE("授权成功 tcsetattr() failed");
            close(fd);
            /* TODO: throw an exception */
            return NULL;
        }
    }

    /* Create a corresponding file descriptor */
    {
        jclass cFileDescriptor = (*env)->FindClass(env, "java/io/FileDescriptor");
        jmethodID iFileDescriptor = (*env)->GetMethodID(env, cFileDescriptor, "<init>", "()V");
        jfieldID descriptorID = (*env)->GetFieldID(env, cFileDescriptor, "descriptor", "I");
        mFileDescriptor = (*env)->NewObject(env, cFileDescriptor, iFileDescriptor);
        (*env)->SetIntField(env, mFileDescriptor, descriptorID, (jint) fd);
    }

    return mFileDescriptor;
}

/*
 * Class:     cedric_serial_SerialPort
 * Method:    close
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_serial_port_SerialPort_close
        (JNIEnv *env, jobject thiz, jint closeFdType) {

    jclass SerialPortClass = (*env)->GetObjectClass(env, thiz);
    jclass FileDescriptorClass = (*env)->FindClass(env, "java/io/FileDescriptor");

    jfieldID serialVMFieldID = (*env)->GetFieldID(env, SerialPortClass, "serialVM",
                                                  "Lcom/serial/port/vm/SerialVM;");
    LOGD("授权成功 执行close");
    jobject serialVMObject = (*env)->GetObjectField(env, thiz, serialVMFieldID);
    if (serialVMObject == NULL) {
        LOGD("授权成功 SerialVM object is NULL");
        return;
    }
    jclass serialVMClass = (*env)->GetObjectClass(env, serialVMObject);
    if (closeFdType == 1) {

        // 获取 SerialVM 类中的 fd232 字段
        jfieldID mFdID = (*env)->GetFieldID(env, serialVMClass, "fd232",
                "Lkotlinx/coroutines/flow/MutableStateFlow;");
        if (mFdID == NULL) {
            LOGD("授权成功 Field fd232 not found in SerialVM");
            return;
        }

        // 获取 232fd 字段的值
        jobject mFdObject = (*env)->GetObjectField(env, serialVMObject, mFdID);
        if (mFdObject == NULL) {
            LOGD("授权成功 fd232 is NULL");
            return;
        }

        // 获取 MutableStateFlow 的 value 字段，注意 fd232 持有的是 FileDescriptor 类型的对象
        jclass mutableStateFlowClass = (*env)->GetObjectClass(env, mFdObject);
        jmethodID getValueMethodID = (*env)->GetMethodID(env, mutableStateFlowClass, "getValue",
                "()Ljava/lang/Object;");
        if (getValueMethodID == NULL) {
            LOGD("授权成功 fd232 Method getValue not found in MutableStateFlow");
            return;
        }
        // 调用 getValue 来获取实际的 FileDescriptor 对象
        jobject fileDescriptorObject = (*env)->CallObjectMethod(env, mFdObject, getValueMethodID);
        if (fileDescriptorObject == NULL) {
            LOGD("授权成功 fd232 FileDescriptor object is NULL");
            return;
        }
        // 获取 FileDescriptor 的 descriptor 字段
        jclass fileDescriptorClass = (*env)->FindClass(env, "java/io/FileDescriptor");
        jfieldID descriptorID = (*env)->GetFieldID(env, fileDescriptorClass, "descriptor", "I");
        if (descriptorID == NULL) {
            LOGD("授权成功 fd232 descriptorID is NULL");
            return;
        }

        jint descriptor = (*env)->GetIntField(env, fileDescriptorObject, descriptorID);
        LOGD("授权成功 fd232 close(fd = %d)", descriptor);
        close(descriptor);
    } else if (closeFdType == 2) {

        // 获取 SerialVM 类中的 _485fd 字段
        jfieldID mFdID = (*env)->GetFieldID(env, serialVMClass, "fd485",
                "Lkotlinx/coroutines/flow/MutableStateFlow;");
        if (mFdID == NULL) {
            LOGD("授权成功 Field fd485 not found in SerialVM");
            return;
        }

        // 获取 485fd 字段的值
        jobject mFdObject = (*env)->GetObjectField(env, serialVMObject, mFdID);
        if (mFdObject == NULL) {
            LOGD("授权成功 fd485 is NULL");
            return;
        }

        // 获取 MutableStateFlow 的 value 字段，注意 fd485 持有的是 FileDescriptor 类型的对象
        jclass mutableStateFlowClass = (*env)->GetObjectClass(env, mFdObject);
        jmethodID getValueMethodID = (*env)->GetMethodID(env, mutableStateFlowClass, "getValue",
                "()Ljava/lang/Object;");
        if (getValueMethodID == NULL) {
            LOGD("授权成功 fd485 Method getValue not found in MutableStateFlow");
            return;
        }
        // 调用 getValue 来获取实际的 FileDescriptor 对象
        jobject fileDescriptorObject = (*env)->CallObjectMethod(env, mFdObject, getValueMethodID);
        if (fileDescriptorObject == NULL) {
            LOGD("授权成功 fd485 FileDescriptor object is NULL");
            return;
        }
        // 获取 FileDescriptor 的 descriptor 字段
        jclass fileDescriptorClass = (*env)->FindClass(env, "java/io/FileDescriptor");
        jfieldID descriptorID = (*env)->GetFieldID(env, fileDescriptorClass, "descriptor", "I");
        if (descriptorID == NULL) {
            LOGD("授权成功 fd485 descriptorID is NULL");
            return;
        }

        jint descriptor = (*env)->GetIntField(env, fileDescriptorObject, descriptorID);
        LOGD("授权成功 fd485 close(fd = %d)", descriptor);
        close(descriptor);
    } else if (closeFdType == 3) {

        // 获取 SerialVM 类中的 _usbfd 字段
        jfieldID mFdID = (*env)->GetFieldID(env, serialVMClass, "usbfd",
                "Lkotlinx/coroutines/flow/MutableStateFlow;");
        if (mFdID == NULL) {
            LOGD("授权成功 Field usbfd not found in SerialVM");
            return;
        }

        // 获取 usbfd 字段的值
        jobject mFdObject = (*env)->GetObjectField(env, serialVMObject, mFdID);
        if (mFdObject == NULL) {
            LOGD("授权成功 usbfd is NULL");
            return;
        }

        // 获取 MutableStateFlow 的 value 字段，注意 usbfd 持有的是 FileDescriptor 类型的对象
        jclass mutableStateFlowClass = (*env)->GetObjectClass(env, mFdObject);
        jmethodID getValueMethodID = (*env)->GetMethodID(env, mutableStateFlowClass, "getValue",
                "()Ljava/lang/Object;");
        if (getValueMethodID == NULL) {
            LOGD("授权成功 usbfd Method getValue not found in MutableStateFlow");
            return;
        }
        // 调用 getValue 来获取实际的 FileDescriptor 对象
        jobject fileDescriptorObject = (*env)->CallObjectMethod(env, mFdObject, getValueMethodID);
        if (fileDescriptorObject == NULL) {
            LOGD("授权成功 usbfd FileDescriptor object is NULL");
            return;
        }
        // 获取 FileDescriptor 的 descriptor 字段
        jclass fileDescriptorClass = (*env)->FindClass(env, "java/io/FileDescriptor");
        jfieldID descriptorID = (*env)->GetFieldID(env, fileDescriptorClass, "descriptor", "I");
        if (descriptorID == NULL) {
            LOGD("授权成功 usbfd descriptorID is NULL");
            return;
        }

        jint descriptor = (*env)->GetIntField(env, fileDescriptorObject, descriptorID);
        LOGD("授权成功 usbfd close(fd = %d)", descriptor);
        close(descriptor);
    }

}

