//package com.recycling.toolsapp.utils
//
//import android.graphics.Bitmap
//import android.graphics.Color
//import com.sumiramis.awesomeqr.renderer.RenderOption
//import com.sumiramis.awesomeqr.renderer.AwesomeQrRenderer
//import com.sumiramis.awesomeqr.renderer.logo.Logo
//import com.sumiramis.awesomeqr.renderer.background.StillBackground
//import com.sumiramis.awesomeqr.renderer.errorCorrectionLevel.ErrorCorrectionLevel
//
//class QrCodeGenerator {
//
//    fun generateQrCodeWithRoundedLogo(
//        content: String,        // 要编码的文本或URL
//        size: Int,              // 二维码图片大小（像素）
//        logoBitmap: Bitmap?,    // Logo的Bitmap对象
//        cornerRadius: Float = 20f // Logo圆角半径（像素）
//    ) {
//        // 创建渲染选项
//        val renderOption = RenderOption().apply {
//            this.content = content
//            this.size = size
//            borderWidth = 20 // 设置边框宽度
//            ecl = ErrorCorrectionLevel.H // 使用高容错级别，确保加Logo后仍可扫描
//            patternScale = 0.35f
//            roundedPatterns = true // 启用二维码本身的圆角模式
//
//            // 设置颜色
//            color.apply {
//                light = Color.WHITE   // 空白区域颜色
//                dark = Color.BLACK    // 数据点颜色（可根据品牌色调整）
//                background = Color.WHITE // 背景颜色
//                auto = false
//            }
//
//            // 设置Logo
//            logo = Logo().apply {
//                bitmap = logoBitmap
//                borderRadius = cornerRadius // 设置Logo圆角半径
//                borderWidth = 10 // Logo边框宽度，可选
//                scale = 0.2f // Logo大小相对于二维码的比例（建议0.15-0.25）
//            }
//        }
//
//        // 异步渲染二维码
//        AwesomeQrRenderer.renderAsync(renderOption) { result ->
//            if (result.bitmap != null) {
//                // 生成成功，使用result.bitmap
//                val finalQrCodeBitmap: Bitmap = result.bitmap
//                // 例如：显示在ImageView上或保存到文件
//                // imageView.setImageBitmap(finalQrCodeBitmap)
//            } else {
//                // 处理错误
//            }
//        }
//    }
//}