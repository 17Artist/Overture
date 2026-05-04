# 命令

主命令：`/overture`（别名：`/ot`、`/oi`）

## 子命令

| 命令 | 参数 | 权限 | 说明 |
|------|------|------|------|
| `/ot list` | `[group]` | 玩家 | 打开物品菜单 GUI，可指定分组 |
| `/ot give` | `<item> [player] [amount]` | OP | 发放物品给指定玩家，默认 1 个 |
| `/ot get` | `<item> [amount]` | 玩家 | 获取物品到自己背包 |
| `/ot rebuild` | — | 玩家 | 强制重构手持物品（不检查版本，直接重新构建） |
| `/ot serialize` | — | 玩家 | 序列化手持物品为 JSON 并输出到聊天 |
| `/ot info` | — | 玩家 | 查看手持物品的 Overture 信息 |
| `/ot reload` | — | OP | 重载所有配置文件 |

## Tab 补全

- `<item>` 参数自动补全所有已注册的物品 ID
- `[player]` 参数自动补全在线玩家名

## info 命令输出

```
--- 物品信息 ---
ID: diamond_sword_1
版本: a3f8c2...
数据: {damage: 7, durability: 100, durability_current: 85}
唯一: UUID=550e8400-e29b-41d4-a716-446655440000
  玩家: Steve
  时间: 2024-01-15 14:30:00
```
