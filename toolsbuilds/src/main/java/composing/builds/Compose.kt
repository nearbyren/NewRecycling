package composing.builds

object Compose {

    //时间：2017.2.13；每次修改版本号都要添加修改时间
    //V1_1_2_161209_beta
    //V主版本号_子版本号_阶段版本号_日期版本号_希腊字母版本号

    /**
     * 程序编译app时候用的sdk版本 建议最新
     */
    const val compileSdk = 34

    /**
     * 程序运行的最低的要求的Sdk   [使用cameraX时记得切换成这个 21]
     */
    const val minSdk = 28

    /**
     * 向前兼容的作用
     */
    const val targetSdk = 34

    const val versionCode = 1

    const val versionName = "1.0"

    const val kotComExtVer = "1.5.2"
    const val coreKtx = "androidx.core:core-ktx:1.9.0"
    const val activityKtx = "androidx.activity:activity-ktx:1.8.0-alpha07"
    const val lifeRunKtx = "androidx.lifecycle:lifecycle-runtime-ktx:2.6.1"
    const val actCompose = "androidx.activity:activity-compose:1.7.0"
    const val ui = "androidx.compose.ui:ui"
    const val uiGrap = "androidx.compose.ui:ui-graphics"
    const val uiToolPre = "androidx.compose.ui:ui-tooling-preview"
    const val material3 = "androidx.compose.material3:material3"
    const val arrowCore = "io.arrow-kt:arrow-core:1.2.0"
    const val uiTestJunt = "androidx.compose.ui:ui-test-junit4:1.1.1"
    const val lifeVmComp = "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1"



    const val junit = "junit:junit:4.13.2"

    const val testJunit = "androidx.test.ext:junit:1.1.5"
    const val testEspCore = "androidx.test.espresso:espresso-core:3.4.0"
    const val composeBom = "androidx.compose:compose-bom:2023.10.01"
    const val uiTestJunit = "androidx.compose.ui:ui-test-junit4"
    const val uiTooling = "androidx.compose.ui:ui-tooling"
    const val uiTestManifest = "androidx.compose.ui:ui-test-manifest"



}