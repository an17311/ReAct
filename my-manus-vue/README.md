# my-manus-vue

Manus 智能助手的前端界面（Vue 3 + Vite + Element Plus）。

## 功能介绍

一个类 Manus 的 AI 智能助手界面：用一句话描述目标，后端会自动拆解执行计划并调度多个专项智能体协作完成。

- 🌐 **网页浏览与信息获取** —— 自动操作真实浏览器，搜索、点击、提取网页内容
- 🗺️ **地图与路线规划** —— 基于高德地图，地点查询、驾车路线规划
- 📊 **数据图表生成** —— 生成可交互的 HTML 图表，支持在线预览与下载
- 📑 **表格与文档生成** —— 可导出 Excel 的数据表格、排版精美的网页文档

界面特性：

- 欢迎屏功能介绍 + 示例任务一键填入，打开即知道"能做什么"
- 任务卡片实时展示**执行计划**（子任务链）、**当前执行状态**与**逐步思考过程**
- 最终结果 Markdown 渲染（表格 / 代码高亮），产物卡片支持 **iframe 在线预览** 与下载
- 断线自动重连提示、任务取消、系统消息提示、深色模式

## 开发

```bash
npm install      # 安装依赖
npm run dev      # 启动开发服务器（默认 http://localhost:5173）
```

开发模式下 WebSocket 通过 vite proxy 转发到 `http://localhost:18081`（后端地址）；
如需指定其他后端，可设置环境变量 `VITE_WS_URL`（如 `ws://192.168.1.10:18081/bs-dialog-websocket`）。

## 构建部署

```bash
npm run build    # 产物输出到 dist/
```

生产部署建议将 `dist/` 与后端同源部署（或将 `VITE_WS_URL` 指向后端地址）。

## 与后端的通信

纯 STOMP over WebSocket（`@stomp/stompjs`）：

| 方向 | 目的地 | 说明 |
|---|---|---|
| 上行 | `/app/enhanced-dialog` | 提交任务 `{type:'user', text}` |
| 上行 | `/app/cancel` | 取消当前任务 |
| 下行 | `/user/queue/dialog` | 结构化事件：`lifecycle_start/end`、`progress`、`result`、`artifact`、`system_info/error`、`cancelled`、`plan_update` |

协议解析集中在 `src/utils/protocol.js`，对旧版后端的纯文本协议（`[系统]` / `[Agent名]` 前缀）做了回退兼容。

## 目录结构

```
src/
├── App.vue               # 根组件：侧栏 + 主区布局
├── main.js               # 入口：Element Plus / 主题
├── styles/theme.css      # 设计变量（明/暗双主题）
├── composables/
│   ├── useStomp.js       # WebSocket 连接管理（自动重连）
│   └── useAgentTask.js   # 任务状态机（事件分发）
├── utils/
│   ├── protocol.js       # 下行消息协议解析
│   └── markdown.js       # Markdown 渲染 + 代码高亮
└── components/           # 侧栏 / 欢迎屏 / 任务卡片 / 计划面板 / 产物卡片 / 消息 / 输入区
```
