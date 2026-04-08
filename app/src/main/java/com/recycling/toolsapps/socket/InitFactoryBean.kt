package com.recycling.toolsapps.socket

data class InitFactoryBean(

    /***
     * 红外
     */
    var irDefaultState: Int,
    /***
     * 传感器模式
     */
    var weightSensorMode: Int,
    /***
     * 箱体状态
     */
    var list: List<FactoryBean>? = null,
){
    constructor() : this(0,0,null)
}
