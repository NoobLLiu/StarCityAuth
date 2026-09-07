# AuthId 身份系统设计文档

## 1. 系统概述

### 核心概念

- **账号（Account）**：AuthMe 管理的登录账号，由邮箱绑定，一个玩家名对应一个账号
- **身份（Identity）**：AuthId 管理的游戏身份，每个身份拥有独立的 UUID，最多 3 个
- **玩家连接 UUID**：玩家实际连接服务器时的 UUID（由 `PlayerHandshakeEvent` 替换）

### 关系模型

```
一个 AuthMe 账号（邮箱） → 最多 3 个身份（UUID）
                          → 选择一个身份 → 替换当前连接 UUID
```

### 数据标识

- 以 AuthMe 账号名（玩家名）作为主键关联身份
- 邮箱作为辅助验证（从 AuthMe 读取，不单独存储）

---

## 2. 登录流程

```
玩家连接服务器
    │
    ▼
PlayerHandshakeEvent ─→ 识别渠道，记录原始 UUID
    │
    ▼
AuthMe 登录/注册流程（AuthMe 接管）
    │
    ▼
AuthMe LoginEvent 触发 ─→ AuthId 开始介入
    │
    ├─ 检查是否绑定邮箱（通过 AuthMe API 读取）
    │   │
    │   ├─ 未绑定邮箱 ─→ 聊天栏提示：请先使用 /email add 绑定邮箱
    │   │                  （等待玩家绑定后重新触发检查）
    │   │
    │   └─ 已绑定邮箱 ─→ 检查是否有创建的身份
    │       │
    │       ├─ 无身份 ─→ 聊天栏提示 + 自动打开身份创建页面
    │       │
    │       └─ 有身份 ─→ 打开「身份选择页面」
    │
    ▼
玩家选择身份 ─→ 替换 UUID ─→ 正常游戏
```

---

## 3. 页面设计

### 3.1 身份选择页面（主页面）

**触发时机**：AuthMe 登录成功后，已绑定邮箱且已有身份

**GUI 类型**：27 格箱子（3 行）

```
┌─────────────────────────────────────────────┐
│  ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️  │
│  灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻  │
├─────────────────────────────────────────────┤
│         │ 身份1  │         │ 身份2  │         │ 身份3  │         │ 创建新  │
│  灰玻    │ ⚔️     │  灰玻    │ 🛡️     │  灰玻    │ 🏠     │  灰玻    │ ➕     │
├─────────────────────────────────────────────┤
│  ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️  │
│  灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻  │
└─────────────────────────────────────────────┘
```

**槽位布局**：
| 槽位 | 内容 | 说明 |
|------|------|------|
| 0,1,2,3,5,6,7,8 | 灰色玻璃板 | 装饰边框 |
| 4 | 空（或标题信息） | 顶部中间 |
| 10 | 身份1 物品 | 点击选择此身份 |
| 13 | 身份2 物品 | 点击选择此身份 |
| 16 | 身份3 物品 | 点击选择此身份 |
| 9,11,12,14,15,17 | 灰色玻璃板 | 分隔 |
| 18-26 | 灰色玻璃板 + 创建按钮 | 底部边框 |

**身份物品显示**：
- 物品：玩家头颅（Skull），显示身份对应的皮肤
- 名称：`§a身份1` / `§7身份2` / `§c身份3`（当前使用的用 `§a` 高亮）
- Lore：
  ```
  §7UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
  §7创建时间: 2026-09-07
  §7上次使用: 2026-09-07
  §e§l点击切换到此身份
  ```
- 当前使用中的身份：物品发光（附魔光效），名称前加 `§a✔ `

**创建新身份按钮**（槽位 17）：
- 物品：`EMERALD`
- 名称：`§a创建新身份`
- Lore：
  ```
  §7创建一个新的游戏身份
  §7当前已有 §f1/3 §7个身份
  §e§l点击创建
  ```

### 3.2 创建身份确认页面

**触发时机**：玩家点击「创建新身份」按钮

**GUI 类型**：27 格箱子

```
┌─────────────────────────────────────────────┐
│  ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️  │
│  灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻  │
├─────────────────────────────────────────────┤
│         │ 确认   │         │ 输入名 │         │ 取消   │         │         │
│  灰玻    │ ✅     │  灰玻    │ 📝     │  灰玻    │ ❌     │  灰玻    │  灰玻    │
├─────────────────────────────────────────────┤
│  ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️  │
│  灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻  │
└─────────────────────────────────────────────┘
```

