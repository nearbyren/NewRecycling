package com.recycling.toolsapp.http

import com.google.gson.reflect.TypeToken
import nearby.lib.netwrok.response.CorHttp
import nearby.lib.netwrok.response.InfoResponse
import nearby.lib.netwrok.response.ResponseHolder

class RepoImpl : Repo {
    override suspend fun issueDevice(headers: MutableMap<String, String>, params: MutableMap<String, Any>): ResponseHolder<String> {
        return CorHttp.getInstance().post(url = HttpUrl.issueDevice, headers = headers, params = params, type = object : TypeToken<InfoResponse<String>>() {}.type, kClazz = String::class)
    }

    override suspend fun connectAddress(headers: MutableMap<String, String>, params: MutableMap<String, Any>): ResponseHolder<String> {
        return CorHttp.getInstance().post(url = HttpUrl.connectAddress, headers = headers, params = params, type = object : TypeToken<InfoResponse<String>>() {}.type, kClazz = String::class)
    }

    override suspend fun uploadPhoto(params: MutableMap<String, Any>): ResponseHolder<String> {
        return CorHttp.getInstance().postMultipart(url = HttpUrl.uploadPhoto, params = params, type = object : TypeToken<InfoResponse<String>>() {}.type, kClazz = String::class)
    }

    override suspend fun uploadLog(params: MutableMap<String, Any>): ResponseHolder<String> {
        return CorHttp.getInstance().postMultipart(url = HttpUrl.uploadLog, params = params, type = object : TypeToken<InfoResponse<String>>() {}.type, kClazz = String::class)
    }
}