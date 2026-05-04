# 动作系统

物品事件通过 Aria 脚本引擎驱动。脚本在物品加载时预编译为 `AriaCompiledRoutine`，运行时只需创建上下文执行。

## 配置格式

```yaml
my_item:
  event:
    from: [model_id]           # 引用事件模型
    data:                      # 事件变量（注入脚本上下文 vars）
      custom_key: "value"
    on_right_click!!: |        # !! = 自动取消原版事件
      if (cooldown.check('skill', 3000)) {
        cooldown.set('skill')
        sendMessage('&a技能释放！')
        item.damage(5)
      } else {
        var.remaining = cooldown.time('skill', 3000) / 1000
        sendMessage("&c冷却中，剩余 {remaining} 秒")
      }
    on_attack: |
      item.damage(1)
```

## 事件触发点

| 触发器 | Bukkit 事件 | 说明 |
|--------|-------------|------|
| `on_left_click` | PlayerInteractEvent | 左键 |
| `on_right_click` | PlayerInteractEvent | 右键 |
| `on_right_click_entity` | PlayerInteractEntityEvent | 右键实体 |
| `on_attack` | EntityDamageByEntityEvent | 攻击实体 |
| `on_damage` | PlayerItemDamageEvent | 耐久损耗 |
| `on_consume` | PlayerItemConsumeEvent | 消耗物品 |
| `on_drop` | PlayerDropItemEvent | 丢弃 |
| `on_pick` | EntityPickupItemEvent | 拾取 |
| `on_block_break` | BlockBreakEvent | 破坏方块 |
| `on_item_break` | PlayerItemBreakEvent | 物品损坏 |
| `on_swap_to_offhand` | PlayerSwapHandItemsEvent | 切到副手 |
| `on_swap_to_mainhand` | PlayerSwapHandItemsEvent | 切到主手 |
| `on_build` | ItemBuildEvent.Pre | 构建时 |
| `on_release` | ItemReleaseEvent | 释放时 |
| `on_release_display` | ItemReleaseEvent.Display | 展示生成时 |

键名加 `!!` 后缀会自动取消原始 Bukkit 事件（`event.isCancelled = true`）。

## 上下文变量

| 变量 | 类型 | 说明 |
|------|------|------|
| `player` | Player | 当前玩家 |
| `item` | ItemStream | 当前物品流 |
| `event` | Event | 原始 Bukkit 事件 |
| `vars` | Map | `event.data` 配置的变量 |

## 内置函数

完整函数列表见 [Aria 内置函数](/reference/aria-functions)。

### 常用示例

```javascript
// 冷却技能
if (cooldown.check('fireball', 5000)) {
  cooldown.set('fireball')
  sendMessage('&c火球术！')
  playSound('ENTITY_BLAZE_SHOOT', 1.0, 1.5)
  item.damage(10)
}

// 读写数据
var.level = item.data('custom.level')
item.data('custom.level', level + 1)

// 消耗品（使用次数限制）
item.use()
sendMessage('&a剩余使用次数: ' + item.uses())

// 消耗品（直接消耗物品数量）
item.consume(1)
potion.give('REGENERATION', 200, 2)

// 不可丢弃
cancel()
sendMessage('&c此物品不可丢弃')
```

### 使用次数 (uses)

在 `data` 中配置 `uses` 字段，配合 `item.use()` 和 `item.uses()` 实现使用次数限制：

```yaml
my_scroll:
  display: simple_display
  icon: PAPER
  name:
    item_name: "&d传送卷轴"
  lore:
    item_type: "&5消耗品"
    item_desc:
      - "&f使用后传送回出生点"
      - "&7剩余次数: &f<uses_display>"
  data:
    uses: 5
  data-mapper:
    uses_display: "{uses}"
  event:
    on_right_click!!: |
      if (item.uses() > 0) {
        item.use()
        runCommand('spawn')
        sendMessage('&a已传送！剩余 ' + item.uses() + ' 次')
      } else {
        sendMessage('&c使用次数已耗尽')
      }
```

`item.use(n?)` 会自动递减 `data.uses`，当次数降至 0 时自动消耗物品。
