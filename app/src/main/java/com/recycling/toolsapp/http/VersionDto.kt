package com.recycling.toolsapp.http

data class VersionDto(
    var hasUpdate: Boolean = false,
    var newVersion: String? = null,
    var description: String? = null,
    var apkUrl: String? = null,
    var force: Boolean = false
)
