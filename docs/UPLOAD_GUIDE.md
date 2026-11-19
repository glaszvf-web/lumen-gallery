# 上传指引（给执行上传的 AI）

本文件面向负责把本仓库上传到代码托管平台的执行者。按步骤执行即可，无需额外决策。

## 1. 仓库信息（建议值，可微调）

| 项 | 值 |
|---|---|
| 仓库名 | `lumen-gallery` |
| 一句话描述（英文） | Fully-offline Android gallery with byte-level, fully-Chinese metadata inspection for JPEG/PNG/HEIC/RAW/CR3 & video color science. No third-party metadata libs. |
| 一句话描述（中文） | 全离线安卓相册：逐字节解析 JPEG/PNG/HEIC/RAW/CR3 与视频色彩科学，全中文元数据透视，无第三方元数据库。 |
| 可见性 | public |
| 主要语言标签 | kotlin, android, jetpack-compose, metadata, exif, raw, forensics, privacy |
| 默认分支 | main |

## 2. 上传前检查（已由打包方完成，上传前复核一遍）

- [x] 全库无个人称谓（已脚本化清零并 grep 复核）
- [x] 包名已中性化：`com.lumen.gallery`
- [x] 无签名文件（lumen-signing.properties / *.keystore 已排除）
- [x] 无验证截图（shots/ 已排除）
- [x] 无本机绝对路径写入文档
- [ ] 复核：全库身份字符串 grep 应为 0 命中 与个人称谓应为 0 命中

## 3. 上传步骤

### 方式 A：有 gh CLI
```bash
cd <本目录>
git init -b main
git add -A
git commit -m "Lumen Gallery v61.0.0.1 — initial open-source release"
gh repo create lumen-gallery --public --source=. --push \
  --description "Fully-offline Android gallery with byte-level Chinese metadata inspection"
```

### 方式 B：仅 git
```bash
cd <本目录>
git init -b main
git add -A
git commit -m "Lumen Gallery v61.0.0.1 — initial open-source release"
# 在平台上手动创建空仓库 <YOUR_GITHUB_USERNAME>/lumen-gallery 后：
git remote add origin https://<YOUR_GITHUB_USERNAME>@github.com/<YOUR_GITHUB_USERNAME>/lumen-gallery.git
git push -u origin main
```

占位符 `<YOUR_GITHUB_USERNAME>` 需替换为实际账号名。

## 4. 上传后动作

1. 仓库 Settings → About 填入上面的英文描述与标签
2. Releases → Create a new release：tag `v61.0.0.1`，标题 `v61.0.0.1`，
   附上构建说明（本仓不含预编译 APK；`gradle assembleRelease` 自产）
3. 如平台支持 Social Preview，可后续补截图（本包刻意不含任何截图）

## 5. 禁止事项

- 禁止把任何 `*.keystore` / `lumen-signing.properties` 补提交进仓库
- 禁止补充任何包含个人相册内容的截图
- 禁止在仓库简介里附加与本项目无关的个人链接
