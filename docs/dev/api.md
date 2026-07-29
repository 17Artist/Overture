# API 参考

## OvertureAPI

公共 API 门面，所有方法均为静态调用。

```kotlin
import priv.seventeen.artist.overture.api.OvertureAPI
```

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getItem(id)` | `OvertureItem?` | 获取物品定义 |
| `getItems()` | `Map<String, OvertureItem>` | 获取所有物品 |
| `getItemIds()` | `List<String>` | 获取所有物品 ID |
| `generateItem(id, player?)` | `ItemStack?` | 生成物品（支持异步线程调用） |
| `getItemName(id)` | `String?` | 获取模板展示名（缓存，无需 build） |
| `getItemLore(id)` | `List<String>?` | 获取模板描述（缓存，无需 build） |
| `getTemplateItem(id)` | `ItemStack?` | 获取模板物品副本（缓存，仅用于展示） |
| `readStream(item)` | `ItemStream` | 从 ItemStack 读取物品流 |
| `isOvertureItem(item)` | `Boolean` | 判断是否为 Overture 物品 |
| `getOvertureId(item)` | `String?` | 获取物品 ID |
| `serialize(item)` | `String` | 序列化物品为 JSON |
| `deserialize(json)` | `ItemStack?` | 从 JSON 反序列化 |
| `registerProvider(provider)` | `Unit` | 注册物品提供者 |
| `reload()` | `Unit` | 重载所有配置 |

## 快速展示读取（无需 build）

物品加载后，每个 `OvertureItem` 会在首次访问时以 `player = null` 构建一次**模板物品**并缓存
（reload 后自动失效）。之后所有展示读取都直接命中缓存，不再触发构建事件链。

```kotlin
// 拍卖行列表页：一页 20 个物品，只需要名字和图标
for (listing in page) {
    val name = OvertureAPI.getItemName(listing.itemId) ?: listing.itemId
    val icon = OvertureAPI.getTemplateItem(listing.itemId)   // 缓存 clone，开销 ≈ ItemStack.clone()
    inventory.setItem(slot, icon)
}
```

注意事项：

- 返回的是**模板默认展示**：条件展示（ConditionalDisplay）按 `player = null` 求值，
  data-mapper 变量使用模板初始数据。需要玩家个性化展示时仍应使用 `generateItem(id, player)`。
- `getTemplateItem` 的返回值**仅用于菜单图标等展示场景，不要发放给玩家**——
  unique 物品的 UUID、时间戳等实例数据不会重新生成。发放物品请始终使用 `generateItem`。

## 异步生成物品

`generateItem` 及其事件链（`ItemBuildEvent`、`ItemReleaseEvent`）采用动态 async 标记，
可以安全地在异步线程调用，适用于生成后序列化入库、邮件发货等纯数据场景：

```kotlin
// 交易行结算：全程无需回到主线程
Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
    val itemStack = OvertureAPI.generateItem(rpgItemId) ?: return@Runnable
    itemStack.amount = amount
    val json = OvertureAPI.serialize(itemStack)
    database.insertMail(buyerUuid, json)   // 数据库 IO 留在异步线程
})
```

约束：

- 异步调用时请传 `player = null`，或确保条件展示脚本不访问世界状态。
- 监听 `ItemBuildEvent` / `ItemReleaseEvent` 的插件如需操作世界状态，
  应先检查 `event.isAsynchronous`，异步时调度回主线程。
- `ItemGiveEvent` / `ItemUpdateEvent` 仍为严格同步事件：`give` 与更新流程会操作玩家背包，
  必须在主线程执行。

## ItemProvider

物品提供者接口，支持多来源物品加载。

```kotlin
interface ItemProvider {
    val id: String           // 提供者标识
    val priority: Int        // 优先级（值越小越先加载）
    fun load(): Map<String, OvertureItem>
    fun reload()
}
```

### 自定义提供者示例

```kotlin
class DatabaseProvider : ItemProvider {
    override val id = "database"
    override val priority = 10

    override fun load(): Map<String, OvertureItem> {
        // 从数据库加载物品配置
        return mapOf()
    }

    override fun reload() {
        // 重新连接数据库
    }
}

// 注册
OvertureAPI.registerProvider(DatabaseProvider())
```

## ItemStream

物品流 — 运行时物品实例。

```kotlin
val stream = OvertureAPI.readStream(itemStack)

// 身份判断
stream.isOverture          // 是否为 Overture 物品
stream.overtureId          // 物品 ID

// 数据读写
stream.getData("damage")?.asInt()
stream.setData("damage", ItemTagData.of(10))
stream.removeData("temp_key")

// 版本检查
stream.isOutdated(item)    // 是否过时

// 保存
stream.save()              // 仅写入 NBT
stream.toItemStack(player) // 完整释放（触发事件链）
```

## 物品序列化

`serialize` / `deserialize` 用于把物品存入数据库（拍卖行、邮件等）：

```kotlin
// 存储：上架/成交时序列化快照
val json = OvertureAPI.serialize(itemStack)

// 恢复：领取时反序列化
val restored = OvertureAPI.deserialize(json)
```

- Overture 物品只存 `id + amount + data + unique`，反序列化时基于**当前模板**重新构建，
  再覆盖恢复 `data`（耐久等活跃数据）与 `unique`（UUID、绑定玩家等），
  因此物品外观始终跟随最新配置，实例数据不丢失。
- `data` 采用类型化 JSON 编码（`{"t": 类型ID, "v": 值}`），NBT 类型无损往返。
- 旧版本序列化的 JSON（`data` 为字符串格式）仍可反序列化，但 `data` 无法恢复（旧格式本身不可逆）。
- `deserialize` 内部会调用物品构建链，同样支持异步线程调用。

## MapperFunction

注册自定义映射函数：

```kotlin
import priv.seventeen.artist.overture.core.mapper.MapperFunction

MapperFunction.register("myFunc") { args ->
    val value = (args[0] as Number).toInt()
    "结果: $value"
}
```

## MetaRegistry

注册自定义 Meta 类型：

```kotlin
import priv.seventeen.artist.overture.core.meta.MetaRegistry

MetaRegistry.register("my_meta") { section, value, locked ->
    MyCustomMeta(section, locked)
}
```
