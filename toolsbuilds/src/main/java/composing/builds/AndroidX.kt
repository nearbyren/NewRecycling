package composing.builds

/**
 * @author:
 * @created on: 2022/3/4 13:36
 * @description:
 */
object AndroidX {
    const val exifinterface = "androidx.exifinterface:exifinterface:1.3.2"
    const val databindingRuntime = "androidx.databinding:databinding-runtime:4.2.2"
    const val percentlayout = "androidx.percentlayout:percentlayout:1.0.0"
    const val annotation = "androidx.annotation:annotation:1.0.0"
    const val appcompat = "androidx.appcompat:appcompat:1.3.1"
    const val core = "androidx.core:core:1.0.0"
    const val recyclerView = "androidx.recyclerview:recyclerview:1.2.1"
    const val coreKtx = "androidx.core:core-ktx:1.6.0"
    const val supportV13 = "androidx.legacy:legacy-support-v13:1.0.0"
    const val activityKtx = "androidx.activity:activity-ktx:1.4.0"
    const val cardview = "androidx.cardview:cardview:1.0.0"
    const val multidex = "androidx.multidex:multidex:2.0.1"
    const val legacy = "androidx.legacy:legacy-support-v4:1.0.0"
    const val paging = "androidx.paging:paging-runtime-ktx:3.0.1"
    const val viewpager = "androidx.viewpager:viewpager:1.0.0"
    const val splashscreen = "androidx.core:core-splashscreen:1.0.1"


    private const val fragVersion = "1.3.6"
    const val fragment = "androidx.fragment:fragment:$fragVersion"
    const val fragmentKtx = "androidx.fragment:fragment-ktx:$fragVersion"
    const val fragmentTesting = "androidx.fragment:fragment-testing:${fragVersion}"


    private const val lifecycVersion = "2.2.0"
    const val lifeExt = "androidx.lifecycle:lifecycle-extensions:$lifecycVersion"
    const val lifeViewModelKtx = "androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycVersion"
    const val lifeRunKtx = "androidx.lifecycle:lifecycle-runtime-ktx:$lifecycVersion"
    const val lifeLiveDataKtx = "androidx.lifecycle:lifecycle-livedata-ktx:$lifecycVersion"
    const val lifeCommjava8 = "androidx.lifecycle:lifecycle-common-java8:$lifecycVersion"
    const val lifeVMSavedState = "androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycVersion"
    const val lifeService = "androidx.lifecycle:lifecycle-service:$lifecycVersion"


    private const val consVersion = "2.1.4"
    const val consLayout = "androidx.constraintlayout:constraintlayout:$consVersion"
    const val consLaySolver = "androidx.constraintlayout:constraintlayout-solver:$consVersion"


    private const val navVersion = "2.4.2"
    const val navfraKtx = "androidx.navigation:navigation-fragment-ktx:$navVersion"
    const val navUiKtx = "androidx.navigation:navigation-ui-ktx:$navVersion"

    //GlobalScope（不推荐）
    const val kotCorCor = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0"
    const val kotCorAnd = "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.0"
    const val kotlinStdlib = "org.jetbrains.kotlin:kotlin-stdlib:1.6.10"


    private const val roomVersion = "2.3.0"
    const val roomRuntime = "androidx.room:room-runtime:$roomVersion"
    const val roomCompiler = "androidx.room:room-compiler:$roomVersion"
    const val roomKtx = "androidx.room:room-ktx:$roomVersion"


    private const val cameraVersion = "1.0.0"
    const val cameraCore = "androidx.camera:camera-core:$cameraVersion"
    const val cameraCamera2 = "androidx.camera:camera-camera2:$cameraVersion"
    const val cameraLifecycle = "androidx.camera:camera-lifecycle:$cameraVersion"
    const val cameraView = "androidx.camera:camera-view:$cameraVersion"
    const val cameraExtensions = "androidx.camera:camera-extensions:$cameraVersion"
}