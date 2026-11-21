# 批量重命名（Android）

一个原生 Android 应用：选中手机里的一个文件夹，批量改名并自动加序列号。支持自定义命名模板、纯序列号模式、按拍摄时间/修改时间排序、改名预览、冲突检测和**一键撤销**。

- 语言：Kotlin + Jetpack Compose（Material 3）
- 最低系统：Android 8.0（API 26）
- 权限：**不需要任何存储权限**，通过系统文件夹选择器（SAF）申请单个文件夹的读写授权
- 包名：`com.dsh.batchrenamer`

---

## 一、怎么拿到能安装的 APK

你手上有一台没有 Android 开发环境的电脑也没关系。项目已经配好了 GitHub Actions 自动构建，把代码传到 GitHub，云端会自动编译出 APK。

### 方式 A：GitHub 云端自动构建（推荐，不需要装任何开发工具）

1. **注册 / 登录 GitHub**：打开 https://github.com （没有账号就注册一个，免费）。

2. **新建仓库**：右上角 `+` → **New repository**。
   - Repository name 填 `BatchFileRenamer`（名字随意）
   - 选 **Private**（私有）或 Public 都行，私有同样能用 Actions
   - **不要**勾选 "Add a README file"
   - 点 **Create repository**

3. **上传项目文件**：在新仓库页面点 **uploading an existing file** 链接（或 `Add file` → `Upload files`）。
   把本项目文件夹 `android-file-renamer` **里面的所有内容**（不是这个文件夹本身）拖进网页上传区域，包括：
   - `app/`、`.github/`、`build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、`.gitignore`

   > ⚠️ **关键**：`.github/workflows/build-apk.yml` 必须一起上传，否则不会自动构建。
   > 如果网页上传时看不到 `.github` 文件夹，说明你的文件管理器隐藏了以 `.` 开头的项目。
   > Windows 资源管理器一般能看到；看不到时请在资源管理器中打开「查看 → 显示 → 隐藏的项目」。
   > 上传完成后，在仓库里确认存在 `.github/workflows/build-apk.yml` 这个文件。

4. **提交**：页面底部点 **Commit changes**。

5. **等待云端编译**：点仓库顶部的 **Actions** 标签页。
   - 会出现一个名为 `Build APK` 的运行记录，状态是黄色圆点（进行中）
   - 首次构建大约 **5～10 分钟**（要下载 Gradle 和依赖）
   - 变成 **绿色对勾** 就成功了

6. **下载 APK**：点进那次成功的运行 → 页面拉到最底部 **Artifacts** 区域 → 下载 `BatchFileRenamer-apk`（下载下来是一个 zip）。

7. **安装**：
   - 把 zip 传到手机上解压，得到 `BatchFileRenamer-debug.apk`
   - 点开安装即可。如果提示"禁止安装未知来源应用"，去设置里给浏览器/文件管理器开启对应权限再装。

> 如果 Actions 运行失败（红色叉），点进去看日志里第一段报错，把报错发我，我直接改代码。

#### 兜底：如果上传后 Actions 没跑起来

说明 `.github/workflows/build-apk.yml` 没上传成功（这个文件夹是点开头的，偶尔会被漏掉）。手动补上即可：

1. 在仓库页面点 **Add file** → **Create new file**
2. 文件名一栏**完整输入**：`.github/workflows/build-apk.yml`（输入 `/` 会自动变成目录层级）
3. 打开项目里的 `.github/workflows/build-apk.yml`，把全部内容粘贴进去
4. 点 **Commit changes**

提交后 Actions 会自动开始跑。

### 方式 B：用自己的 Android Studio 编译

1. 安装 [Android Studio](https://developer.android.com/studio)（约 2GB，安装时让它装好 SDK）
2. 打开 Android Studio → **Open** → 选择 `android-file-renamer` 文件夹
3. 首次打开会自动下载 Gradle 和依赖（需要联网，等它 Sync 完成）
4. 菜单 **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
5. 编译完成后点弹出提示里的 **locate**，APK 在：
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 二、怎么用

1. 打开 App，点 **选择文件夹**，在系统弹出框里挑一个文件夹（比如相机导出的目录）。
   - 系统会申请该文件夹的读写授权，**授权后 App 会一直记得**，下次打开不用重选。
2. 设置命名规则：
   - **自定义模板**：在输入框里写模板，或点下面的快捷预设
   - **纯序列号**：直接生成 `001.jpg`、`002.jpg`……
3. 设置 **起始序号**、**补零位数**（例如位数 3 → `001`；填 0 → `1`）
4. 选择 **排序方式**（序号按这个顺序分配）和 **文件范围**（全部 / 仅图片 / 仅视频 / 图片+视频）
5. 下方实时预览"原名 → 新名"。冲突项会用红色标出，执行时自动跳过。
6. 点底部 **开始重命名**，确认后执行（分两步：先改成临时名，再改成目标名，这样"互相改名"的场景也不会冲突）。
7. 完成后弹窗显示成功/失败数量。点 **撤销这次修改** 可以整体还原。

### 命名模板占位符

| 占位符 | 含义 | 示例 |
|---|---|---|
| `{name}` | 原文件名（不含扩展名） | `IMG_2031` |
| `{n}` | 序列号 | `001` |
| `{ext}` | 扩展名 | `jpg` |
| `{date}` | 日期 `yyyyMMdd` | `20240101` |
| `{time}` | 时间 `HHmmss` | `143025` |
| `{yyyy}` `{MM}` `{dd}` | 年 / 月 / 日 | `2024` `01` `01` |
| `{HH}` `{mm}` `{ss}` | 时 / 分 / 秒（注意 `{MM}` 是月、`{mm}` 是分） | `14` `30` `25` |

规则说明：

- **模板里如果没写 `{ext}`，会自动保留原扩展名**，所以 `{n}` 得到的是 `001.jpg` 而不是 `001`。
- 结果里不能用的字符（`\ / : * ? " < > |`）会自动替换成 `_`，首尾的空格和点会被去掉。
- 名字过长会自动截断到 200 个字符。

