<template>
    <div class="task-card" :class="'status-' + task.status">
        <!-- 标题行：任务内容 + 状态徽标 -->
        <div class="task-header">
            <div class="task-prompt">{{ task.prompt || '未命名任务' }}</div>
            <span class="task-status" :class="'badge-' + task.status">
                <el-icon v-if="task.status === 'running'" class="spin">
                    <Loading />
                </el-icon>
                <el-icon v-else-if="task.status === 'done'">
                    <CircleCheckFilled />
                </el-icon>
                <el-icon v-else-if="task.status === 'cancelled'">
                    <RemoveFilled />
                </el-icon>
                <el-icon v-else>
                    <CircleCloseFilled />
                </el-icon>
                {{ statusLabel }}
            </span>
        </div>

        <!-- 执行计划（由 plan_update 事件驱动） -->
        <PlanPanel v-if="task.plan.length" :plan="task.plan" class="task-plan" />

        <!-- 执行状态行 -->
        <div v-if="task.status === 'running'" class="task-status-line">
            <span class="typing-dots"><i></i><i></i><i></i></span>
            <span>{{ task.statusText }}</span>
        </div>

        <!-- 思考时间线：运行中默认展示最近几步，结束后收起为可展开的执行过程 -->
        <div v-if="task.steps.length" class="task-steps">
            <div class="steps-toggle" @click="stepsExpanded = !stepsExpanded">
                <el-icon>
                    <ArrowDown v-if="stepsExpanded || task.status === 'running'" />
                    <ArrowRight v-else />
                </el-icon>
                <span>执行过程 · {{ task.steps.length }} 步</span>
            </div>
            <div v-show="stepsExpanded || task.status === 'running'" class="steps-list">
                <div v-for="s in visibleSteps" :key="s.id" class="step-row">
                    <span class="step-agent-badge">{{ agentMeta(s.agentName).emoji }}
                        {{ agentMeta(s.agentName).label }}</span>
                    <span class="step-text">{{ s.text }}</span>
                </div>
                <div v-if="!stepsExpanded && task.status === 'running' && task.steps.length > visibleSteps.length"
                    class="steps-more" @click="stepsExpanded = true">
                    展开全部 {{ task.steps.length }} 步
                </div>
            </div>
        </div>

        <!-- 产物卡片 -->
        <div v-if="task.artifacts.length" class="task-artifacts">
            <ArtifactCard v-for="a in task.artifacts" :key="a.id" :artifact="a" />
        </div>
    </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Loading, CircleCheckFilled, CircleCloseFilled, RemoveFilled, ArrowDown, ArrowRight } from '@element-plus/icons-vue'
import PlanPanel from './PlanPanel.vue'
import ArtifactCard from './ArtifactCard.vue'
import { agentMeta } from '@/utils/protocol'

const props = defineProps({
    task: { type: Object, required: true },
})

const stepsExpanded = ref(false)

const STATUS_LABELS = {
    running: '执行中',
    done: '已完成',
    cancelled: '已取消',
    failed: '失败',
}

const statusLabel = computed(() => STATUS_LABELS[props.task.status] || props.task.status)

// 收起时只显示最近 3 步，展开后显示全部
const visibleSteps = computed(() =>
    stepsExpanded.value || props.task.status !== 'running'
        ? props.task.steps
        : props.task.steps.slice(-3))
</script>

<style scoped>
.task-card {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-lg);
    padding: 16px 18px;
    box-shadow: var(--shadow-card);
    display: flex;
    flex-direction: column;
    gap: 12px;
}

/* ---- 标题行 ---- */
.task-header {
    display: flex;
    align-items: flex-start;
    gap: 12px;
}

.task-prompt {
    flex: 1;
    font-size: 15px;
    font-weight: 600;
    line-height: 1.5;
    word-break: break-word;
}

.task-status {
    flex: none;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    font-weight: 600;
    border-radius: var(--radius-full);
    padding: 3px 12px;
}

.badge-running {
    color: var(--primary);
    background: var(--primary-light);
}

.badge-done {
    color: var(--success);
    background: color-mix(in srgb, var(--success) 12%, transparent);
}

.badge-cancelled {
    color: var(--text-secondary);
    background: color-mix(in srgb, var(--text-secondary) 12%, transparent);
}

.badge-failed {
    color: var(--danger);
    background: color-mix(in srgb, var(--danger) 12%, transparent);
}

/* ---- 执行状态行 ---- */
.task-status-line {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 13px;
    color: var(--text-secondary);
}

.typing-dots {
    display: inline-flex;
    gap: 4px;
}

.typing-dots i {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--primary);
    animation: blink 1.2s ease-in-out infinite;
}

.typing-dots i:nth-child(2) {
    animation-delay: 0.2s;
}

.typing-dots i:nth-child(3) {
    animation-delay: 0.4s;
}

@keyframes blink {

    0%,
    100% {
        opacity: 0.25;
    }

    50% {
        opacity: 1;
    }
}

/* ---- 思考时间线 ---- */
.task-steps {
    border-top: 1px dashed var(--border-color);
    padding-top: 10px;
}

.steps-toggle {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 12.5px;
    color: var(--text-secondary);
    cursor: pointer;
    user-select: none;
}

.steps-toggle:hover {
    color: var(--primary);
}

.steps-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 10px;
}

.step-row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    font-size: 12.5px;
    line-height: 1.6;
}

.step-agent-badge {
    flex: none;
    font-size: 11px;
    color: var(--primary);
    background: var(--primary-light);
    border-radius: var(--radius-full);
    padding: 1px 9px;
    white-space: nowrap;
}

.step-text {
    color: var(--text-secondary);
    word-break: break-word;
}

.steps-more {
    font-size: 12px;
    color: var(--primary);
    cursor: pointer;
    user-select: none;
}

/* ---- 产物 ---- */
.task-artifacts {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.spin {
    animation: spin 1s linear infinite;
}

@keyframes spin {
    from {
        transform: rotate(0deg);
    }

    to {
        transform: rotate(360deg);
    }
}
</style>
