#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成百货商品 SVG 图（写入 shop-frontend/public/imgs/goods/ 与 carousel/）"""
import os, urllib.parse

OUT = "/Users/shuozhi/IdeaProjects/shopping/shop-frontend/public/imgs/goods"
os.makedirs(OUT, exist_ok=True)

# (slug, emoji, name, bg 渐变主色, 辅助色)
GOODS = [
    # 1 数码家电
    ("earbuds-pro", "🎧", "无线蓝牙耳机 Pro", "#e3f2fd", "#90caf9"),
    ("smartband-s6", "⌚", "智能手环 S6", "#e8eaf6", "#9fa8da"),
    ("powerbank-10k", "🔋", "快充移动电源", "#e0f7fa", "#80deea"),
    ("monitor-4k", "🖥️", "4K 显示器", "#ede7f6", "#b39ddb"),
    ("air-conditioner", "❄️", "节能变频空调", "#e1f5fe", "#81d4fa"),
    ("airfryer-5l", "🍟", "大容量空气炸锅", "#fff3e0", "#ffcc80"),
    # 2 家居日用
    ("thermos-cup", "🥤", "316保温杯", "#fff8e1", "#ffe082"),
    ("storage-set", "🧹", "收纳五件套", "#f1f8e9", "#c5e1a5"),
    ("kettle-15l", "🫖", "恒温电水壶", "#fbe9e7", "#ffab91"),
    ("latex-pillow", "😴", "天然乳胶枕", "#f3e5f5", "#ce93d8"),
    ("clean-set", "🧽", "清洁卫生套组", "#e0f2f1", "#80cbc4"),
    # 3 美妆个护
    ("facial-cleanser", "🧴", "氨基酸洁面乳", "#fce4ec", "#f48fb1"),
    ("toner-ha", "💧", "玻尿酸爽肤水", "#e8f5e9", "#a5d6a7"),
    ("sunscreen-50", "🧢", "清爽防晒霜", "#e3f2fd", "#64b5f6"),
    ("lipstick-gift", "💄", "丝绒口红礼盒", "#ffebee", "#ef9a9a"),
    # 4 食品生鲜
    ("daily-nuts", "🌰", "每日坚果 30日装", "#fff3e0", "#ffca8a"),
    ("organic-milk", "🥛", "有机纯牛奶 12盒", "#f5f5f5", "#e0e0e0"),
    ("coffee-beans", "☕", "云南咖啡豆 1kg", "#fbe9e7", "#d7a27a"),
    ("durian-cake", "🍰", "榴莲千层蛋糕", "#fffde7", "#ffd54f"),
    # 5 服饰鞋包
    ("cotton-tshirt", "👕", "重磅纯棉T恤", "#eceff1", "#b0bec5"),
    ("denim-pants", "👖", "直筒休闲牛仔裤", "#e8eaf6", "#7986cb"),
    ("sun-jacket", "🧥", "防晒皮肤衣", "#e1f5fe", "#4fc3f7"),
    ("sneakers", "👟", "轻弹运动鞋", "#fce4ec", "#f06292"),
    # 6 运动户外
    ("yoga-mat", "🧘", "TPE环保瑜伽垫", "#e8f5e9", "#81c784"),
    ("dumbbell-20", "🏋️", "可调节哑铃 20kg", "#efebe9", "#bcaaa4"),
    ("sport-towel", "🛀", "速干运动毛巾", "#e1f5fe", "#90caf9"),
    ("camping-tent", "⛺", "双人露营帐篷", "#f1f8e9", "#aed581"),
    # 7 母婴玩具
    ("blocks-space", "🚀", "积木空间站 520粒", "#e3f2fd", "#9ecbff"),
    ("baby-clothes", "👶", "纯棉婴儿连体衣", "#fff3e0", "#ffccbc"),
    ("science-books", "🔭", "儿童百科绘本 8册", "#f3e5f5", "#ba68c8"),
    # 8 图书文具
    ("markers-24", "🖍️", "双头马克笔 24色", "#ffebee", "#ff8a80"),
    ("notebook-a5", "📔", "多袋拉链笔记本", "#e0f2f1", "#4db6ac"),
    ("gel-pens-20", "🖊️", "中性笔 20支装", "#e8eaf6", "#7986cb"),
]