**流程**：
1. 玩家点击「创建新身份」→ 关闭 GUI → 聊天栏提示输入身份名称
2. 玩家在聊天栏输入名称 → 系统验证名称合法性（长度、字符、重复）
3. 验证通过 → 打开确认页面，显示新身份信息
4. 点击「确认」→ 创建身份，生成 UUID，保存数据
5. 点击「取消」→ 返回身份选择页面

**确认页面物品**：
- 槽位 10（确认）：`LIME_DYE`，名称 `§a确认创建`，Lore 显示新身份 UUID
- 槽位 13（信息）：`PAPER`，名称 `§e新身份信息`，Lore 显示详情
- 槽位 16（取消）：`RED_DYE`，名称 `§c取消`，Lore `§7返回身份选择`

### 3.3 邮箱未绑定提示页面

**触发时机**：AuthMe 登录成功但未绑定邮箱

**GUI 类型**：27 格箱子

```
┌─────────────────────────────────────────────┐
│  ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️  │
│  灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻  │
├─────────────────────────────────────────────┤
│         │ 提示   │         │ 邮箱   │         │ 帮助   │         │         │
│  灰玻    │ ⚠️     │  灰玻    │ 📧     │  灰玻    │ ❓     │  灰玻    │  灰玻    │
├─────────────────────────────────────────────┤
│  ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️       ◻️  │
│  灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻     灰玻  │
└─────────────────────────────────────────────┘
```

**物品**：
- 槽位 10（提示）：`BARRIER`，名称 `§c§l请先绑定邮箱`，Lore `§7使用身份系统前需要绑定邮箱`
- 槽位 13（邮箱）：`PAPER`，名称 `§e绑定邮箱`，Lore `§7请输入: §f/email add <你的邮箱> <确认邮箱>`
- 槽位 16（帮助）：`BOOK`，名称 `§e帮助`，Lore `§7绑定邮箱后可以§f创建游戏身份§7，每个账号最多§f3个身份`

---

## 4. 命令设计

| 命令 | 说明 | 权限 |
|------|------|------|
| `/authid` | 打开身份选择页面 | `authid.use` |
| `/authid create <名称>` | 直接创建新身份 | `authid.create` |
| `/authid list` | 列出所有身份 | `authid.use` |
| `/authid select <编号>` | 直接选择身份 | `authid.use` |
| `/authid delete <编号>` | 删除身份 | `authid.delete` |
| `/authid info` | 查看当前身份信息 | `authid.use` |

---

## 5. 技术可行性分析

### 5.1 AuthMe 对接

**可行方案**：监听 AuthMe 事件 + 调用 AuthMe API

```java
// 监听登录成功事件
@EventHandler
public void onAuthMeLogin(fr.xephi.authme.events.LoginEvent event) {
    String playerName = event.getPlayer().getName();
    // 获取 PlayerAuth 对象
    PlayerAuth auth = fr.xephi.authme.api.v3.AuthMeApi.getInstance()
        .getAuth(playerName);
    // 读取邮箱
    String email = auth.getEmail();
    // 判断是否绑定邮箱
    if (email == null || email.isEmpty()) {
        // 提示绑定邮箱
    }
}
```

**关键 API**：
| API | 用途 |
|-----|------|
| `AuthMeApi.getInstance().getAuth(name)` | 获取玩家认证数据 |
| `PlayerAuth.getEmail()` | 读取绑定邮箱 |
| `AuthMeApi.getInstance().isAuthenticated(name)` | 检查是否已登录 |
| `fr.xephi.authme.events.LoginEvent` | 登录成功事件 |
| `fr.xephi.authme.events.RegisterEvent` | 注册成功事件 |

**风险**：
- AuthMe 版本兼容性：不同版本 API 可能有差异，需要确认 `NoobLLiu/AuthMeReReloaded` 的具体版本
- 事件时序：需要确保在 AuthMe 登录完成后才触发身份选择

### 5.2 UUID 替换

**可行方案**：在 `PlayerHandshakeEvent` 中替换 UUID

```java
@EventHandler(priority = EventPriority.LOWEST)
public void onHandshake(PlayerHandshakeEvent event) {
    // 记录原始 UUID
    UUID originalUuid = event.getUniqueId();
    // 查询玩家选择的身份 UUID
    UUID identityUuid = getSelectedIdentity(event.getUsername());
    if (identityUuid != null) {
        event.setUniqueId(identityUuid);
    }
}
```

**技术要点**：
- `PlayerHandshakeEvent.setUniqueId()` 是 Paper 原生 API，可靠
- 替换后所有后续事件和 `Player#getUniqueId()` 都使用新 UUID
- 需要在握手阶段就确定 UUID，但此时无法访问 AuthMe API
- **解决方案**：在 AuthMe 登录成功后，将玩家选择的身份 UUID 缓存，下次连接时使用

