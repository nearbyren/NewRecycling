package nearby.lib.netwrok.response

import android.content.Context
import android.content.SharedPreferences
import java.lang.reflect.Method

/**
 * @author:
 * @created on: 2022/8/8 19:56
 * @description:
 */
object SPreUtil {
    /***
     *是否出厂了
     * init
     */
    const val init = "init"
    const val host = "host"
    const val port = "port"
    const val isQrCode = "isQrCode"
    const val lightOn = "lightOn"
    const val lightOff = "lightOff"
    const val initSocket = "initSocket"
    const val type_grid = "type_grid"
    const val init_sn = "init_sn"
    const val gversion = "gversion"
    const val setImsi = "setImsi"
    const val setImei = "setImei"
    const val setIccid = "setIccid"
    const val netStatusText1 = "netStatusText1"
    const val netStatusText2 = "netStatusText2"
    const val loginCount = "loginCount"
    const val transId = "transId"
    const val setSignal = "setSignal"
    const val login_sn = "login_sn"
    const val mobileDoorGeX = "mobileDoorGeX"
    const val rodHinderValue1 = "rodHinderValue1"
    const val rodHinderValue2 = "rodHinderValue2"
    const val setMobileDoor = "setMobileDoor"
    const val userId = "userId"
    const val debugPasswd = "debugPasswd"
    const val irOverflow = "irOverflow"
    const val overflowState = "overflowState"
    const val overflowState1 = "overflowState1"
    const val overflowState2 = "overflowState2"
    const val overflow = "overflow"
    const val crash = "crash"
    const val setting_ip = "setting_ip"
    const val clearFaultDoor = "clearFaultDoor"
    const val saveIr1 = "saveIr1"
    const val saveIr2 = "saveIr2"
    /**
     * 保存在手机里面的文件名
     */
    val FILE_NAME = "share_data"

    /**
     * 保存数据的方法，我们需要拿到保存数据的具体类型，然后根据类型调用不同的保存方法
     *
     * @param context
     * @param key
     * @param value
     */
    fun put(context: Context?, key: String?, value: Any) {
        if (context == null) return
        val sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val editor = sp.edit()
        if (value is String) {
            editor.putString(key, value)
        } else if (value is Int) {
            editor.putInt(key, value)
        } else if (value is Boolean) {
            editor.putBoolean(key, value)
        } else if (value is Float) {
            editor.putFloat(key, value)
        } else if (value is Long) {
            editor.putLong(key, value)
        } else {
            editor.putString(key, value.toString())
        }
        SharedPreferencesCompat.apply(editor)
    }

    /**
     * 得到保存数据的方法，我们根据默认值得到保存的数据的具体类型，然后调用相对于的方法获取值
     *
     * @param context
     * @param key
     * @param defaultObject
     * @return
     */
    operator fun get(context: Context?, key: String?, defaultObject: Any?): Any? {
        if (context == null) return null
        val sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        if (defaultObject is String) {
            return sp.getString(key, defaultObject as String?)
        } else if (defaultObject is Int) {
            return sp.getInt(key, (defaultObject as Int?)!!)
        } else if (defaultObject is Boolean) {
            return sp.getBoolean(key, (defaultObject as Boolean?)!!)
        } else if (defaultObject is Float) {
            return sp.getFloat(key, (defaultObject as Float?)!!)
        } else if (defaultObject is Long) {
            return sp.getLong(key, (defaultObject as Long?)!!)
        }
        return null
    }

    /**
     * 移除某个key值已经对应的值
     *
     * @param context
     * @param key
     */
    fun remove(context: Context, key: String?) {
        val sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val editor = sp.edit()
        editor.remove(key)
        SharedPreferencesCompat.apply(editor)
    }

    /**
     * 清除所有数据
     *
     * @param context
     */
    fun clear(context: Context) {
        val sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val editor = sp.edit()
        editor.clear()
        SharedPreferencesCompat.apply(editor)
    }

    /**
     * 查询某个key是否已经存在
     *
     * @param context
     * @param key
     * @return
     */
    fun contains(context: Context, key: String?): Boolean {
        val sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        return sp.contains(key)
    }

    /**
     * 返回所有的键值对
     *
     * @param context
     * @return
     */
    fun getAll(context: Context): Map<String?, *>? {
        val sp = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        return sp.all
    }


    /**
     * 创建一个解决SharedPreferencesCompat.apply方法的一个兼容类
     *
     * @author zhy
     */
    private object SharedPreferencesCompat {
        private val sApplyMethod = findApplyMethod()

        /**
         * 反射查找apply的方法
         *
         * @return
         */
        private fun findApplyMethod(): Method? {
            try {
                val clz: Class<*> = SharedPreferences.Editor::class.java
                return clz.getMethod("apply")
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
            }
            return null
        }

        /**
         * 如果找到则使用apply执行，否则使用commit
         *
         * @param editor
         */
        fun apply(editor: SharedPreferences.Editor) {
            try {
                if (sApplyMethod != null) {
                    sApplyMethod.invoke(editor)
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            editor.commit()
        }
    }
}