常见例子：

| 模板 | 结果 |
|---|---|
| `{name}_{n}` | `旅行_001.jpg` |
| `{n}` | `001.jpg` |
| `IMG_{date}_{n}` | `IMG_20240101_001.jpg` |
| `{yyyy}-{MM}-{dd}_{n}` | `2024-01-01_001.jpg` |

> 选「按拍摄时间」排序时，会读取照片的 EXIF 拍摄时间；视频没有 EXIF，则用修改时间。
> 文件很多时首次排序会慢一些（要逐个读 EXIF），请耐心等待。

---

## 三、项目结构

```
android-file-renamer/
├─ app/src/main/java/com/dsh/batchrenamer/
│  ├─ MainActivity.kt                 入口
│  ├─ model/                          数据模型（文件、规则、计划）
│  ├─ engine/
│  │  ├─ TemplateEngine.kt            模板解析、文件名清洗
│  │  ├─ RenamePlanner.kt             生成改名计划 + 冲突检测（不碰文件）
│  │  └─ ExifDates.kt                 读取拍摄时间（带缓存）
│  ├─ data/
│  │  ├─ DocumentRepository.kt        SAF 文件夹读取
│  │  ├─ RenameExecutor.kt            两阶段批量改名 + 回滚
│  │  └─ UndoStore.kt                 撤销记录持久化
│  └─ ui/                             Compose 界面 + ViewModel
├─ app/src/main/res/                  资源、图标、主题
├─ .github/workflows/build-apk.yml    云端自动构建
└─ 构建脚本（Gradle）
```

设计上的几个关键点：

- **先算完再动手**：`RenamePlanner` 只生成计划，界面预览的就是真正会执行的映射，不会"点下去才发现顺序不对"。
- **两阶段改名**：批量改名最容易踩的坑是「A→B、B→C」时就地改名会撞车。这里先把所有文件改成唯一临时名，再统一改成目标名。
- **失败即回滚**：单个文件失败时立刻改回原名，不会留下半改名状态。
- **撤销**：完成后的映射会保存到本地，App 重启后依然能撤销上一批。

---

## 四、已知限制

- 只处理所选文件夹**当前这一层**的文件，不递归处理子文件夹。
- 依赖系统文件选择器提供的文件访问能力。极少数第三方文件管理器（或云盘）的实现不支持重命名，这类文件夹里操作会报失败。
- 撤销只保留**最近一批**（再执行一批会把上一批的撤销记录覆盖掉）。
- 批量改名运行中请不要强杀 App；如果极端情况下中断，文件夹里可能残留以 `__rnm_tmp_` 开头的临时文件，手动改名回来即可。

---

## 五、本地命令行构建（可选）

如果你已经装好 JDK 17 和 Android SDK，项目没有内置 Gradle Wrapper，可以先生成再构建：

```bash
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`
