package com.recycling.toolsapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextUtils
import android.text.TextWatcher
import android.view.MotionEvent
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.widget.ContentLoadingProgressBar
import com.google.gson.Gson
import com.recycling.toolsapp.fitsystembar.showIme
import com.recycling.toolsapp.http.RepoImpl
import com.recycling.toolsapp.model.LogEntity
import com.recycling.toolsapp.nav.NavTouDoubleActivity
import com.recycling.toolsapp.nav.NavTouSingleActivity
import com.recycling.toolsapp.socket.FactoryBean
import com.recycling.toolsapp.socket.InitFactoryBean
import com.recycling.toolsapp.utils.EntityType
import com.recycling.toolsapp.utils.IccidOper
import com.recycling.toolsapp.vm.CabinetVM
import com.serial.port.utils.AppUtils
import com.serial.port.utils.CmdCode
import com.serial.port.utils.Loge
import kotlinx.coroutines.launch
import nearby.lib.netwrok.response.SPreUtil
import java.util.regex.Pattern
import kotlin.random.Random

/***
 * 出厂配置
 * 这里考虑用fragment去构建
 */
class InitFactoryActivity : AppCompatActivity() {
    private val cabinetVM: CabinetVM by viewModels()

    private var acetSn1: AppCompatEditText? = null
    private var acetSn2: AppCompatTextView? = null
    private var acetSn3: AppCompatEditText? = null
    private var acetSn4: AppCompatEditText? = null

    private var acivNiHao: AppCompatImageView? = null
    private var acetDealerId: AppCompatEditText? = null
    private var actvSnConfirm: AppCompatTextView? = null
    private var actvSnConfirm2: AppCompatTextView? = null
    private var actvInit: AppCompatTextView? = null
    private var actvInitConfirm: AppCompatTextView? = null
    private var actvInitConfirm2: AppCompatTextView? = null
    private var llConfirm: ConstraintLayout? = null
    private var llConfirm2: ConstraintLayout? = null
    private var rgLattice: RadioGroup? = null
    private var cpbLoading: ContentLoadingProgressBar? = null
    private var mRecycleType: Int = -1
    private var downTime = 0L

