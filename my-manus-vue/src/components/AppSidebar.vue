<template>
    <aside class="sidebar">
        <div class="brand">
            <div class="brand-logo">🤖</div>
            <div class="brand-name">Manus 智能助手</div>
        </div>

        <el-button class="new-task-btn" type="primary" round :disabled="running" @click="$emit('new-task')">
            <el-icon>
                <Plus />
            </el-icon>
            <span class="new-task-text">新建任务</span>
        </el-button>

        <!-- 历史任务占位：二期接入后端持久化 -->
        <el-tooltip content="历史任务功能开发中，敬请期待" placement="right">
            <div class="history-entry">
                <el-icon>
                    <Clock />
                </el-icon>
                <span class="new-task-text">历史任务</span>
            </div>
        </el-tooltip>

        <div class="sidebar-footer">
            <button class="side-icon-btn" title="使用帮助" @click="helpVisible = true">
                <el-icon>
                    <QuestionFilled />
                </el-icon>
            </button>
            <button class="side-icon-btn" :title="isDark ? '切换为浅色模式' : '切换为深色模式'" @click="$emit('toggle-dark')">
                <el-icon>
                    <Sunny v-if="isDark" />
                    <Moon v-else />
                </el-icon>
            </button>
        </div>

        <!-- 帮助弹窗 -->
        <el-dialog v-model="helpVisible" title="使用帮助" width="min(560px, 92%)">
            <div class="help-body">
                <h4>我能做什么？</h4>
                <p>只需用一句话描述目标，我会自动拆解执行计划，调度多个专项智能体协作完成：</p>
                <ul>
                    <li>🌐 <b>网页浏览</b>：操作真实浏览器搜索、点击、提取网页内容</li>
                    <li>🗺️ <b>地图查询</b>：地点搜索、路线规划（高德地图）</li>
                    <li>📊 <b>图表生成</b>：生成可交互、可下载的 HTML 图表</li>
                    <li>📑 <b>表格 / 文档</b>：可导出 Excel 的表格、排版精美的网页文档</li>
                </ul>
                <h4>使用提示</h4>
                <ul>
                    <li>任务执行中可以随时点击输入框旁的 <b>停止按钮</b> 取消当前任务</li>
                    <li>执行过程可在任务卡片的「执行过程」中展开查看每一步思考</li>
                    <li>生成的图表 / 表格 / 文档会以卡片形式出现，支持<b>在线预览</b>与<b>下载</b></li>
                    <li>浏览器类任务会在服务端机器弹出真实浏览器窗口；如目标网站需要登录，请在服务端机器上手动完成登录</li>
                </ul>
            </div>
        </el-dialog>
    </aside>
</template>

<script setup>
import { ref } from 'vue'
import { Plus, Clock, QuestionFilled, Sunny, Moon } from '@element-plus/icons-vue'

defineProps({
    running: { type: Boolean, default: false },
    isDark: { type: Boolean, default: false },
})

defineEmits(['new-task', 'toggle-dark'])

const helpVisible = ref(false)
</script>

<style scoped>
.sidebar {
    flex: none;
    width: 232px;
    display: flex;
    flex-direction: column;
    padding: 18px 14px;
    background: var(--bg-sidebar);
    border-right: 1px solid var(--border-color);
}

.brand {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 2px 6px 16px;
}

.brand-logo {
    flex: none;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    background: var(--primary-light);
    border-radius: 10px;
}

.brand-name {
    font-size: 15px;
    font-weight: 700;
}

.new-task-btn {
    width: 100%;
}

/* 历史任务占位入口 */
.history-entry {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
    padding: 8px 12px;
    border-radius: var(--radius-full);
    font-size: 14px;
    color: var(--text-tertiary);
    cursor: not-allowed;
    user-select: none;
}

.sidebar-footer {
    margin-top: auto;
    display: flex;
    gap: 8px;
    padding-top: 12px;
}

.side-icon-btn {
    width: 34px;
    height: 34px;
    border: 1px solid var(--border-color);
    background: var(--bg-card);
    color: var(--text-secondary);
    border-radius: 10px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    transition: color 0.2s, border-color 0.2s;
}

.side-icon-btn:hover {
    color: var(--primary);
    border-color: var(--primary);
}

/* ---- 帮助弹窗内容 ---- */
.help-body {
    font-size: 13.5px;
    line-height: 1.8;
    color: var(--text-secondary);
}

.help-body h4 {
    color: var(--text-primary);
    margin: 14px 0 6px;
}

.help-body ul {
    padding-left: 1.3em;
    margin: 4px 0;
}

/* ---- 移动端：侧栏收窄为图标栏 ---- */
@media (max-width: 768px) {
    .sidebar {
        width: 60px;
        padding: 14px 8px;
        align-items: center;
    }

    .brand {
        padding-bottom: 12px;
    }

    .brand-name,
    .new-task-text {
        display: none;
    }

    .new-task-btn {
        width: 36px;
        padding: 8px;
    }

    .history-entry {
        padding: 8px;
    }
}
</style>
