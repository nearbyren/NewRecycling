package com.serial.port

import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


open class SerialPort(path: String, baudrate: Int) {

    /*
     * 不要修改这个变量名 mFd，也不要修改它的类型。
     * 它必须与 C++ 代码中的 jfieldID mFdID 匹配。
     */
    private var mFd: FileDescriptor? = null

    // 暴露给外部的流
    val inputStream: InputStream
    val outputStream: OutputStream

    init {
        // 调用 native 方法打开串口并返回文件描述符
        mFd = open(path, baudrate, 0)

        if (mFd == null) {
            throw Exception("原生层返回的 FileDescriptor 为空，串口打开失败")
        }

        // 基于 fd 创建 Java 层的流
        inputStream = FileInputStream(mFd)
        outputStream = FileOutputStream(mFd)
    }

    // 对应 C++: Java_com_serial_port_SerialPort_open
    private external fun open(path: String, baudrate: Int, flags: Int): FileDescriptor

    // 对应 C++: Java_com_serial_port_SerialPort_close
    external fun close()

    companion object {
        init {
            // 加载你的 so 库，名字要和你的 CMakeLists.txt 里定义的一致
            System.loadLibrary("SerialPort")
        }
    }
}
