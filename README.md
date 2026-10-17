# 民宿记账 · Homestay Ledger

一个给**民宿房东**用的安卓收入记账本。纯本地、离线、无账号、无广告、**不联网**。

- 包名 `com.fnos.homestay`　｜　版本 1.0（versionCode 1）　｜　约 **25 KB**
- minSdk 21（Android 5.0+）/ targetSdk 34
- ⭐ **零权限**：`AndroidManifest.xml` 里连 `INTERNET` 都没有 → 数据不可能外传
- 数据文件：`/data/data/com.fnos.homestay/files/records.txt`，每行 `日期|金额|备注`（纯文本，可直接备份/导出）
- 不用 Gradle / Android Studio / AndroidX，只用系统框架 API，命令行 `aapt2 → javac → d8 → zipalign → apksigner` 直接出 APK

## 下载

- 最新版页面：`https://github.com/<用户名>/<仓库名>/releases/latest`
- **永久直链**（永远指向最新版，换版本只要传同名新文件）：
  `https://github.com/<用户名>/<仓库名>/releases/latest/download/homestay-ledger-v1.0.apk`

> 安装前需在手机上允许「安装未知来源应用」。本 APK 使用调试签名，仅供自用/内测分享。

## 功能

| 页面 | 内容 |
|---|---|
| **明细** | 顶部显示当前筛选范围的合计；筛选按钮「全部 → 本月 → 今年」循环；每条 = 星期/日期 + 备注 + 金额；点条目 → 编辑（改日期/金额/备注、删除）；底部固定「+ 记一笔」 |
| **汇总** | `◀ 2026 ▶` 切年；1~12 月各一行（笔数 + 金额）；底部「全年合计」；**点某个月 → 跳到明细只看那个月** |

**记一笔**：日期默认今天，点日期可开系统日历改到任意一天（补记）；金额支持小数；备注可留空（备注里的 `|` 和换行会被自动换成空格，防止破坏文件格式）。

## 截图

> 待补：手机截 3 张放进 `screenshots/`（明细页 / 汇总页 / 记一笔弹窗）。

## 构建

```bash
SDK=/tmp/and bash build.sh          # 产物 build/homestay-ledger.apk
python3 gen_icon.py --preview       # 图标 ASCII 自检
```

依赖三样（见 `build.sh` 顶部变量）：

| 变量 | 内容 |
|---|---|
| JDK | `javac` / `keytool` |
| build-tools | `aapt2` `d8` `zipalign` `apksigner`（脚本取 `$SDK/bt/*/` 下第一个） |
| platform | `android.jar`（脚本取 `$SDK/pl/android-*/android.jar`） |

没有 SDK 时可以用命令行工具包：

```bash
curl -LO https://dl.google.com/android/repository/build-tools_r34-linux.zip
curl -LO https://dl.google.com/android/repository/platform-35_r02.zip
# 解压后 chmod -R +x，再用 SDK=<目录> 跑 build.sh
```

## 项目结构

```
AndroidManifest.xml          清单（零权限）
build.sh                     一条命令打 APK
gen_icon.py                  用 PIL 生成各密度图标
res/drawable*/               图标 + 列表行背景
src/com/fnos/homestay/
    MainActivity.java        全部界面与逻辑（941 行，单文件）
```

## 隐私

- 不申请任何权限，无网络代码，数据只存在 App 私有目录。
- 卸载即删除；要备份就把 `records.txt` 拷出来。

## 手打 APK 踩过的坑（沿用，别重踩）

1. `d8`（build-tools 34 / R8 8.2.2）解析**匿名内部类**会 NPE 崩溃 → 本工程**零匿名内部类**：Activity 自己 `implements View.OnClickListener, DatePickerDialog.OnDateSetListener`，动态行靠 `setTag(Object)` + 前缀分发。
2. `View.setTag(int, Object)` 首参必须是资源 id，用字符串 key 编译不过 → 一律 `setTag(obj)` / `getTag()`。
3. `ScrollView.addView()` 的子 View 必须用 `FrameLayout.LayoutParams`，否则边距/权重被丢。
4. 打包后必须回读验证：`aapt2 dump badging` + `dump xmltree` + 翻 `classes.dex` 里的**单字面量**（中文文案、`records.txt`）。
5. 排序用手写插入排序（`Collections.sort` 要传 Comparator = 匿名内部类，踩坑 1）。

## 许可

[MIT](LICENSE)
