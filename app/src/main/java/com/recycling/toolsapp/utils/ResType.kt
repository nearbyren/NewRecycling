package com.recycling.toolsapp.utils

/***
 * 资源状态标识
 */
object ResType {
    /**
     * -1.已插入数据
     */
    const val TYPE_F1 = -1
    /**
     * 0.不需要刷新
     */
    const val TYPE_0 = 0

    /**
     * 1.需要刷新
     */
    const val TYPE_1 = 1

    /**
     * 2.还未升级
     */
    const val TYPE_2 = 2

    /**
     * 3.升级
     */
    const val TYPE_3 = 3

    /**
     * 4.下载失败
     */
    const val TYPE_4 = 4
}