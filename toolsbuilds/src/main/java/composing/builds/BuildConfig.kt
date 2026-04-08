package composing.builds

/**
 * @author:
 * @created on: 2022/3/4 13:39
 * @description:
 * 在传统软件开发过程中，软件版本周期可分为三个阶段，分别是：α、β、λ。
 *
 * Alpha(α)：内部测试版。这个是最早的版本，这个版本包含很多 BUG功能也不全，主要是给开发人员和测试人员测试和找 BUG 用的。
 * Beta(β)：公开测试版。这个版本比 Alpha 版发布得晚一些，主要是给社区用户和忠实用户测试用的，该版本任然存在很多BUG，但是相对 Alpha 版要稳定一些，这个阶段版本的软件还会不断增加新功能。
 * Gamma(λ)：现在大部分人都叫 RC 版（Release Candidate - 候选版本），该版本又较 Beta 版更进一步了，该版本功能不再增加，和最终发布版功能一样。这个版本有点像最终发行版之前的一个类似预览版，这个的发布就标明离最终发行版不远了。
 * Stable：稳定版。在开源软件中，都有 Stable 版，这个就是开源软件的最终发行版。
 */
object BuildConfig {

    //时间：2017.2.13；每次修改版本号都要添加修改时间
    //V1_1_2_161209_beta
    //V主版本号_子版本号_阶段版本号_日期版本号_希腊字母版本号

    /**
     * 程序编译app时候用的sdk版本 建议最新
     */
    const val compileSdk = 33

    /**
     * 程序运行的最低的要求的Sdk   [使用cameraX时记得切换成这个 21]
     */
    const val minSdk = 21

    /**
     * 向前兼容的作用
     */
    const val targetSdk = 33

    const val versionCode = 1

    const val versionName = "1.0"
}