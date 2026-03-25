# WanPass

WanPass 是一个面向 Android 的本地优先保险箱应用，用来保存登录信息和私密笔记。项目当前是单模块 Android App，核心目标是把本地存储、解锁、恢复码和 WebDAV 备份这几件事做清楚，而不是做一套复杂的多端同步平台。

## 当前能力

- 创建、编辑、删除两类记录：登录信息、私密笔记
- 首页按标题、账号、网站、正文、备注做本地搜索，并展示最近查看记录
- 使用系统设备凭证解锁，可选强生物识别快速解锁
- 支持自动锁定，退到后台后按配置清空会话
- 首次初始化生成恢复码，已解锁状态下可在设置页查看恢复码
- 支持 HTTPS WebDAV 连接测试、首次启用、增量同步、从远端恢复、接管远端备份
- 敏感页面启用系统防截屏 / 录屏保护；应用数据不参与 Android 云备份或设备迁移
- 默认离线可用；不依赖服务器才能完成本地增删改查

## 技术栈与运行要求

### 技术栈

- Kotlin 2.2
- Jetpack Compose + Material 3
- Room
- DataStore Preferences
- Hilt
- OkHttp
- kotlinx.serialization

### 运行要求

- JDK 17
- Android SDK 36 编译环境
- Android API 35+ 设备或模拟器
- 设备必须已启用系统锁屏密码 / PIN / 图案

## 快速开始

### 运行单元测试

Windows:

```powershell
.\gradlew.bat test
```

macOS / Linux:

```bash
./gradlew test
```

### 构建调试包

Windows:

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux:

```bash
./gradlew assembleDebug
```

### 本地运行

推荐直接用 Android Studio 打开仓库并运行 `app` 模块。项目当前只有一个应用模块 `:app`。

## 架构概览

项目代码主要集中在 `app/src/main/java/io/github/c1921/wanpass` 下，按职责拆成几层：

- `ui`: Compose 路由、页面状态和交互，包含 onboarding、unlock、home、item、settings
- `session`: 会话态、自动锁定、内存中的保险箱密钥、副本清理、搜索索引加载
- `security`: 保险箱数据加密、Keystore 包裹、本地恢复材料管理、WebDAV 凭据加密
- `data.local`: Room 实体、DAO、数据库
- `data.webdav`: WebDAV 配置校验、HTTP 客户端、远端文件模型、备份/恢复服务
- `data.repository`: 本地仓库实现和 WebDAV 同步网关
- `domain`: 仓库接口、领域模型、同步状态

一个典型数据流如下：

1. UI 收集用户输入并调用仓库接口。
2. 仓库从 `VaultSessionManager` 取当前会话中的保险箱密钥。
3. 业务字段先加密，再写入 Room / WebDAV。
4. 解锁后才把搜索字段解密载入内存索引；锁定后清空。

## 加密与密钥管理

这一节描述当前代码里的真实实现，不包含未来规划。

### 1. 保险箱密钥

- 首次创建本地保险箱时，会生成一个随机 32 字节保险箱密钥。
- 这个密钥用于加解密所有保险箱内容，不直接明文落盘。
- 本地持久化时，保险箱密钥会先被 Android Keystore 中的 AES-256 密钥再次包裹。
- 当前包裹密钥别名为 `wanpass_vault_wrap_key`。

### 2. 数据加密算法

- 保险箱内容使用 `AES/GCM/NoPadding`
- 每次加密生成新的 12 字节随机 IV
- GCM 认证标签长度为 128 bit
- 持久化字节格式为 `IV || ciphertext+tag`

当前会被保险箱密钥加密的内容包括：

- 记录标题
- 登录信息或笔记正文的序列化内容
- 本地搜索用的 search blob
- 恢复码本身

当前不会被加密、需要以明文字段保留的元数据包括：

- `id`
- `type`
- `createdAt` / `updatedAt` / `deletedAt`
- `revision`
- `syncState`

这样做的原因是本地列表排序、增量同步和远端索引需要这些元数据参与判断。

### 3. 恢复码与恢复材料

- 恢复码格式是 `6 x 4` 分组，例如 `ABCD-EFGH-IJKL-MNOP-QRST-UVWX`
- 字符集会避开容易混淆的字符
- 输入恢复码时会先做标准化：转大写、去掉空格和短横线
- 恢复码应抄写到离线介质并单独保管，不建议截图保存，或放入会同步到云端的相册 / 笔记工具

恢复链路如下：

1. 生成 16 字节随机 salt。
2. 使用 `PBKDF2WithHmacSHA256` 从恢复码派生 256-bit 恢复密钥。
3. 迭代次数固定为 `120000`。
4. 用恢复密钥再次加密保险箱密钥，形成 `recoveryWrappedVaultKey`。
5. 用保险箱密钥加密恢复码本身，便于已解锁状态下在设置页重新展示。

