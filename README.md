# 光析 LUMEN · Lumen Gallery

中文照片元数据透视相册 —— 把每一张照片的"底裤"扒干净。

An Android gallery focused on deep, fully-Chinese metadata inspection for every common (and uncommon) image / video format.

## 简介

光析 LUMEN 是一款以"元数据透视"为核心卖点的 Android 相册。它不依赖任何第三方元数据库，
所有解析器（JPEG 段结构 / PNG chunk / GIF 块 / WebP RIFF / BMP / ISO-BMFF / TIFF·DNG IFD）
均为逐字节手写实现，配合全量中文 tag 字典，把编码结构、拍摄参数、隐私痕迹、AI 生成指纹
全部拆开讲人话。

**术语规则**：难懂的专业描述一律白话汉化；专业缩写后缀（bit / MP / ISO / f/ / PQ / HLG /
BT.2020 / CFA）原样保留。

## 功能特性

### 解析引擎
- **全格式支持**：JPEG · PNG（cICP HDR / APNG）· GIF · WebP · BMP · HEIC · AVIF ·
  DNG / CR2 / NEF / ARW / ORF / RW2 / CR3 等 RAW · MP4 / HEVC 视频
- **TiffDeep IFD 解析器**：IFD0 / SubIFD / ExifIFD / GPS 分组输出，DNG 全套
  （黑电平 / 色彩矩阵 / 校准光源 / CFA）中文转译
- **CR3 字节级脱敏**：佳能容器 CMT 盒定位 → GPS 指针（0x8825）物理归零，
  只删定位不伤拍摄参数（附合成字节流单元测试）
- **MakerNote 逆向**：15 种基址 × 双偏移基准试探标准 IFD 结构，未公开区域诚实标注
- **AI 取证**：ComfyUI / Stable Diffusion WebUI / InvokeAI 生成图识别，
  提取完整 prompt 与工作流 JSON
- **RAW 显影预览**：从底片内嵌 JPEG（0x0201/0x0202）直接显影显示
- **视频色彩科学**：MediaExtractor 深挖 10bit / HLG / PQ / BT.2020 → 华为 LOG 判定
- **术语速读**：内置 30+ 条术语字典，透视页自动列出本图涉及术语的白话解释，数据行可点查

### 相册与工具
- 时间线分组 / 相册集 / 那年今天回忆页 / 日历视图 / 地图模式 / 收藏星标
- 批量：多选、脱敏导出、重命名、缩放、水印、格式转换（JPEG/PNG/WebP）、
  哈希 CSV、PDF 合成、ZIP 打包、元数据 JSON 导出
- 双图对比（元数据 diff）、相似图聚类（dHash）、隐私雷达
- 液态玻璃设计语言、OLED 纯黑模式、强调色主题、桌面 Widget、外部分享直透
- 全程离线，**不申请 INTERNET 权限**，任何数据不出设备

## 构建

要求：Android Studio（或 Gradle 8.9+）· JDK 17 · compileSdk 35 · minSdk 26

```bash
gradle assembleRelease        # 或 import 到 Android Studio 直接跑
gradle testReleaseUnitTest    # 单元测试（TIFF 解析 / CR3 脱敏）
```

- 签名：默认走 debug 签名。如需自有签名，在项目根目录建
  `lumen-signing.properties`（store.file / store.password / key.alias / key.password），
  也可用同名环境变量提供。
- 测试样张：`testimgs/` 内含脚本字节级手工合成的 DNG 样张，
  可用 `tools/gen_testimgs.py` 与 `tools/gen_dng_v27.py` 重新生成。

## 权限说明

| 权限 | 用途 |
|---|---|
| READ_MEDIA_IMAGES / VIDEO | 读取媒体库展示与解析 |
| ACCESS_MEDIA_LOCATION | 缺失时系统会在 FUSE 层抹除 GPS EXIF，透视仪需要真实数据 |
| — | **无 INTERNET 权限**，全程离线 |

## 文档

- `docs/CHANGELOG.md` —— 61 个版本的完整迭代纪事
- `docs/UPLOAD_GUIDE.md` —— 发布到代码托管平台的操作指引

## License

MIT — 见 [LICENSE](LICENSE)
