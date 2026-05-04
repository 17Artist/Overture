# 架构设计

## 模块结构

```
priv.seventeen.artist.overture/
├── Overture.kt                    # 插件入口
├── api/
│   ├── OvertureAPI.kt             # 公共 API 门面
│   ├── ItemProvider.kt            # 物品提供者接口
│   └── event/                     # Bukkit 代理事件
├── core/
│   ├── item/                      # 物品流系统
│   │   ├── ItemStream.kt          # 物品流基类
│   │   ├── ItemStreamGenerated.kt # 首次生成的物品流
│   │   ├── OvertureItem.kt        # 物品定义
│   │   ├── ItemKey.kt             # NBT 键常量
│   │   ├── ItemSignal.kt          # 信号枚举
│   │   └── ItemSerializer.kt      # JSON 序列化
│   ├── display/                   # 展示系统
│   ├── meta/                      # Meta 系统（12 种实现）
│   ├── model/                     # 事件模型
│   ├── group/                     # 物品分组
│   ├── action/                    # 动作系统 + Aria 集成
│   ├── mapper/                    # 数据映射器
│   └── manager/                   # 管理器
├── feature/                       # 特性模块
├── command/                       # 命令
├── listener/                      # 事件监听
└── util/                          # 工具类
```

## NBT 存储结构

```
ItemStack NBT:
└── overture (Compound)
    ├── id (String)              — 物品 ID
    ├── version (String)         — SHA-1 签名
    ├── data (Compound)          — 活跃数据
    ├── unique (Compound)        — 唯一数据
    │   ├── uuid (String)
    │   ├── player (String)
    │   ├── date (Long)
    │   └── date-formatted (String)
    └── meta-history (List)      — Meta 历史记录
```

## 物品构建生命周期

```
ItemManager.generate(id, player)
│
├─ OvertureItem.build(player)
│   ├─ 创建 ItemStreamGenerated
│   ├─ 写入 overture.id / overture.data
│   ├─ 写入 lockedData（!! 标记）
│   ├─ 触发 ItemBuildEvent.Pre          ← 可取消，可修改变量
│   ├─ 写入 overture.version (SHA-1)
│   └─ 触发 ItemBuildEvent.Post         ← Meta build/drop 在此执行
│       ├─ drop: 移除已删除的 Meta
│       ├─ build: 构建当前 Meta（写入 NBT）
│       └─ 记录 meta-history
│
└─ ItemStream.toItemStack(player)
    ├─ 获取 ItemMeta
    ├─ 触发 ItemReleaseEvent.Release     ← Meta buildMeta + Display 构建
    │   ├─ Meta.buildMeta(itemMeta)
    │   ├─ Meta.buildRelease(item, meta)
    │   ├─ 耐久同步（syncDurability）
    │   └─ Display 构建（仅 ItemStreamGenerated）:
    │       ├─ SelectDisplay 事件
    │       ├─ 条件展示评估
    │       ├─ DataMapper 注入变量
    │       ├─ Display 事件
    │       └─ Display.build() → name + lore
    ├─ 写回 ItemMeta
    ├─ 从 ItemStack 重新读取完整 NBT     ← 包含 Bukkit 写入的 display 等
    ├─ 合并 overture 节点到最新 NBT
    ├─ saveTo → 最终 ItemStack           ← 同时包含 display 和 overture 数据
    ├─ 触发 ItemReleaseEvent.Final       ← 最终修改机会
    └─ 返回 ItemStack
```

## Signal 机制

信号用于在物品操作流程中传递状态信息。

| 信号 | 触发时机 | 效果 |
|------|----------|------|
| `UPDATE_CHECKED` | 更新检查时 | 仅 locked Meta 重新 build，非 locked 保留已有值 |
| `DURABILITY_CHANGED` | 耐久变化时 | 脚本执行后触发 rebuild |
| `DURABILITY_DESTROYED` | 耐久归零时 | 跳过 rebuild，执行物品损坏处理 |

## !! 锁定机制

`!!` 后缀控制物品更新时的行为：

**数据锁定**：`data` 中 `key!!` 标记的数据在每次构建时强制覆盖，不保留物品上已有的值。非锁定数据在更新时保留。

**展示锁定**：`icon!!`、`name!!`、`lore!!` 控制更新时是否强制重新生成对应属性。

**Meta 锁定**：`meta` 中 `key!!` 标记的 Meta 在更新检查时仍然重新 build。非锁定 Meta 在 `UPDATE_CHECKED` 信号下跳过 build，保留已有值。

**事件锁定**：`event` 中 `on_xxx!!` 标记的事件在触发时自动取消原始 Bukkit 事件。