本地 DataStore 会保存四类关键材料：

- Keystore 包裹后的保险箱密钥
- 恢复码包裹后的保险箱密钥
- 恢复 salt
- 用保险箱密钥加密后的恢复码

如果本机的 Keystore 包裹凭证失效，用户可以输入恢复码，重新解出保险箱密钥，并在当前设备上重新绑定新的 Keystore 包裹密钥。

### 4. 解锁流程

WanPass 不使用独立“主密码”。当前设计依赖 Android 提供的本机安全能力：

- `BiometricPrompt` 的 `DEVICE_CREDENTIAL`
- 可选的 `BIOMETRIC_STRONG`
- 用于包裹保险箱密钥的 Keystore AES-256 密钥要求最近一次系统身份验证
- 当前认证窗口为 `300` 秒；超过窗口后，再次解锁、使用恢复码重绑本地包裹密钥或首次写入本地包裹密钥前，会重新触发系统验证
- 设置页重新查看恢复码、首次启用 WebDAV 和从远端恢复等高风险操作，也会先触发系统验证

解锁成功后：

- 应用从 DataStore 读取被包裹的保险箱密钥
- 使用 Android Keystore 解开包裹
- 将保险箱密钥副本放入内存会话
- 解密所有 search blob，载入内存搜索索引

锁定或自动锁定时：

- 会话中的保险箱密钥字节数组会被覆盖清零
- 内存搜索索引会被清空
- 后续读取记录明文会失败，直到再次解锁

### 5. WebDAV 凭据加密

WebDAV 密码不会明文保存在设置里：

- 密码使用单独的 Android Keystore AES-256 密钥加密
- 当前别名为 `wanpass_webdav_password_key`
- DataStore 里保存的是 Base64 形式的密文
- 这把凭据密钥同样要求最近一次系统身份验证，认证窗口也是 `300` 秒
- 解密后的明文密码只保留在当前解锁会话的内存缓存中；锁定、自动锁定或停用 WebDAV 时会清空

这把 WebDAV 凭据密钥与保险箱内容密钥是两条独立链路。

## WebDAV 备份模型

### 基本规则

- 只接受 `https://` WebDAV 地址
- 远端目录默认是 `WanPass`
- 当前实现是“单活设备接管”模型，不做多设备自动合并

### 远端文件

启用后，远端目录会写入这些文件：

- `manifest.json`: 记录 schema 版本、当前接管设备 ID、首次声明时间、最近备份时间
- `index.json`: 记录条目 ID、版本号、更新时间；结构中保留删除状态字段
- `items/<id>.json`: 每条记录一个文件，保存本地已有密文字段的 Base64 形式
- `recovery.json`: 保存恢复材料，用于新设备恢复

远端不会收到本地解锁所需的 Android Keystore 包裹密钥，但会收到恢复所需的恢复材料。

### 备份与恢复流程

首次启用 WebDAV 时：

- 如果远端没有备份，上传本地快照并标记当前设备为 active device
- 如果远端已有备份且本地为空，要求用户用恢复码从远端恢复
- 如果远端已有备份且本地也有数据，要求用户明确选择“上传本地覆盖远端”或“从远端恢复”

从 WebDAV 恢复时：

1. 下载 `manifest.json`、`index.json`、`items/*.json`、`recovery.json`
2. 用用户输入的恢复码和远端恢复材料派生恢复密钥
3. 解出保险箱密钥
4. 在本机重新写入 Keystore 包裹后的保险箱密钥
5. 用远端快照替换本地数据库
6. 再次上传快照，接管远端 active device

## 安全边界与当前限制

- 这不是“主密码 + 云同步”方案；本地解锁依赖系统设备凭证 / 强生物识别。
- Android 系统云备份和 Android 12+ 设备迁移已显式禁用；不能依赖系统备份把本地密钥材料带到新设备。
- 如果已经卸载应用、清除应用数据，或换到一台全新设备，恢复码必须配合 WebDAV 备份一起使用。
- WebDAV 服务器看到的是密文记录、同步元数据和恢复材料，而不是保险箱明文。
- 但如果攻击者同时拿到 WebDAV 备份和正确恢复码，就可以恢复出保险箱内容；恢复码本身必须离线单独妥善保存，不应截图或放入会同步到云端的相册 / 笔记工具。
- 当前同步策略是单活接管，不做多端冲突合并。
- 元数据字段为同步和排序保留明文，不是“整库所有字段全密文”设计。
- 当前仅支持 Android 平台，且最低 API 要求较高。

## 测试

当前仓库已经包含若干单元测试，覆盖的重点包括：

- AES-GCM 加解密回环
- 恢复密钥派生的确定性
- WebDAV 配置校验
- WebDAV 远端模型映射
- WebDAV 同步状态策略
- 首页列表 key 的稳定性

执行命令：

```powershell
.\gradlew.bat test
```