    //标记是否确认过二维码内容
    private var isConfirm = false
    private var isConfirm2 = false
    private val httpRepo by lazy { RepoImpl() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_init_fractory)
        initFactory()
    }

    private fun toGoHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage("com.android.launcher3")
            addCategory(Intent.CATEGORY_HOME)
        }
        startActivity(intent)
    }

    private fun initFactory() {
        acetDealerId = findViewById(R.id.acet_dealer_id)
        acetDealerId?.setAlphanumericLimitNumber()
        acivNiHao = findViewById(R.id.aciv_ni_hao)
        acivNiHao?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (System.currentTimeMillis() - downTime >= 3000) {
                        // 执行5秒长按回调
                        toGoHome()
                        true // 消耗事件
                    } else false
                }

                else -> false
            }
        }
        initCheckedChange()

        acetSn1 = findViewById(R.id.acet_sn1)
        acetSn2 = findViewById(R.id.acet_sn2)
        acetSn3 = findViewById(R.id.acet_sn3)
        acetSn4 = findViewById(R.id.acet_sn4)

        acetSn1?.setAlphanumericLimitTopFour()
        acetSn4?.setAlphanumericLimitLastFive()

        actvSnConfirm = findViewById(R.id.actv_sn_confirm)
        actvSnConfirm2 = findViewById(R.id.actv_sn_confirm2)
        cpbLoading = findViewById(R.id.cpb_loading)

        initConfirm()
    }

    /***
     * 初始化确认提交数据
     */
    private fun initConfirm() {
        actvInit = findViewById(R.id.actv_init)
        actvInitConfirm = findViewById(R.id.actv_init_confirm)
        actvInitConfirm2 = findViewById(R.id.actv_init_confirm2)
        llConfirm = findViewById(R.id.ll_confirm)
        llConfirm2 = findViewById(R.id.ll_confirm2)
        actvInit?.setOnClickListener {
            acetSn4?.let { it1 -> window.showIme(it1, false) }
            clickSubmit()
        }
        actvInitConfirm?.setOnClickListener {
            llConfirm?.isVisible = false
            llConfirm2?.isVisible = true
            isConfirm = true
        }
        actvInitConfirm2?.setOnClickListener {
            llConfirm2?.isVisible = false
            isConfirm2 = true
        }
    }

    /***
     * 初始化格口控件
     */
    private fun initCheckedChange() {
        rgLattice = findViewById(R.id.rg_lattice)
        val selectedId = rgLattice?.checkedRadioButtonId
        selectedId?.let { sid ->
            selectedText(sid)
        }
        rgLattice?.setOnCheckedChangeListener { _, checkedId ->
            selectedText(checkedId)
        }
    }

    /***
     * 格口选项
     */
    private fun selectedText(checkedId: Int) {
        val selected = when (checkedId) {
            R.id.mrb_lattice1 -> "单格口"
            R.id.mrb_lattice2 -> "双格口"
            R.id.mrb_lattice3 -> "子母格口"
            else -> null
        }
        isConfirm = false
        isConfirm2 = false
        when (selected) {
            "单格口" -> {
                mRecycleType = 1
                SPreUtil.put(AppUtils.getContext(), SPreUtil.type_grid, 1)
                acetSn2?.text = "001"
            }

            "双格口" -> {
                mRecycleType = 2
                SPreUtil.put(AppUtils.getContext(), SPreUtil.type_grid, 2)
                acetSn2?.text = "002"
            }

            "子母格口" -> {
                mRecycleType = 3
                SPreUtil.put(AppUtils.getContext(), SPreUtil.type_grid, 3)
                acetSn2?.text = "003"
            }
        }
    }

    fun getNew5(input: String): String {
        // 获取原始字符串的长度
        val length = input.length

        // 检查字符串长度是否足够长以包含末尾三位数
        if (length < 3) {
            return input // 如果不足三位，直接返回原字符串
        }

        // 生成三位随机数
        val randomNumber = Random.nextInt(10000, 100000) // 生成一个100到999之间的随机数（包括100和999）

        // 构建新的字符串，替换末尾三位数
        val newString = input.substring(0, length - 5) + randomNumber.toString().padStart(5, '0')
        return newString
    }

    fun getNewSn(input: String): String {
        // 获取原始字符串的长度
        val length = input.length

        // 检查字符串长度是否足够长以包含末尾三位数
        if (length < 3) {
            return input // 如果不足三位，直接返回原字符串
        }

        // 生成三位随机数
        val randomNumber = Random.nextInt(100, 1000) // 生成一个100到999之间的随机数（包括100和999）

        // 构建新的字符串，替换末尾三位数
        val newString = input.substring(0, length - 3) + randomNumber.toString().padStart(3, '0')
        return newString
    }

    @SuppressLint("SetTextI18n")
    private fun getSocketUrl(sb: StringBuilder) {
        cabinetVM.ioScope.launch {
            val from = mutableMapOf<String, Any>()
            from["sn"] = sb.toString()
            val headers = mutableMapOf<String, String>()
            headers["token"] = BuildConfig.initToken
            Loge.e("获取socket连接 from ${Gson().toJson(from)} | headers $headers")
            httpRepo.connectAddress(headers, from).onSuccess { initString ->
                hideLoading(false, "初始化智能回收柜成功")
                initString?.let { url ->
                    val socketUrl = initString.split(":")
                    toGoSubmit(sb, socketUrl[0], socketUrl[1].toInt())
                    cabinetVM.insertInfoLog(LogEntity().apply {
                        cmd = "connectAddress"
                        msg = "获取socket地址成功"
                        time = AppUtils.getDateYMDHMS()
                    })
                }
            }.onFailure { code, message ->
                hideLoading(false, "获取socket连接 onFailure ${code} ${message}")
                cabinetVM.insertInfoLog(LogEntity().apply {
                    cmd = "connectAddress"
                    msg = "$code,$message"
                    time = AppUtils.getDateYMDHMS()
                })
            }.onCatch { e ->
                hideLoading(false, "获取socket连接 onCatch ${e.errorMsg}")
                cabinetVM.insertInfoLog(LogEntity().apply {
                    cmd = "connectAddress"
                    msg = "${e.errorCode},${e.errorMsg}"
                    time = AppUtils.getDateYMDHMS()
                })
            }
        }
    }

    /***
     * 设备出厂初始化
     */
    @SuppressLint("SetTextI18n")
    private fun clickSubmit() {
        val sn1 = acetSn1?.text.toString()
        val sn4 = acetSn4?.text.toString()

        if (TextUtils.isEmpty(sn1)) {
            Toast.makeText(AppUtils.getContext(), "请输入出厂配置sn码前四位", Toast.LENGTH_LONG)
                .show()
            return
        }
        if (TextUtils.isEmpty(sn4)) {
            Toast.makeText(AppUtils.getContext(), "请输入出厂配置sn码后四位", Toast.LENGTH_LONG)
                .show()
            return
        }

        val sb = StringBuilder()
        sb.append(acetSn1?.text.toString())
        sb.append(acetSn2?.text.toString())
        sb.append(acetSn3?.text.toString())
        sb.append(acetSn4?.text.toString())
        actvSnConfirm?.text = "${sb.toString()}"
        actvSnConfirm2?.text = "${sb.toString()}"
        if (mRecycleType == -1) {
            Toast.makeText(AppUtils.getContext(), "请输入出厂回收箱格口类型", Toast.LENGTH_LONG)
                .show()
            return
        }
        if (!isConfirm) {
            llConfirm?.isVisible = true
        } else if (isConfirm2) {
            cpbLoading?.isVisible = true
            getSocketUrl(sb)
        }
    }

    fun toGoSubmit(
        sb: StringBuilder,
        mHost: String? = BuildConfig.socketIP,
        mPort: Int? = BuildConfig.socketPort
    ) {
        cabinetVM.ioScope.launch {
            val from = mutableMapOf<String, Any>()
            val ifb = InitFactoryBean().apply {
                irDefaultState = 1
                weightSensorMode = 1
                list = mutableListOf<FactoryBean>().apply {
                    if (mRecycleType == 1 || mRecycleType == 3) {
                        add(FactoryBean().apply {
                            ir = 1
                            volume = 9
                            if (mRecycleType == 3) {
                                rodHinderValue = EntityType.ROD_HINDER_MIN3
                                SPreUtil.put(AppUtils.getContext(), SPreUtil.rodHinderValue1, rodHinderValue)
                            } else if (mRecycleType == 1) {
                                rodHinderValue = EntityType.ROD_HINDER_MIN1
                                SPreUtil.put(AppUtils.getContext(), SPreUtil.rodHinderValue1, rodHinderValue)
                            }

                        })
                    } else {
                        add(FactoryBean().apply {
                            ir = 1
                            volume = 9
                            rodHinderValue = EntityType.ROD_HINDER_MIN1
                        })
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.rodHinderValue1,  EntityType.ROD_HINDER_MIN1)
                        add(FactoryBean().apply {
                            ir = 1
                            volume = 10
                            rodHinderValue = EntityType.ROD_HINDER_MIN1
                        })
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.rodHinderValue2,  EntityType.ROD_HINDER_MIN1)

                    }
                }
            }
            val getIccid = IccidOper.getInstance(AppUtils.getContext()).getIccid()
            val getImei = IccidOper.getInstance(AppUtils.getContext()).getIMEI()
            val getImsi = IccidOper.getInstance(AppUtils.getContext()).getIMSI()

            //模拟测试
