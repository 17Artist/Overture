# Meta 系统

所有 Meta 在物品配置的 `meta` 节点下定义。键名加 `!!` 后缀表示锁定（更新时强制重新应用）。

## attribute

属性修饰符。

```yaml
meta:
  attribute:
    mainhand:                          # 槽位
      generic_attack_damage: "+7"      # 固定加成
      generic_attack_speed: "+10%"     # 百分比加成
    offhand:
      generic_armor: "+2~5"            # 区间随机
```

**槽位**：`mainhand`、`offhand`、`head`、`chest`、`legs`、`feet`、`any`

**值格式**：
| 格式 | 运算 | 示例 |
|------|------|------|
| `+n` | ADD_NUMBER | `"+7"` |
| `+n%` | ADD_SCALAR | `"+10%"` |
| `+min~max` | ADD_NUMBER (随机) | `"+2~5"` |

## enchantment

附魔。键名为 minecraft 命名空间键（小写）。

```yaml
meta:
  enchantment:
    sharpness: 3
    unbreaking: 2
    fire_aspect: 1
```

## unique

唯一物品标记。生成时写入 UUID、玩家名、时间戳。

```yaml
meta:
  unique: true
```

## durability

自定义耐久系统。

```yaml
meta:
  durability:
    max: 100                           # 最大耐久
    remains: STICK                     # 损坏后残骸（可选）
    synchronous: true                  # 同步原版耐久条（默认 true）
    display: "&8[ &f%symbol% &8]"      # 自定义样式（可选，覆盖全局）
    display-symbol:
      full: "◆"
      empty: "◇"
```

**耐久条变量**：`%symbol%`、`%current%`、`%max%`、`%percent%`

**注入的展示变量**：`durability`（耐久条）、`durability_current`、`durability_max`

**耐久耗尽行为**：当 `durability_current` 降至 0 时，物品的交互（左键/右键/攻击/破坏方块）会被自动阻止。如果配置了 `remains`，物品会变为残骸物品；否则播放破碎音效并移除物品。

## unbreakable

不可破坏。

```yaml
meta:
  unbreakable: true
```

## custom_model_data

自定义模型数据。

```yaml
meta:
  custom_model_data: 10001
```

## item_flag

物品标志。

```yaml
meta:
  item_flag:
    - HIDE_ATTRIBUTES
    - HIDE_ENCHANTS
    - HIDE_UNBREAKABLE
```

## color

皮革装备颜色。

```yaml
meta:
  color: "255,128,0"     # RGB
  # 或
  color: "#FF8000"       # HEX
```

## skull

头颅皮肤。

```yaml
meta:
  skull: "player_name"                    # 玩家名
  # 或
  skull: "http://textures.minecraft.net/texture/..."  # URL
  # 或
  skull: "eyJ..."                         # Base64
```

## potion

药水效果（仅药水类物品）。

```yaml
meta:
  potion:
    - "SPEED,100,1"              # 类型,持续时间(tick),等级
    - "REGENERATION,200,2"
```

## native

原生 NBT 直写。数据写入物品 NBT 根节点（不在 overture 节点下）。

```yaml
meta:
  native:
    CustomTag: "hello"
    nested:
      key: 42
```

## shiny

发光效果。

```yaml
meta:
  shiny: true
```
