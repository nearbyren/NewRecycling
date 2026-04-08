package com.recycling.toolsapps.http

import nearby.lib.netwrok.response.ResponseHolder

interface Repo {
    suspend fun issueDevice(headers: MutableMap<String, String>, params: MutableMap<String, Any>): ResponseHolder<String>
    suspend fun connectAddress(headers: MutableMap<String, String>, params: MutableMap<String, Any>): ResponseHolder<String>
    suspend fun uploadPhoto(params: MutableMap<String, Any>): ResponseHolder<String>
    suspend fun uploadLog(params: MutableMap<String, Any>): ResponseHolder<String>
}
