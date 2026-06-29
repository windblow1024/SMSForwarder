# 短信转发器 App — 修改说明

## 与原需求文档的关键区别

根据你的要求，对原设计做了以下重要修改：

### 1️⃣ 默认被监控号码改为 10086202
- **默认值**：`10086202`
- **原设计**：无默认值
- 可在设置中自由添加、修改、删除

### 2️⃣ 转发逻辑改为：监听到新增短信 → 实时转发
- **原设计**：收到"申请转发"指令后，一次性查询最近 N 条短信并回复
- **新设计**：
  - 收到"申请转发"指令 → 进入授权有效期（默认30分钟）
  - 在授权期内，**每 3 秒轮询检查被监控号码是否有新增短信**
  - 发现新增短信 → **立即实时转发**给白名单号码
  - 转发格式：`【转发信息】+号码：短信内容`

### 3️⃣ 转发格式
- 原示例：`[短信转发] 来自 10086 的最新短信：`
- 新格式：`【转发信息】10086：【短信内容】`

## 项目结构

```
SMSForwarder/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/smsforwarder/
│       │   ├── SmsForwarderApp.kt          # Application 类
│       │   ├── data/
│       │   │   ├── db/
│       │   │   │   ├── AppDatabase.kt       # Room 数据库
│       │   │   │   └── Daos.kt              # DAO 接口
│       │   │   ├── model/Entities.kt        # 数据实体
│       │   │   └── repository/AppRepository.kt
│       │   ├── receiver/
│       │   │   ├── SmsReceiver.kt           # 短信广播接收
│       │   │   └── BootReceiver.kt          # 开机自启动
│       │   ├── service/
│       │   │   └── SmsForwardService.kt     # 核心转发服务
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── screen/
│       │   │   │   ├── MainScreen.kt        # 主页 + 底部导航
│       │   │   │   ├── WhitelistScreen.kt    # 白名单管理
│       │   │   │   ├── MonitoredNumberScreen.kt  # 监控号码管理
│       │   │   │   ├── ForwardLogScreen.kt  # 转发日志
│       │   │   │   └── SettingsScreen.kt    # 设置
│       │   │   ├── theme/Theme.kt
│       │   │   └── viewmodel/MainViewModel.kt
│       │   └── util/
│       │       ├── SmsReader.kt             # 读取短信
│       │       ├── SmsSender.kt             # 发送短信
│       │       ├── PermissionUtil.kt        # 权限工具
│       │       └── SettingsKeys.kt
│       └── res/
│           ├── mipmap-*/ic_launcher.png
│           └── values/
│               ├── strings.xml
│               └── styles.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```