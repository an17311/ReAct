<template>
    <div class="message-item" :class="'kind-' + item.kind">
        <!-- 用户消息：右侧渐变气泡 -->
        <template v-if="item.kind === 'user'">
            <div class="user-bubble">{{ item.text }}</div>
        </template>

        <!-- 系统提示：居中提示条 -->
        <template v-else-if="item.kind === 'system'">
            <div class="system-bar" :class="{ 'is-error': item.error }">
                <el-icon>
                    <WarningFilled v-if="item.error" />
                    <InfoFilled v-else />
                </el-icon>
                <span>{{ stripPrefix(item.text) }}</span>
            </div>
        </template>

        <!-- 助手结果：markdown 渲染 -->
        <template v-else-if="item.kind === 'result'">
            <div class="assistant-row">
                <div class="avatar">M</div>
                <div class="result-body md-body" v-html="renderedHtml"></div>
            </div>
        </template>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { WarningFilled, InfoFilled } from '@element-plus/icons-vue'
import { renderMarkdown } from '@/utils/markdown'
import { stripPrefix } from '@/utils/protocol'

const props = defineProps({
    item: { type: Object, required: true },
})

const renderedHtml = computed(() => (props.item.kind === 'result' ? renderMarkdown(props.item.text) : ''))
</script>

<style scoped>
.message-item {
    margin: 14px 0;
}

/* ---- 用户气泡 ---- */
.kind-user {
    display: flex;
    justify-content: flex-end;
}

.user-bubble {
    max-width: 78%;
    background: var(--bg-chat-user);
    color: var(--text-on-primary);
    padding: 10px 16px;
    border-radius: 18px 18px 4px 18px;
    line-height: 1.6;
    font-size: 14.5px;
    white-space: pre-wrap;
    word-break: break-word;
    box-shadow: var(--shadow-card);
}

/* ---- 系统提示条 ---- */
.kind-system {
    display: flex;
    justify-content: center;
}

.system-bar {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    max-width: 90%;
    font-size: 12.5px;
    color: var(--text-secondary);
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-full);
    padding: 5px 14px;
}

.system-bar.is-error {
    color: var(--danger);
    border-color: color-mix(in srgb, var(--danger) 35%, transparent);
    background: color-mix(in srgb, var(--danger) 8%, var(--bg-card));
}

/* ---- 结果气泡 ---- */
.assistant-row {
    display: flex;
    gap: 10px;
    align-items: flex-start;
}

.avatar {
    flex: none;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: var(--bg-chat-user);
    color: var(--text-on-primary);
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 15px;
}

.result-body {
    flex: 1;
    min-width: 0;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 4px 18px 18px 18px;
    padding: 12px 16px;
    box-shadow: var(--shadow-card);
}
</style>
