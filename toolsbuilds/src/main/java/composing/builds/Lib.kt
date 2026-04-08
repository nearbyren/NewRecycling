package composing.builds

/**
 * @author:
 * @created on: 2022/3/4 13:46
 * @description:
 */
object Lib {
    //toast
    const val Toasty = "com.github.GrenderG:Toasty:1.2.8"
    const val light = "io.github.tonnyl:light:1.0.0"
    const val stateframelayout = "am.widget:stateframelayout:2.0.2"


    //功能引导指示图
    const val MaterialIntroView = "com.github.iammert:MaterialIntroView:1.6.0"

    //引导图
    const val slidingtutorial = "com.cleveroad:slidingtutorial:0.9"


    private const val logger_version = "2.2.0"
    const val logger = "com.orhanobut:logger:${logger_version}"


    private const val timber_version = "4.7.1"
    const val timber = "com.jakewharton.timber:timber:${timber_version}"


    private const val rxjava_version = "2.1.3"
    const val rxjava = "io.reactivex.rxjava2:rxjava:${rxjava_version}"
    const val rxandroid = "io.reactivex.rxjava2:rxandroid:2.0.1"
    const val rxbinding2 = "com.jakewharton.rxbinding2:rxbinding:2.1.1"


    private const val okhttp3_version = "4.7.2"
    const val okhttp3 = "com.squareup.okhttp3:okhttp:${okhttp3_version}"
    const val loggingInterceptor = "com.squareup.okhttp3:logging-interceptor:3.8.1"
    const val retrofit = "com.squareup.retrofit2:retrofit:2.9.0"
    const val retrofitConverterGson = "com.squareup.retrofit2:converter-gson:2.9.0"
    const val adapterRxjava = "com.squareup.retrofit2:adapter-rxjava:2.9.0"
    const val adapterRxjava2 = "com.squareup.retrofit2:adapter-rxjava2:2.3.0"
    const val converterCalars = "com.squareup.retrofit2:converter-scalars:2.9.0"
    const val conscryptAndroid = "org.conscrypt:conscrypt-android:2.2.1"


    //拦截请求调试

    private const val stetho_okhttp3_version = "1.5.0"
    const val stethoOkhttp3 = "com.facebook.stetho:stetho-okhttp3:${stetho_okhttp3_version}"


    //拦截请求调试

    private const val stetho_version = "1.5.0"
    const val stetho = "com.facebook.stetho:stetho:${stetho_version}"

    const val nativecrashreport = "com.tencent.bugly:nativecrashreport:3.9.0"

    private const val crashwoodpecker_version = "2.1.1"
    const val crashwoodpecker = "me.drakeet.library:crashwoodpecker:${crashwoodpecker_version}"


    const val smarttablayout = "com.ogaclejapan.smarttablayout:library:2.0.0@aar"
    const val smarttablayoutUtils = "com.ogaclejapan.smarttablayout:utils-v4:2.0.0@aar"


    //美团打包
    const val mtwalle = "com.meituan.android.walle:library:1.1.6"


    private const val permDispVersion = "2.1.3"
    const val permissionsdispatcher =
        "com.github.hotchemi:permissionsdispatcher:${permDispVersion}"
    const val permissionsdispatcherProcessor =
        "com.github.hotchemi:permissionsdispatcher-processor:${permDispVersion}"


    const val arouterApi = "com.alibaba:arouter-api:1.5.2"
    const val arouterCompiler = "com.alibaba:arouter-compiler:1.5.2"


    private const val tinkerVersion = "1.9.14.18"
    const val tinkerLib = "com.tencent.tinker:tinker-android-lib:$tinkerVersion"
    const val tinkerLoader = "com.tencent.tinker:tinker-android-loader:$tinkerVersion"
    const val tinkerAno = "com.tencent.tinker:tinker-android-anno:$tinkerVersion"
    const val tinkerAnoSupport = "com.tencent.tinker:tinker-android-anno-support:$tinkerVersion"


    private const val leakVersion = "1.6.2"
    const val leakcanaryAnd = "com.squareup.leakcanary:leakcanary-android:$leakVersion"
    const val leakcanaryAndNoOp = "com.squareup.leakcanary:leakcanary-android-no-op:$leakVersion"
    const val leakcanarySuppFrag = "com.squareup.leakcanary:leakcanary-support-fragment:$leakVersion"


    const val glide = "com.github.bumptech.glide:glide:4.12.0"

    //注解处理器
    const val glideCompiler = "com.github.bumptech.glide:compiler:4.12.0"
    const val glideIntegration = "com.github.bumptech.glide:okhttp3-integration:4.12.0"


    const val lottie = "com.airbnb.android:lottie:5.0.3"

    const val easypermissions = "pub.devrel:easypermissions:3.0.0"

    const val helper = "com.tencent.vasdolly:helper:3.0.6"

    const val eventbus = "org.greenrobot:eventbus:3.3.1"
    const val autosize = "me.jessyan:autosize:1.2.1"

    const val mmkvStatic = "com.tencent:mmkv:1.3.0"

    const val relinker = "com.getkeepsafe.relinker:relinker:1.4.3"


    const val fastjson = "com.alibaba:fastjson:1.2.44"

    const val immersionbar = "com.gyf.immersionbar:immersionbar:3.0.0"

    const val alignTextView = "me.codeboy.android:align-text-view:2.3.2"

    const val labelsView = "com.github.donkingliang:LabelsView:1.6.0"

    const val utilcodex = "com.blankj:utilcodex:1.29.0"

    const val refreshLayoutKernel = "io.github.scwang90:refresh-layout-kernel:2.1.0"
    const val header_classics = "io.github.scwang90:refresh-header-classics:2.1.0"
    const val footer_classics = "io.github.scwang90:refresh-footer-classics:2.1.0"
    const val footer_ball = "io.github.scwang90:refresh-footer-ball:2.1.0"        //球脉冲加载

    const val magicIndicator = "com.github.hackware1993:MagicIndicator:1.5.0"

    const val permissionx = "com.guolindev.permissionx:permissionx:1.6.4"

    const val sketch = "me.panpf:sketch:2.6.1"
    const val sketchGif = "me.panpf:sketch-gif:2.6.1"

    const val recyclerAdapter = "io.github.cymchad:BaseRecyclerViewAdapterHelper4:4.1.4"

    const val zhihuMatisse = "com.zhihu.android:matisse:0.5.3-beta3"

    const val wechatSdkAndroid = "com.tencent.mm.opensdk:wechat-sdk-android:6.8.0"
    const val crashreport = "com.tencent.bugly:crashreport:4.1.9.2"


    const val guideview = "com.binioter:guideview:1.0.0"

    const val alipaysdkAndroid = "com.alipay.sdk:alipaysdk-android:15.8.11"

    const val BaiduMapSDKMap = "com.baidu.lbsyun:BaiduMapSDK_Map:7.5.5"
    const val BaiduMapSDKSearch = "com.baidu.lbsyun:BaiduMapSDK_Search:7.5.5"
    const val BaiduMapSDKUtil = "com.baidu.lbsyun:BaiduMapSDK_Util:7.5.5"
    const val BaiduMapSDKLocationAll = "com.baidu.lbsyun:BaiduMapSDK_Location_All:9.4.0"

    const val youth = "io.github.youth5201314:banner:2.2.2"
}