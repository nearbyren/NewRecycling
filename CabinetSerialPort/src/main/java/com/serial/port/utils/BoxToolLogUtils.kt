package com.serial.port.utils

import android.annotation.SuppressLint
import android.os.Environment
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat

/***
 * 记录下位机箱子信息
 * Record lower machine box information
 */
object BoxToolLogUtils {
    @SuppressLint("SimpleDateFormat")
    private val formatdate = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
    /***
     * 记录socket日志
     */
    fun  recordSocket(type: String, json: String) {
        try {
            val builder = StringBuilder()
            val time = AppUtils.getDateYMDHMS()
            builder.append(time).append("\n").append(json).append('\n')
            val fileName = "socket-${type}---${AppUtils.getDateYMD()}.txt"
            val path =
                    AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + "/socket_box_crash/"
            val dirs = File(path)
            if (!dirs.exists()) {
                dirs.mkdirs()
            }
            val file = File(path, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            // 追加写入模式
            val fos = FileOutputStream(file, true)
            val bos = BufferedOutputStream(fos)
            bos.write(builder.toString().toByteArray())
            bos.flush()
            bos.close()
        } catch (e: SecurityException) {
            Loge.d("BoxToolLogUtils recordLowerBox an error occured while writing file...$e")
        }
    }


    /***
     * 流程日志
     */

    fun  recordTask( text: String){
        try {
            val builder = StringBuilder()
            val time = AppUtils.getDateYMDHMS()
            builder.append(time).append("\n").append(text).append('\n')
            val fileName = "a-task---${AppUtils.getDateYMD()}.txt"
            val path =
                AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + "/socket_box_crash/"
            val dirs = File(path)
            if (!dirs.exists()) {
                dirs.mkdirs()
            }
            val file = File(path, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            // 追加写入模式
            val fos = FileOutputStream(file, true)
            val bos = BufferedOutputStream(fos)
            bos.write(builder.toString().toByteArray())
            bos.flush()
            bos.close()
        } catch (e: SecurityException) {
            Loge.d("BoxToolLogUtils recordLowerBox an error occured while writing file...$e")
        }
    }

    fun savePrintln( text: String){
        try {
            val builder = StringBuilder()
            val time = AppUtils.getDateYMDHMS()
            builder.append(time).append("\n").append(text).append('\n')
            val fileName = "a-task---${AppUtils.getDateYMD()}.txt"
            val path =
                AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + "/socket_box_crash/"
            val dirs = File(path)
            if (!dirs.exists()) {
                dirs.mkdirs()
            }
            val file = File(path, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            // 追加写入模式
            val fos = FileOutputStream(file, true)
            val bos = BufferedOutputStream(fos)
            bos.write(builder.toString().toByteArray())
            bos.flush()
            bos.close()
        } catch (e: SecurityException) {
            Loge.d("BoxToolLogUtils recordLowerBox an error occured while writing file...$e")
        }
    }
    fun saveCamera( text: String){
        try {
            val builder = StringBuilder()
            val time = AppUtils.getDateYMDHMS()
            builder.append(time).append("\n").append(text).append('\n')
            val fileName = "a-camera---${AppUtils.getDateYMD()}.txt"
            val path =
                AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + "/socket_box_crash/"
            val dirs = File(path)
            if (!dirs.exists()) {
                dirs.mkdirs()
            }
            val file = File(path, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            // 追加写入模式
            val fos = FileOutputStream(file, true)
            val bos = BufferedOutputStream(fos)
            bos.write(builder.toString().toByteArray())
            bos.flush()
            bos.close()
        } catch (e: SecurityException) {
            Loge.d("BoxToolLogUtils recordLowerBox an error occured while writing file...$e")
        }
    }
    /***
     * 所有日志信息
     */
    fun listBoxInfoFiles(): List<String>? {
        return try {
            val dir =
                    File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "socket_box_crash")
            if (dir.exists() && dir.isDirectory) {
                dir.list()?.filter { File(dir, it).isFile }
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.e("FileUtils", "Permission denied", e)
            null
        }
    }

    /***
     * @param typePort 232 或者 485
     * 接收下位机数据
     */
    fun receiveOriginalLower(typePort: Int, packet: ByteArray) {
        val builder = StringBuilder()
        val time = AppUtils.getDateYMDHMS()
        builder.append(time).append(" | ").append(typePort).append(" | ").append(ByteUtils.toHexString(packet)).append('\n').append("----------------------------------------------------------------------------------------------------------------").append('\n')

        try {
            val fileName = "lower-receive-${typePort}---${AppUtils.getDateYMD()}.txt"
            val path =
                    AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + "/socket_box_crash/"
            val dirs = File(path)
            if (!dirs.exists()) {
                dirs.mkdirs()
            }
            val file = File(path, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            // 追加写入模式
            val fos = FileOutputStream(file, true)
            val bos = BufferedOutputStream(fos)
            bos.write(builder.toString().toByteArray())
            bos.flush()
            bos.close()
        } catch (e: SecurityException) {
            Loge.d("BoxToolLogUtils originalLower an error occured while writing file...$e")
        }
    }

    /***
     * @param typePort 232 或者 485
     * 发送给下位机数据
     */
    fun sendOriginalLower(typePort: Int, packet: String) {
        val builder = StringBuilder()
        val time = AppUtils.getDateYMDHMS()
        builder.append(time).append(" | ").append(typePort).append(" | ").append(packet).append('\n').append("----------------------------------------------------------------------------------------------------------------").append('\n')

        try {
            val fileName = "lower-send-${typePort}---${AppUtils.getDateYMD()}.txt"
            val path =
                    AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + "/socket_box_crash/"
            val dirs = File(path)
            if (!dirs.exists()) {
                dirs.mkdirs()
            }
            val file = File(path, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            // 追加写入模式
            val fos = FileOutputStream(file, true)
            val bos = BufferedOutputStream(fos)
            bos.write(builder.toString().toByteArray())
            bos.flush()
            bos.close()
        } catch (e: SecurityException) {
            Loge.d("BoxToolLogUtils originalLower an error occured while writing file...$e")
        }
    }

    /***
     * @param typePort 232 或者 485
     * 发送给下位机数据 定时发送的
     */
    fun sendOriginalLowerStatus(typePort: Int, packet: String) {
        val builder = StringBuilder()
        val time = AppUtils.getDateYMDHMS()
        builder.append(time).append(" | ").append(typePort).append(" | ").append(packet).append('\n').append("----------------------------------------------------------------------------------------------------------------").append('\n')

        try {
            val fileName = "lower-send-status-${typePort}---${AppUtils.getDateYMD()}.txt"
            val path =
                    AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath + "/socket_box_crash/"
            val dirs = File(path)
            if (!dirs.exists()) {
                dirs.mkdirs()
            }
            val file = File(path, fileName)
            if (!file.exists()) {
                file.createNewFile()
            }
            // 追加写入模式
            val fos = FileOutputStream(file, true)
            val bos = BufferedOutputStream(fos)
            bos.write(builder.toString().toByteArray())
            bos.flush()
            bos.close()
        } catch (e: SecurityException) {
            Loge.d("BoxToolLogUtils originalLower an error occured while writing file...$e")
        }
    }
}