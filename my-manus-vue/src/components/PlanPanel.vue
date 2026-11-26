<template>
    <div class="plan-panel">
        <div class="plan-title">
            <el-icon>
                <List />
            </el-icon>
            <span>执行计划</span>
            <span class="plan-progress">{{ doneCount }}/{{ plan.length }}</span>
        </div>
        <div class="plan-steps">
            <div v-for="(step, i) in plan" :key="i" class="plan-step" :class="'is-' + (step.status || 'PENDING').toLowerCase()">
                <span class="step-icon">
                    <el-icon v-if="step.status === 'SUCCESS'" class="icon-success">
                        <CircleCheckFilled />
                    </el-icon>
                    <el-icon v-else-if="step.status === 'FAILED'" class="icon-failed">
                        <CircleCloseFilled />
                    </el-icon>
                    <el-icon v-else-if="step.status === 'RUNNING'" class="icon-running spin">
                        <Loading />
                    </el-icon>
                    <span v-else class="step-dot"></span>
                </span>
                <span class="step-text">{{ step.subTask }}</span>
                <span class="step-agent">{{ agentMeta(step.agent).label }}</span>
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { List, CircleCheckFilled, CircleCloseFilled, Loading } from '@element-plus/icons-vue'
import { agentMeta } from '@/utils/protocol'

const props = defineProps({
    plan: { type: Array, default: () => [] },
})

const doneCount = computed(() => props.plan.filter(s => s.status === 'SUCCESS').length)
</script>

<style scoped>
.plan-panel {
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    background: var(--primary-light);
    padding: 12px 14px;
}

.plan-title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    color: var(--primary);
    margin-bottom: 8px;
}

.plan-progress {
    margin-left: auto;
    font-weight: 500;
    color: var(--text-secondary);
    font-variant-numeric: tabular-nums;
}

.plan-steps {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.plan-step {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    font-size: 13px;
    line-height: 1.5;
}

.step-icon {
    flex: none;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    margin-top: 1px;
}

.icon-success {
    color: var(--success);
}

.icon-failed {
    color: var(--danger);
}

.icon-running {
    color: var(--primary);
}

.step-dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    border: 1.5px dashed var(--text-tertiary);
    display: inline-block;
}

.step-text {
    flex: 1;
    color: var(--text-primary);
}

.is-pending .step-text {
    color: var(--text-tertiary);
}

.is-failed .step-text {
    color: var(--danger);
    text-decoration: line-through;
    text-decoration-color: rgba(239, 68, 68, 0.5);
}

.step-agent {
    flex: none;
    font-size: 11px;
    color: var(--text-secondary);
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-full);
    padding: 1px 8px;
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
