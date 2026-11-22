package com.campusglass.schedule

import android.graphics.Bitmap

/**
 * 教务验证码「看得清」增强（纯显示层处理，不改变提交内容）。
 *
 * 学校这套验证码是 72×27 的小 GIF：深蓝色块状字符 + 满屏彩色小噪点，字符之间还常粘连。
 * 直接塞进小框里会被非整数倍缩放，噪点和字符糊在一起 → 人就容易读错。
 *
 * 这里的做法刻意「保守」：
 * 1) 只把**明显不是蓝色墨水**的像素刷白（彩色噪点），偏蓝的像素一律**保留原来的颜色**——
 *    包括笔画边缘的抗锯齿过渡色。曾经试过「只留纯深蓝、其余全白」的二值化，
 *    结果是 "6" 被读成 "J"、"k" 被读成 "x"，把人带沟里，已废弃；
 * 2) 这里**不做放大**（scale 默认 1）：放大交给界面层按「整数倍物理像素」渲染
 *    （Image + FilterQuality.None），一次采样到位；在这里先放大再让界面缩回去，
 *    等于两次重采样，反而把笔画拉变形；
 * 3) 若这张图不像我们认识的样式（偏蓝像素太少），原样返回，绝不把验证码「改没了」。
 */
internal object CaptchaImage {

    /** 蓝色分量要比红/绿至少高出这么多，才算「墨水像素」。 */
    private const val BLUE_LEAD = 40
    private const val DEFAULT_SCALE = 1
    private const val PAPER_COLOR = 0xFFFFFFFF.toInt()
    /** 放大后像素总量上限，防止极端小图被放到 OOM。 */
    private const val MAX_OUT_PIXELS = 4_000_000

    fun enhance(src: Bitmap, scale: Int = DEFAULT_SCALE): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0 || scale < 1) return src

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        var ink = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val blueish = b - r >= BLUE_LEAD && b - g >= BLUE_LEAD
            if (blueish) {
                ink++
                pixels[i] = c or ALPHA_OPAQUE
            } else {
                pixels[i] = PAPER_COLOR
            }
        }
        // 不认识这个配色 —— 说明学校换图了，老老实实显示原图
        if (ink < 20) return src

        val outW = w * scale
        val outH = h * scale
        if (outW.toLong() * outH > MAX_OUT_PIXELS) {
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }
        val out = IntArray(outW * outH)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val color = pixels[y * w + x]
                for (dy in 0 until scale) {
                    val row = (y * scale + dy) * outW
                    for (dx in 0 until scale) out[row + x * scale + dx] = color
                }
            }
        }
        return Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, outW, 0, 0, outW, outH)
        }
    }

    private const val ALPHA_OPAQUE = 0xFF000000.toInt()
}
