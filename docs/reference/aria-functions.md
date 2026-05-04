# Aria 内置函数

物品事件脚本中可用的所有内置函数。

## item 命名空间

| 函数                      | 说明                                  |
|-------------------------|-------------------------------------|
| `item.damage(n)`        | 扣除耐久 n 点，触发 `DURABILITY_CHANGED` 信号 |
| `item.repair(n)`        | 恢复耐久 n 点（不超过最大值）                    |
| `item.durability()`     | 获取当前耐久值                             |
| `item.maxDurability()`  | 获取最大耐久值                             |
| `item.consume(n?)`      | 消耗物品数量，默认 1                         |
| `item.data(key)`        | 读取活跃数据                              |
| `item.data(key, value)` | 写入活跃数据                              |
| `item.removeData(key)`  | 删除活跃数据                              |
| `item.update()`         | 标记物品需要更新                            |
| `item.id()`             | 获取物品 ID                             |
| `item.amount()`         | 获取物品数量                              |
| `item.uses()`           | 获取剩余使用次数（读取 `data.uses`）            |
| `item.use(n?)`          | 消耗使用次数，默认 1。次数耗尽时自动消耗物品             |

## cooldown 命名空间

| 函数                        | 说明                      |
|---------------------------|-------------------------|
| `cooldown.check(key, ms)` | 检查冷却是否结束，返回 `true` 表示可用 |
| `cooldown.set(key)`       | 设置冷却开始时间为当前时间           |
| `cooldown.time(key, ms)`  | 获取剩余冷却时间（毫秒）            |

::: tip
`cooldown.check` 和 `cooldown.time` 的 `ms` 参数是冷却总时长（毫秒）。
:::

## potion 命名空间

| 函数                                        | 说明                    |
|-------------------------------------------|-----------------------|
| `potion.give(type, duration, amplifier?)` | 给予药水效果，amplifier 默认 0 |
| `potion.remove(type)`                     | 移除药水效果                |
| `potion.clear()`                          | 清除所有药水效果              |

药水类型使用 minecraft 命名空间键，如 `'SPEED'`、`'REGENERATION'`。

## 全局函数

| 函数                                  | 说明                       |
|-------------------------------------|--------------------------|
| `cancel()`                          | 取消当前 Bukkit 事件           |
| `sendMessage(msg)`                  | 向玩家发送消息（支持 `&` 颜色代码）     |
| `playSound(sound, volume?, pitch?)` | 播放音效，volume/pitch 默认 1.0 |
| `runCommand(cmd)`                   | 以玩家身份执行命令                |
| `broadcast(msg)`                    | 全服广播消息                   |
