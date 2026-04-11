package com.cabinet.toolsapp.tools.bus

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * @date: 2024/7/18 10:17
 * @desc: 基于Flow封装的FlowBus
 */
object FlowBus {
    private const val TAG = "FlowBus"
    private val busMap = mutableMapOf<String, FlowEventBus<*>>()
    private val busStickMap = mutableMapOf<String, FlowStickEventBus<*>>()

    @Synchronized
    fun <T> with(key: String): FlowEventBus<T> {
        var flowEventBus = busMap[key]
        if (flowEventBus == null) {
            flowEventBus = FlowEventBus<T>(key)
            busMap[key] = flowEventBus
        }
        return flowEventBus as FlowEventBus<T>
    }

    @Synchronized
    fun <T> withStick(key: String): FlowStickEventBus<T> {
        var stickEventBus = busStickMap[key]
        if (stickEventBus == null) {
            stickEventBus = FlowStickEventBus<T>(key)
            busStickMap[key] = stickEventBus
        }
        return stickEventBus as FlowStickEventBus<T>
    }

    open class FlowEventBus<T>(private val key: String) : DefaultLifecycleObserver {
        //私有对象用于发送消息
        private val _events: MutableSharedFlow<T> by lazy {
            obtainEvent()
        }

        //暴露的公有对象用于接收消息
        private val events = _events.asSharedFlow()

        open fun obtainEvent(): MutableSharedFlow<T> =
            MutableSharedFlow(0, 1, BufferOverflow.DROP_OLDEST)

        //在主线程中接收数据
        fun register(lifecycleOwner: LifecycleOwner,action: (t: T) -> Unit){
            //绑定生命周期,解决内存泄露
            lifecycleOwner.lifecycle.addObserver(this)
            lifecycleOwner.lifecycleScope.launch {
                events.collect {
                    try {
                        action(it)
                    }catch (e:Exception){
                        e.printStackTrace()
                        Log.e(TAG, "FlowBus - Error:$e")
                    }
                }
            }
        }

        //在协程中接收数据
        fun register(scope: CoroutineScope,action: (t: T) -> Unit){
            scope.launch {
                events.collect{
                    try {
                       action(it)
                    }catch (e:Exception){
                        e.printStackTrace()
                        Log.e(TAG, "FlowBus - Error:$e")
                    }
                }
            }
        }

        //在协程中发送数据
        suspend fun post(event: T){
            _events.emit(event)
        }

        //在主线程中发送数据
        fun post(scope: CoroutineScope,event: T){
            scope.launch {
                _events.emit(event)
            }
        }

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            Log.w(TAG, "FlowBus ==== 自动onDestroy")
            val subscriptCount = _events.subscriptionCount.value
            if (subscriptCount <= 0)
                busMap.remove(key)
        }

        // 手动调用的销毁方法，用于Service、广播等
        fun destroy() {
            Log.w(TAG, "FlowBus ==== 手动销毁")
            val subscriptionCount = _events.subscriptionCount.value
            if (subscriptionCount <= 0) {
                busMap.remove(key)
            }
        }

    }

    class FlowStickEventBus<T>(key: String) : FlowEventBus<T>(key) {
        override fun obtainEvent(): MutableSharedFlow<T> =
            MutableSharedFlow(1, 1, BufferOverflow.DROP_OLDEST)
    }

    /****
     *
     *  FlowBus.with<MessageEvent>("test").register(this@MainActivity) {
     *             LogUtils.d(TAG,it.toString())
     *             if(it.message == "stop"){
     *                 LogUtils.d(TAG,"===接收到的消息为==="+it.message)
     *             }
     *         }
     * FlowBus.withStick<MessageEvent>("mineFragment").post(this, messageEvent)
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     */

}