# muse-ppt-template

Muse PPT 项目模板仓库。创建 PPT 应用时，系统会从此仓库导入初始骨架文件。

## 目录结构

```
muse-ppt-template/
├── src/
│   ├── config.json  # PPT 配置文件（标题、风格、页面清单）
│   ├── index.html   # 演示文稿入口（画布 1920×1080，contain 缩放）
│   ├── index.css    # 主题 CSS 变量（默认商务蓝）
│   └── pages/       # 幻灯片页面目录（Agent 运行时创建）
│       └── 封面/
│           ├── index.html            # 该页的 HTML 片段
│           ├── shapes/               # 形状定义
│               └── xxx.shape.json
└── README.md        # 本文件
```

## 配置说明

### config.json

```json
{
  "version": "1.0",
  "title": "PPT 模板",
  "style": "default",
  "outline": []
}
```

- `version`: 配置版本，固定为 `"1.0"`
- `title`: PPT 标题
- `style`: 视觉风格标识，默认 `"default"`（商务蓝）
- `outline`: 页面大纲数组，初始为空，由 Agent 生成

### 页面目录约定

每个页面是 `src/pages/` 下的一个独立目录，结构如下：

```
页面名/
├── index.html            # 页面 HTML（单个 <section class="slide"> 片段）
└── shapes/               # 该页使用的形状定义 (.shape.json)
    └── xxx.shape.json
```

- `index.html` 为每个 PPT 页面的入口文件

### index.css

定义 CSS 变量主题，所有页面必须使用变量而非硬编码色值。默认预设包含色彩、字体、字号、间距、圆角等 25 个变量。

### index.html

演示文稿入口文件，核心特性：

- 画布固定 16:9（1920×1080），等比缩放居中
- 页面 HTML 通过占位符 `<!-- PAGES_HTML_REPLACE_POINT -->` 内联注入
- 键盘导航（方向键、空格、Home/End）
- 右下角进度导航点
