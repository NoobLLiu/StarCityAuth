# AuthId - 统一玩家身份系统

## 项目概述

AuthId 是一个 Minecraft Java 版 Paper 1.21 服务器插件，用于解决混合玩家群体的身份统一问题。服务器同时存在三种玩家渠道：

| 渠道 | 来源 | UUID 特征 |
|------|------|-----------|
| **Java Premium** | 正版玩家 | Mojang 官方分配，唯一且固定 |
| **Java Offline** | 离线/盗版玩家 | 由启动器或服务器根据用户名生成，不稳定 |
| **Bedrock (Geyser)** | 基岩版互通玩家 | 经 Geyser 转换，可能存在全零 UUID 或重复问题 |

### 核心目标

为所有渠道的玩家生成一个 **canonical UUID（规范 UUID）**，使原生 Minecraft 服务器和大多数插件能通过 UUID 正确识别同一个玩家，无论他们从哪个渠道登录。

## 架构设计

### 身份解析流程

```
玩家登录 → 识别渠道 (PlayerChannel)
    ├── Java Premium → 直接使用 Mojang UUID 作为 canonical UUID
    ├── Java Offline → 通过 SHA-256 哈希生成稳定的 canonical UUID
    └── Bedrock Geyser → 检测全零 UUID → 使用备用哈希生成 canonical UUID
                         └── 非全零 → 直接使用 Geyser UUID
```

### 关键设计决策

1. **不修改玩家原始 UUID**：Minecraft 服务器和插件生态深度依赖 UUID，强行修改会带来兼容性问题。我们采用 **映射方案**，维护 `channelUuid → canonicalUuid` 的映射表。

2. **SHA-256 哈希生成离线 UUID**：使用盐值 + 用户名生成稳定的 Version 4 UUID，确保同一用户名始终生成相同的 canonical UUID。

3. **Mojang API 联动**：对于离线玩家，可通过 Mojang API 查询其正版 UUID，实现离线账号与正版账号的自动关联。

4. **持久化存储**：所有身份映射数据存储在 JSON 文件中，支持内存缓存 + 磁盘持久化。

### 核心组件

| 组件 | 职责 |
|------|------|
| `PlayerChannel` | 玩家渠道枚举 |
| `ResolvedIdentity` | 统一身份数据结构 |
| `ChannelUUIDGenerator` | 各渠道 canonical UUID 生成器 |
| `IdentityResolver` | 身份解析核心逻辑 |
| `MojangProfileResolver` | Mojang API 查询 + 缓存 |
| `PlayerIdentityStore` | 身份数据持久化存储 |
| `AuthIdConfig` | 插件配置 |

## 开发规范

### 平台要求

- **服务端**：Paper 1.21+
- **Java 版本**：21
- **构建工具**：Gradle (使用 paperweight-userdev 或标准 Paper API)

### 代码规范

1. **包结构**：
   - `com.authid` - 主包，包含主类和配置
   - `com.authid.resolver` - 身份解析相关
   - `com.authid.store` - 数据存储相关
   - `com.authid.listener` - Paper 事件监听器

2. **命名规范**：
   - 类名：PascalCase
   - 方法名：camelCase
   - 常量：UPPER_SNAKE_CASE
   - 配置键：snake_case

3. **日志**：使用 SLF4J（Paper 内置），前缀统一为 `[AuthId]`

### Paper 插件开发要点

1. **事件监听**：使用 `PlayerJoinEvent`、`PlayerLoginEvent` 等 Paper 事件
2. **数据存储**：可选择 JSON 文件、SQLite 或 MySQL
3. **异步处理**：Mojang API 查询必须异步执行，避免阻塞主线程
4. **配置管理**：使用 Paper 的 `YamlConfiguration` 或自定义配置

### 测试场景

- [ ] 正版玩家首次登录 → 正确记录并使用 Mojang UUID
- [ ] 离线玩家首次登录 → 生成稳定的 canonical UUID
- [ ] Geyser 基岩玩家（全零 UUID）→ 生成备用 canonical UUID
- [ ] 同一玩家从不同渠道登录 → 映射到同一 canonical UUID（需 Mojang API 关联）
- [ ] 服务器重启后 → 所有映射数据正确恢复
- [ ] Mojang API 不可用时 → 优雅降级，不影响正常游戏

## 待解决问题

1. **Paper 插件结构转换**：当前代码基于 Fabric 模组结构，需要转换为 Paper 插件结构
2. **UUID 替换机制**：需要研究 Paper API 是否支持在登录阶段替换玩家 UUID（可能需要 ProtocolLib 或事件监听）
3. **Geyser API 集成**：需要确认 Geyser 提供的 API 来准确识别基岩版玩家
4. **数据迁移**：如果服务器已有玩家数据，需要考虑迁移策略
