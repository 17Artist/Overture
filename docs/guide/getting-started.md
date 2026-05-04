# 快速开始

## 构建配置

```kotlin
// build.gradle.kts
plugins {
    id("priv.seventeen.artist.blink.gradle") version "1.1.0"
}

blink {
    name.set("Overture")
    version.set("1.0.0")
    packageName.set("priv.seventeen.artist.overture")
    apiVersion.set("1.21")
    enableAsteroid.set(true)
    enableAria.set(true)
}
```

## 文件结构

插件启用后会在 `plugins/Overture/` 下生成以下目录：

```
plugins/Overture/
├── config.yml           # 主配置
├── items/               # 物品定义（递归扫描，文件夹 = 分组）
│   ├── __group__.yml    # 分组配置（可选，设置 priority）
│   ├── weapons/
│   │   └── swords.yml
│   └── consumables.yml
├── displays/            # 展示方案
│   └── weapon_display.yml
└── scripts/             # Aria 脚本文件（可选）
```

- `items/` 目录递归扫描，每个文件夹自动成为一个物品分组
- `displays/` 目录递归扫描所有 `.yml` 文件
- 以 `__` 开头的文件名和键名会被跳过
- 以 `$` 结尾的键名作为事件模型注册

## 第一个物品

在 `items/` 下创建 `example.yml`：

```yaml
my_sword:
  display: simple_display
  icon: DIAMOND_SWORD
  name:
    item_name: "&b我的第一把剑"
  lore:
    item_type: "&9武器"
    item_desc:
      - "&f这是一把自定义剑"
  meta:
    shiny: true
```

在 `displays/` 下创建 `simple.yml`：

```yaml
simple_display:
  name: "<item_name>"
  lore:
    - "<item_type>"
    - ""
    - "<item_desc...>"
```

重载插件后使用 `/ot get my_sword` 获取物品。

## 命令速查

| 命令 | 说明 |
|------|------|
| `/ot list [group]` | 打开物品菜单 |
| `/ot give <item> [player] [amount]` | 发放物品 |
| `/ot get <item> [amount]` | 获取物品 |
| `/ot rebuild` | 强制重构手持物品 |
| `/ot serialize` | 序列化手持物品 |
| `/ot info` | 查看物品信息 |
| `/ot reload` | 重载配置 |
