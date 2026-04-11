//package com.recycling.toolsapp.utils
//
//import android.content.Context
//import android.os.Bundle
//import android.speech.tts.TextToSpeech
//import com.iflytek.cloud.ErrorCode
//import com.iflytek.cloud.InitListener
//import com.iflytek.cloud.SpeechConstant
//import com.iflytek.cloud.SpeechError
//import com.iflytek.cloud.SpeechSynthesizer
//import com.iflytek.cloud.SynthesizerListener
//import java.util.Locale
//
//object XFTtsManager {
//    private var tts: SpeechSynthesizer? = null
//    private var isInitialized = false
//
//    fun initialize(context: Context) {
//        if (tts == null) {
//            tts = SpeechSynthesizer.createSynthesizer(context) { code ->
//                if (code == ErrorCode.SUCCESS) {
//                    Loge.d("讯飞语音 初始化成功")
//                    // 初始化成功，设置参数
//                    tts?.setParameter(SpeechConstant.VOICE_NAME, "xiaoyan"); // 设置发音人
//                    tts?.setParameter(SpeechConstant.SPEED, "40"); // 设置语速
//                    tts?.setParameter(SpeechConstant.VOLUME, "80"); // 设置音量
//                    setIsInitialized(true)
//                } else {
//                    Loge.d("讯飞语音 初始化失败, 错误码：$code")
//                }
//            }
//        }
//    }
//
//    fun speak(text: String) {
//        tts?.let { tts ->
//            if (isInitialized) {
//                tts.startSpeaking(text, object : SynthesizerListener {
//                    override fun onSpeakBegin() {
//                        Loge.d("讯飞语音 onSpeakBegin ")
//                    }
//
//                    override fun onBufferProgress(p0: Int, p1: Int, p2: Int, p3: String?) {
//                        Loge.d("讯飞语音 onBufferProgress $p0,$p1,$p2,$p3")
//                    }
//
//                    override fun onSpeakPaused() {
//                        Loge.d("讯飞语音 onSpeakPaused ")
//                    }
//
//                    override fun onSpeakResumed() {
//                        Loge.d("讯飞语音 onSpeakResumed ")
//                    }
//
//                    override fun onSpeakProgress(p0: Int, p1: Int, p2: Int) {
//                        Loge.d("讯飞语音 onSpeakProgress $p0,$p1,$p2")
//                    }
//
//                    override fun onCompleted(p0: SpeechError?) {
//                        Loge.d("讯飞语音 onCompleted ${p0?.message}")
//                    }
//
//                    override fun onEvent(p0: Int, p1: Int, p2: Int, p3: Bundle?) {
//                        Loge.d("讯飞语音 onEvent $p0,$p1,$p2,$p3")
//                    }
//
//                })
//            } else {
//                Loge.d("讯飞语音 isInitialized $isInitialized")
//            }
//        }
//    }
//
//
//    fun shutdown() {
//        tts?.destroy()
//        tts = null
//        isInitialized = false
//    }
//
//    private fun setIsInitialized(flag: Boolean) {
//        isInitialized = flag
//    }
//}