TPL = """<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{c1}"/>
      <stop offset="1" stop-color="{c2}"/>
    </linearGradient>
  </defs>
  <rect width="600" height="600" fill="url(#bg)"/>
  <circle cx="480" cy="120" r="150" fill="#ffffff" opacity="0.18"/>
  <circle cx="110" cy="500" r="180" fill="#ffffff" opacity="0.14"/>
  <circle cx="60" cy="80" r="46" fill="#ffffff" opacity="0.22"/>
  <circle cx="540" cy="470" r="28" fill="#ffffff" opacity="0.28"/>
  <text x="300" y="330" font-size="230" text-anchor="middle">{emoji}</text>
  <rect x="130" y="452" width="340" height="56" rx="28" fill="#ffffff" opacity="0.75"/>
  <text x="300" y="490" font-size="30" font-weight="600" fill="#3a3f5c" text-anchor="middle" font-family="'PingFang SC','Microsoft YaHei',sans-serif">{name}</text>
  <text x="300" y="546" font-size="18" fill="#3a3f5c" opacity="0.55" text-anchor="middle" font-family="'PingFang SC',sans-serif">星选百货 · 精选好物</text>
</svg>
"""

for slug, emoji, name, c1, c2 in GOODS:
    svg = TPL.format(emoji=emoji, name=name, c1=c1, c2=c2)
    with open(f"{OUT}/{slug}.svg", "w", encoding="utf-8") as f:
        f.write(svg)

print(f"goods svg x{len(GOODS)} -> {OUT}")

# ---------------- 轮播 banner ----------------
CO = "/Users/shuozhi/IdeaProjects/shopping/shop-frontend/public/imgs/carousel"
os.makedirs(CO, exist_ok=True)

def banner(file, c1, c2, title, sub, badge, deco):
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="1225" height="460" viewBox="0 0 1225 460">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{c1}"/>
      <stop offset="1" stop-color="{c2}"/>
    </linearGradient>
  </defs>
  <rect width="1225" height="460" fill="url(#bg)"/>
  <circle cx="1000" cy="80" r="220" fill="#ffffff" opacity="0.10"/>
  <circle cx="1120" cy="400" r="160" fill="#ffffff" opacity="0.10"/>
  <circle cx="150" cy="420" r="90" fill="#ffffff" opacity="0.10"/>
  <text x="120" y="215" font-size="76" font-weight="800" fill="#ffffff" font-family="'PingFang SC','Microsoft YaHei',sans-serif">{title}</text>
  <text x="122" y="278" font-size="30" fill="#ffffff" opacity="0.92" font-family="'PingFang SC',sans-serif">{sub}</text>
  <rect x="122" y="316" width="{len(badge)*26+40}" height="44" rx="22" fill="#ffffff" opacity="0.24"/>
  <text x="{142+len(badge)*13}" y="345" font-size="22" fill="#ffffff" text-anchor="middle" font-family="'PingFang SC',sans-serif">{badge}</text>
  <text x="920" y="270" font-size="180" text-anchor="middle">{deco}</text>
</svg>
"""
    with open(f"{CO}/{file}", "w", encoding="utf-8") as f:
        f.write(svg)

banner("mall-open.svg", "#5b6ef5", "#8f6ef5", "星选百货 · 新装开业", "8 大品类 33 件甄选好物，全场低至 5 折", "全 场 包 邮", "🛒")
banner("food-fest.svg", "#ff9a6c", "#ff5f5f", "吃货嘉年华", "每日坚果 · 云南咖啡 · 千层蛋糕，新鲜直达", "限 时 特 惠", "🍰")
banner("digital-life.svg", "#22c58b", "#5b6ef5", "智享数码周", "耳机手环显示器，好价一键带回家", "数 码 专 区", "🎧")
print(f"carousel svg x3 -> {CO}")