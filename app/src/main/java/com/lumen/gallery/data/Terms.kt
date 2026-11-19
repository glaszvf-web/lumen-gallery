package com.lumen.gallery.data

/**
 * v48/v49 · 术语速读字典：白话解释 + 专业缩写后缀原样保留
 * 规则（用户定的）：难懂的描述性说法 → 汉化讲人话；bit / MP / ISO / f/ / PQ / HLG / BT.2020 /
 * CFA / LOG / EXIF 等专业术语与单位后缀原样保留。
 */
object Terms {

    data class Term(val key: String, val plain: String)

    /** key 小写匹配关键词 → 白话解释 */
    val ALL: List<Term> = listOf(
        Term("bit", "位深单位 bit：每个颜色通道用多少个 0/1 记录亮度层次。8 bit = 256 级，10 bit = 1024 级，数字越大明暗过渡越细腻，LOG/HDR 素材通常 10 bit 起步。"),
        Term("mp", "像素量单位 MP（百万像素）：宽×高÷100万。50MP 就是五千万像素。"),
        Term("iso", "ISO 感光度：传感器对光的灵敏程度。数值越高弱光越亮，但噪点也越多。"),
        Term("f/", "光圈值 f/N：N 越小光圈越大，进光越多、背景虚化越强。f/1.4 比 f/4 大四倍。"),
        Term("cfa", "CFA 滤色阵列（拜耳阵列）：传感器每像素只感受一种颜色，靠颜色滤镜阵列拼出全彩。RAW 主数据就是这种未显影的马赛克数据。"),
        Term("log", "LOG 拍摄模式：用对数曲线压平明暗，保留最大后期调色空间。素材看着发灰是正常的，套 LUT 或手动调色后色彩才出来。"),
        Term("hdr", "HDR 高动态范围：一条素材同时记录亮部与暗部的更多细节，峰值亮度远超普通 SDR。"),
        Term("pq", "PQ（ST 2084）：HDR10 用的亮度传递函数，按绝对亮度编码，配合 BT.2020 广色域。"),
        Term("hlg", "HLG：BBC/NHK 的 HDR 传递函数，向下兼容 SDR 电视，华为 LOG 视频常用。"),
        Term("bt.2020", "BT.2020：超高清广色域标准，色彩范围远超 SDR 时代的 BT.709。"),
        Term("bt.709", "BT.709：SDR 高清时代的标准色域，如今普通视频的主流。"),
        Term("exif", "EXIF：照片里记录拍摄参数的元数据区（相机/光圈/快门/ISO/GPS 等）。"),
        Term("makernote", "MakerNote 厂商注释区：各厂商私有的隐藏参数区（未公开规范），可能藏着机型/固件/拍摄模式。"),
        Term("ifd", "IFD（Image File Directory）：TIFF/RAW 里元数据的目录结构，一层层指针串起来。"),
        Term("dng", "DNG 数字底片：Adobe 制定的开放 RAW 标准，华为专业模式直出即此格式。"),
        Term("cr3", "CR3：佳能 RAW 容器（ISO-BMFF 盒结构），GPS 藏在 CMT 盒里的 CMT3 区。"),
        Term("gps", "GPS 定位：拍摄坐标（经纬度/海拔），分享前建议脱敏。"),
        Term("aps-c", "APS-C 半画幅：比全画幅小一圈的传感器尺寸，等效焦距需 ×1.5~1.6。"),
        Term("等效焦距", "等效焦距：换算成全画幅视角后的焦距，方便跨机身比较视野。"),
        Term("白平衡", "白平衡：告诉相机什么是“白色”，决定整张图的冷暖色调。"),
        Term("测光", "测光方式：相机判断画面亮暗的算法（评价/中央重点/点测）。"),
        Term("色域", "色域：设备能显示的颜色范围。广色域（BT.2020/DCI-P3）能表达更鲜艳的颜色。"),
        Term("码率", "码率 Mbps：视频每秒写入的数据量，越高细节保留越多，同分辨率下画质越好。"),
        Term("md5", "MD5 / SHA：文件指纹哈希，逐字节相同则指纹相同——查重与校验的核心依据。"),
        Term("dhash", "dHash 感知哈希：按“相邻像素明暗关系”生成指纹，图稍微压缩/转格式指纹仍接近，专抓视觉重复。"),
        Term("xmp", "XMP：Adobe 可扩展元数据标准，记录编辑历史/版权/AI 生成声明等。"),
        Term("iptc", "IPTC：新闻摄影元数据标准，记录标题/关键词/版权方。"),
        Term(" gamma", "Gamma 伽马：亮度编码曲线，人眼对暗部更敏感所以用非线性曲线存储。"),
        // v51 · RAW 显影术语（白话 + 专业名保留）
        Term("黑电平", "黑电平 Black Level：传感器在无光状态下的读数基线，RAW 显影时一切亮度从这条线起算，偏低会死黑、偏高会发灰。"),
        Term("白电平", "白电平 White Level：传感器饱和上限读数，超过它的亮度全部裁切成纯白。"),
        Term("色彩矩阵", "色彩矩阵 Color Matrix：把传感器原生颜色换算到标准色彩空间（如 sRGB/BT.2020）的系数表，RAW 显影的“调色基准”。"),
        Term("校准光源", "校准光源 Illuminant：色彩矩阵对应的光源场景（D65=标准日光、A=钨丝灯），多组矩阵各管一种光。"),
        Term("线性化表", "线性化表 Linearization Table：把传感器非线性响应拉直的查找表，是 RAW 显影的第一道工序。"),
        Term("增益表", "增益表 Op Gain：镜头与成像系统在不同光圈下的增益修正大表，DNG 高阶画质修正用。"),
        Term("raw", "RAW 数字底片：未显影的传感器原始数据，比 JPEG 多 10~14 bit 的后期空间，需要显影（转 JPEG/调色）才能正常观看。"),
        // v52 · 视频封装术语
        Term("fps", "fps（帧率）：每秒画面张数。24 电影感 / 30 常规 / 60 丝滑运动。"),
        Term("profile", "Profile 编码档位：编码器的能力档（如 HEVC Main 10 = 10bit），档位决定色彩深度与压缩特性。"),
        Term("level", "Level 规格等级：同档位下的分辨率/码率上限等级，越高支持的规格越大。"),
        Term("limited", "Limited 色彩范围（视频级 16-235）：视频行业的标准量化区间，黑不是 0 白不是 255。Full（0-255）多见于摄影机直出与截图。"),
        Term("音轨", "音轨：视频里的声音通道，可有多条（如立体声+解说）。"),
        Term("封装", "封装 Container：把视频流/音频流/字幕打包成一个文件的格式（MP4/MKV/MOV），与编码（HEVC/AV1）是两回事。"),
    )

    /** 从一段文本里挑出命中的术语 */
    fun matchIn(vararg texts: String): List<Term> {
        val joined = texts.joinToString(" ").lowercase()
        return ALL.filter { joined.contains(it.key.lowercase()) }
    }
}