//            val saveIccid = getNew5(getIccid)
//            val saveImei = getNew5(getImei)
//            val saveImsi = getNew5(getImsi)
            //指定设备
//                val saveIccid = "898604B2012290663242"
//                val saveImei = "866735074560560"
//                val saveImsi = "460083265103242"
//                val postSn = "0136004ST00066"

            //正式获取
            val saveIccid = getIccid
            val saveImei = getImei
            val saveImsi = getImsi


            val postSn = sb.toString()
            SPreUtil.put(AppUtils.getContext(), SPreUtil.setIccid, saveIccid)
            SPreUtil.put(AppUtils.getContext(), SPreUtil.setImei, saveImei)
            SPreUtil.put(AppUtils.getContext(), SPreUtil.setImsi, saveImsi)
            Loge.d("出厂配置 saveIccid $saveIccid  saveImei $saveImei saveImsi $saveImsi")
            //客户提供 sn和imei不能重复 重复需要向客户删除
            from["dealerId"] = acetDealerId?.text.toString()
            from["debugPasswd"] = 123456 ////设备调试密码
            from["imei"] = saveImei
            from["recycleType"] = mRecycleType  ////回收箱类型 1单个投口  2 双投口  3子母口
            from["sn"] = postSn
            from["config"] = Gson().toJson(ifb)
            val headers = mutableMapOf<String, String>()
            headers["token"] = BuildConfig.initToken
            httpRepo.issueDevice(headers, from).onSuccess { initString ->
                SPreUtil.put(AppUtils.getContext(), SPreUtil.init, true)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.init_sn, postSn)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.gversion, CmdCode.GJ_VERSION)
                cabinetVM.insertInfoLog(LogEntity().apply {
                    cmd = "issueDevice"
                    msg = "出厂化配置成功"
                    time = AppUtils.getDateYMDHMS()
                })
                initSocket(mHost, mPort)
            }.onFailure { code, message ->
                cabinetVM.insertInfoLog(LogEntity().apply {
                    cmd = "issueDevice"
                    msg = "$code,$message"
                    time = AppUtils.getDateYMDHMS()
                })
                if (code == 1066) {
                    hideLoading(false, "初始化智能回收柜成功")
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.init, true)
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.init_sn, postSn)
                    initSocket(mHost, mPort)
                } else {
                    hideLoading(false, "智能回收柜初始化失败 $code $message")
                }
            }.onCatch { e ->
                hideLoading(false, "出厂化配置 onCatch ${e.errorMsg}")
                cabinetVM.insertInfoLog(LogEntity().apply {
                    cmd = "issueDevice"
                    msg = "${e.errorCode},${e.errorMsg}"
                    time = AppUtils.getDateYMDHMS()
                })
            }
        }
    }

    private fun hideLoading(isShow: Boolean, text: String) {
        cabinetVM.mainScope.launch {
            Toast.makeText(AppUtils.getContext(), text, Toast.LENGTH_LONG).show()
            cpbLoading?.isVisible = isShow
        }
    }

    private fun AppCompatEditText.setAlphanumericLimitTopFour() {
        // 输入过滤器
        filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            val pattern = Pattern.compile("[^0-9]")
            if (pattern.matcher(source).find()) "" else null
        }, InputFilter.LengthFilter(4))

        // 实时验证
        addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                isConfirm = false
                isConfirm2 = false
                llConfirm?.isVisible = false
                llConfirm2?.isVisible = false
            }
        })
    }

    private fun AppCompatEditText.setAlphanumericLimitLastFive() {
        // 输入过滤器
        filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            val pattern = Pattern.compile("[^0-9]")
            if (pattern.matcher(source).find()) "" else null
        }, InputFilter.LengthFilter(5))

        // 实时验证
        addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                isConfirm = false
                isConfirm2 = false
                llConfirm?.isVisible = false
                llConfirm2?.isVisible = false

            }
        })
    }

    private fun AppCompatEditText.setAlphanumericLimitNumber() {
        // 输入过滤器
        filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            val pattern = Pattern.compile("[^0-9]")
            if (pattern.matcher(source).find()) "" else null
        }, InputFilter.LengthFilter(4))

        // 实时验证
        addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString() ?: ""
                isConfirm = false
                isConfirm2 = false
                llConfirm?.isVisible = false
                llConfirm2?.isVisible = false
                if (!s.toString().matches(Regex("^[0-9]*$"))) {
                    error = "只允许数字"
                }
                if (input.isNotEmpty()) {
                    val number = input.toInt()
                    val formatted = String.format("%04d", number) // 补足4位
                    if (input != formatted) {
                        acetSn1?.setText("$formatted")
                        acetSn1?.setSelection(formatted.length)
                    } else if (input.length == 4) {
                        acetSn1?.setText("$input")
                        acetSn1?.setSelection(input.length)
                    }
                } else {
                    acetSn1?.setText("")
                }
            }
        })
    }

    abstract class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    /***
     * 初始化连接socket
     */
    private fun initSocket(mHost: String? = BuildConfig.socketIP, mPort: Int? = BuildConfig.socketPort) {
        toGoMain(mHost!!,mPort!!)
    }
    private fun toGoMain(mHost: String = BuildConfig.socketIP,
                         mPort: Int = BuildConfig.socketPort) {
        SPreUtil.put(AppUtils.getContext(), SPreUtil.initSocket, true)
        val typeGrid = SPreUtil[AppUtils.getContext(), SPreUtil.type_grid, -1]
        val b = Bundle()
        b.putString("host",mHost)
        b.putInt("port",mPort)
        val intent = Intent()
        intent.putExtras(b)
        when (typeGrid) {
            1, 3 -> {
                intent.setClass(this@InitFactoryActivity, NavTouSingleActivity::class.java)
                startActivity(intent)
            }

            2 -> {
                intent.setClass(this@InitFactoryActivity, NavTouDoubleActivity::class.java)
                startActivity(intent)
            }
        }
        finish()
    }
}