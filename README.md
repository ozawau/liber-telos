# liber-telos

清醒记账，看见目的。

Android 本地账本应用：用 Compose + Room 管理收入、支出、转账与报销，数据只存在本机。

## 功能

- **主页**：结余（可切换本年 / 本月 / 本周 / 今天）、收支趋势图、分类排行、周期收支报告
- **记一笔**：支出 / 收入 / 转账 / 报销；分类、账户、时间与新建时定位
- **流水**：按月、按日分组浏览；支持滑动删除与详情 / 编辑
- **账户**：净资产汇总、分类拖拽排序；点账户看历史流水，点编辑改账户
- **分类**：完整收入 / 支出二级分类；新安装仅种子分类，无默认账户与流水

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- Room（SQLite），账本库路径：`files/ledger/liber_telos.db`
- Navigation Compose、ViewModel + StateFlow
- 最低 Android 8.0（API 26）

## 环境要求

- JDK 17
- Android SDK（compileSdk / targetSdk 35）
- 可选：已连接的设备或模拟器 + `adb`

## 构建与安装

```bash
./gradlew assembleDebug
```

仅安装 APK、**不覆盖**设备账本数据库：

```bash
./scripts/install-apk.sh
```

Debug APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```
app/src/main/java/com/libertelos/app/
  data/          # Room 实体、DAO、Repository、分类迁移
  ui/            # Compose 界面（home / add / accounts / …）
  viewmodel/     # LedgerViewModel
scripts/         # 安装等工具脚本
```

## 数据与部署注意

- 账本在应用私有目录 `files/ledger/`，与常规 `databases/` 分离
- 更新应用时只装 APK；不要用 `adb push` / `run-as … cp` 覆盖 `liber_telos.db`（及 `-wal` / `-shm`）
- Schema / 数据修正应通过 Room 迁移或启动期迁移完成

## License

Private / personal use unless otherwise stated.
