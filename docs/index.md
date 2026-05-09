---
layout: home

hero:
  name: Overture
  text: 下一代 Minecraft 物品库
  tagline: 为现代服务端设计的物品系统 · 版本感知 · 脚本驱动 · 动态展示
  image:
    src: /logo.svg
    alt: Overture
  actions:
    - theme: brand
      text: 快速开始 →
      link: /guide/getting-started
    - theme: alt
      text: 查看 API
      link: /dev/api
    - theme: alt
      text: GitHub
      link: https://github.com/17Artist/Overture

features:
  - icon: 🌊
    title: 物品流 (ItemStream)
    details: 统一的物品操作中间层，不再直接操作 ItemStack。NBT 读写、事件链、构建释放全部流水线化。
  - icon: 🔐
    title: 版本签名自动同步
    details: 配置变更自动计算 SHA-1 指纹。玩家背包中的物品检测到版本过期会无缝更新到最新定义。
  - icon: 🎯
    title: '!! 锁定控制'
    details: 精确控制更新策略。字段级锁定决定哪些数据保留玩家状态、哪些强制同步最新配置。
  - icon: 🎨
    title: 展示方案分离
    details: Display 独立配置，多个物品共享同一模板。支持基于 Aria 表达式的条件展示切换。
  - icon: ⚡
    title: Aria 脚本引擎
    details: 15 种事件触发点，22 个内置函数，脚本预编译。物品行为由脚本驱动，热重载即时生效。
  - icon: 🧩
    title: 12 种内置 Meta
    details: 属性、附魔、自定义耐久、唯一标记、模型数据、发光、药水、头颅等开箱即用，支持自定义扩展。
---

<style scoped>
.outro {
  max-width: 960px;
  margin: 80px auto 60px;
  padding: 48px 32px;
  text-align: center;
}

.outro-title {
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, var(--overture-glow-start), var(--overture-glow-end));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  margin-bottom: 16px;
}

.outro-text {
  color: var(--vp-c-text-2);
  font-size: 1.05rem;
  line-height: 1.7;
  max-width: 680px;
  margin: 0 auto 32px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  max-width: 720px;
  margin: 40px auto 0;
}

.stat-item {
  padding: 20px 12px;
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  background: var(--vp-c-bg-soft);
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.stat-item:hover {
  transform: translateY(-3px);
  border-color: rgba(var(--overture-brand-rgb), 0.4);
  box-shadow: 0 8px 20px rgba(var(--overture-brand-rgb), 0.1);
}

.stat-value {
  font-size: 1.8rem;
  font-weight: 700;
  background: linear-gradient(135deg, var(--vp-c-brand-1), var(--overture-glow-end));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1.2;
}

.stat-label {
  color: var(--vp-c-text-3);
  font-size: 0.85rem;
  margin-top: 6px;
  letter-spacing: 0.02em;
}

@media (max-width: 640px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

<div class="outro">
  <div class="outro-title">为开发者而生</div>
  <p class="outro-text">
    Overture 构建在 Blink 框架之上，深度集成 Asteroid 跨版本 NMS 桥接与 Aria 轻量级脚本引擎。
    从 YAML 配置到 NBT 操作，从事件驱动到展示渲染，每一层都经过精心设计。
  </p>
  <div class="stats">
    <div class="stat-item">
      <div class="stat-value">12</div>
      <div class="stat-label">Meta 类型</div>
    </div>
    <div class="stat-item">
      <div class="stat-value">15</div>
      <div class="stat-label">事件触发点</div>
    </div>
    <div class="stat-item">
      <div class="stat-value">22</div>
      <div class="stat-label">Aria 函数</div>
    </div>
    <div class="stat-item">
      <div class="stat-value">1.18+</div>
      <div class="stat-label">版本支持</div>
    </div>
  </div>
</div>
