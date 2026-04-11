package com.recycling.toolsapp.http

data class UserDto(
    var user_id: String? = null,
    var name: String? = null,
    var imgUrl: String? = null,
    var unit_gds: String? = null,
    var isUpdate: Int = 0,
    )
