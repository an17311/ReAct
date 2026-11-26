<template>
    <div class="welcome">
        <div class="welcome-hero">
            <div class="hero-logo">🤖</div>
            <h1 class="hero-title">你好，我是 Manus 智能助手</h1>
            <p class="hero-sub">
                告诉我你想完成什么，我会自动拆解计划、调度多个智能体（浏览器 / 地图 / 图表 / 表格 / 文档），
                全程实时汇报进度，交付可预览、可下载的结果。
            </p>
        </div>

        <!-- 功能介绍：让用户一眼看懂"能做什么" -->
        <div class="feature-grid">
            <div v-for="f in features" :key="f.title" class="feature-card">
                <div class="feature-icon">{{ f.emoji }}</div>
                <div class="feature-name">{{ f.title }}</div>
                <div class="feature-desc">{{ f.desc }}</div>
            </div>
        </div>

        <!-- 示例任务：点击填入输入框 -->
        <div class="examples">
            <div class="examples-title">试试这些任务</div>
            <div class="example-chips">
                <button v-for="e in examples" :key="e.text" class="example-chip" @click="$emit('use-prompt', e.text)">
                    <span class="chip-emoji">{{ e.emoji }}</span>{{ e.text }}
                </button>
            </div>
        </div>
    </div>
</template>

<script setup>
const emit = defineEmits(['use-prompt'])

const features = [
    {
        emoji: '🌐',
        title: '网页浏览与信息获取',
        desc: '自动打开真实浏览器，搜索、点击、翻页、提取网页内容，时效信息一网打尽',
    },
    {
        emoji: '🗺️',
        title: '地图与路线规划',
        desc: '基于高德地图，地点查询、驾车/步行路线规划，距离耗时一目了然',
    },
    {
        emoji: '📊',
        title: '数据图表生成',
        desc: '把数据变成可交互的 HTML 图表，支持在线预览与下载',
    },
    {
        emoji: '📑',
        title: '表格与文档生成',
        desc: '生成可导出 Excel 的数据表格，或排版精美的网页文档报告',
    },
]

const examples = [
    { emoji: '🔍', text: '搜索最近一周 AI 领域的热点新闻，整理成表格' },
    { emoji: '🗺️', text: '规划从北京南站到天津之眼的驾车路线，大概需要多久' },
    { emoji: '🎬', text: '抓取豆瓣电影 Top250 前 10 名的评分信息，生成柱状图' },
    { emoji: '📄', text: '生成一份杭州西湖一日游攻略的网页文档' },
]
</script>

<style scoped>
.welcome {
    flex: 1;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 32px 24px 8px;
}

.welcome-hero {
    text-align: center;
    max-width: 640px;
}

.hero-logo {
    font-size: 52px;
    line-height: 1;
    margin-bottom: 16px;
    animation: float 3s ease-in-out infinite;
}

@keyframes float {

    0%,
    100% {
        transform: translateY(0);
    }

    50% {
        transform: translateY(-6px);
    }
}

.hero-title {
    font-size: 26px;
    font-weight: 700;
    margin: 0 0 10px;
    background: var(--bg-chat-user);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
}

.hero-sub {
    font-size: 14px;
    color: var(--text-secondary);
    line-height: 1.8;
    margin: 0;
}

/* ---- 功能卡片 ---- */
.feature-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 14px;
    width: min(720px, 100%);
    margin: 28px 0 8px;
}

@media (max-width: 560px) {
    .feature-grid {
        grid-template-columns: 1fr;
    }
}

.feature-card {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    padding: 16px;
    box-shadow: var(--shadow-card);
    transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}

.feature-card:hover {
    transform: translateY(-3px);
    border-color: var(--primary);
    box-shadow: var(--shadow-float);
}

.feature-icon {
    font-size: 24px;
    margin-bottom: 8px;
}

.feature-name {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 4px;
}

.feature-desc {
    font-size: 12.5px;
    color: var(--text-secondary);
    line-height: 1.7;
}

/* ---- 示例任务 ---- */
.examples {
    width: min(720px, 100%);
    margin-top: 18px;
    text-align: center;
}

.examples-title {
    font-size: 12.5px;
    color: var(--text-tertiary);
    margin-bottom: 12px;
    user-select: none;
}

.example-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    justify-content: center;
}

.example-chip {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    color: var(--text-primary);
    font-size: 13px;
    border-radius: var(--radius-full);
    padding: 8px 16px;
    cursor: pointer;
    transition: border-color 0.2s, background 0.2s, transform 0.15s;
}

.example-chip:hover {
    border-color: var(--primary);
    background: var(--primary-light);
    transform: translateY(-2px);
}

.chip-emoji {
    font-size: 14px;
}
</style>