### 5.3 数据存储

**存储结构**（JSON 文件 `identities.json`）：

```json
{
  "players": {
    "Steve": {
      "email": "steve@example.com",
      "identities": [
        {
          "id": 1,
          "name": "战士",
          "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
          "createdAt": 1694000000000,
          "lastUsedAt": 1694100000000,
          "channel": "java_offline",
          "skinData": "..."
        },
        {
          "id": 2,
          "name": "建筑师",
          "uuid": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
          "createdAt": 1694000000000,
          "lastUsedAt": null,
          "channel": "java_offline",
          "skinData": "..."
        }
      ],
      "selectedIdentityId": 1
    }
  }
}
```

### 5.4 GUI 实现

**可行方案**：使用 Bukkit Inventory API

```java
// 创建 GUI
Inventory gui = Bukkit.createInventory(null, 27,
    net.kyori.adventure.text.Component.text("身份选择"));

// 设置物品
ItemStack identityItem = new ItemStack(Material.PLAYER_HEAD);
SkullMeta meta = (SkullMeta) identityItem.getItemMeta();
meta.setOwner("身份名称");
meta.displayName(Component.text("§a✔ 身份1"));
meta.lore(List.of(
    Component.text("§7UUID: xxxxxxxx-..."),
    Component.text("§e§l点击切换到此身份")
));
identityItem.setItemMeta(meta);
gui.setItem(10, identityItem);

// 打开 GUI
player.openInventory(gui);

// 监听点击
@EventHandler
public void onInventoryClick(InventoryClickEvent event) {
    if (!event.getView().getTitle().equals("身份选择")) return;
    event.setCancelled(true);
    // 处理点击逻辑
}
```

**技术要点**：
- Paper 1.21 支持 `Component.text()` (Adventure API)
- `SkullMeta.setOwner()` 可以显示玩家头颅皮肤
- `InventoryClickEvent` 需要 `setCancelled(true)` 防止物品被拿走

### 5.5 玩家数据迁移

当玩家切换身份时，需要处理数据迁移：

1. **背包数据**：`world/playerdata/<uuid>.dat` 需要在切换时备份和恢复
2. **末影箱**：`world/ender_chests/<uuid>.dat`（如果使用）
3. **位置数据**：AuthMe 的 `lastloc` 数据
4. **其他插件数据**：权限、领地等按 UUID 存储的数据

**方案**：
- 切换身份时，自动备份当前 UUID 的数据
- 如果目标 UUID 有数据，恢复该数据
- 如果没有，创建新数据

---

## 6. 完整流程示例

### 场景1：新玩家首次登录

```
1. 玩家 "Steve" 连接服务器
2. AuthMe 提示注册: /register <password> <password>
3. 玩家注册成功
4. AuthMe RegisterEvent 触发
5. AuthId 检查邮箱 → 未绑定
6. 聊天栏提示: "请先绑定邮箱: /email add <邮箱> <确认邮箱>"
7. 玩家执行: /email add steve@example.com steve@example.com
8. 邮箱绑定成功
9. 玩家执行: /authid (或系统自动触发)
10. 检测到无身份 → 打开身份创建页面
11. 玩家点击「创建新身份」→ 输入名称 "战士"
12. 确认创建 → 生成 UUID → 保存
13. 自动选择该身份 → 替换 UUID → 正常游戏
```

### 场景2：老玩家选择身份

```
1. 玩家 "Steve" 连接服务器
2. AuthMe 登录成功
3. AuthMe LoginEvent 触发
4. AuthId 检查邮箱 → 已绑定
5. 检查身份 → 有2个身份
6. 打开「身份选择页面」
7. 玩家点击「身份1: 战士」
8. 替换 UUID 为身份1的 UUID
9. 玩家数据加载（背包、位置等）
10. 正常游戏
```

---

## 7. 待确认事项

1. **AuthMe 版本**：需要确认 `NoobLLiu/AuthMeReReloaded` 的具体版本和 API 兼容性
2. **服务器是否为离线模式**：影响 UUID 生成和替换逻辑
3. **是否使用 Geyser**：影响渠道识别和 UUID 生成
4. **是否需要数据迁移**：如果服务器已有玩家数据，需要考虑迁移策略
5. **皮肤数据**：是否需要为每个身份独立存储皮肤数据
6. **并发登录**：如果玩家从不同渠道登录，如何处理身份冲突
