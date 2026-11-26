<template>
    <div class="chat-input-wrap">
        <div class="chat-input-box">
            <el-input ref="inputRef" v-model="draft" type="textarea" :autosize="{ minRows: 1, maxRows: 6 }"
                :placeholder="placeholder" resize="none" @keydown.enter.exact.prevent="send" />
            <!-- 执行中：显示停止按钮（与 Send 的禁用状态互补） -->
            <button v-if="locked" class="round-btn stop" title="停止任务" @click="$emit('cancel')">
                <el-icon :size="18">
                    <VideoPause />
                </el-icon>
            </button>
            <button v-else class="round-btn send" :disabled="!draft.trim() || connNotOk" title="发送 (Enter)"
                @click="send">
                <el-icon :size="18">
                    <Promotion />
                </el-icon>
            </button>
        </div>
        <div class="input-hint">
            <template v-if="locked">任务执行中，可点击左侧按钮停止；完成后自动恢复输入</template>
            <template v-else-if="connNotOk">连接已断开，正在自动重连…</template>
            <template v-else>Enter 发送 · Shift + Enter 换行 · 任务由 AI 自动规划执行，通常需要数十秒</template>
        </div>
    </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Promotion, VideoPause } from '@element-plus/icons-vue'

const props = defineProps({
    modelValue: { type: String, default: '' },
    locked: { type: Boolean, default: false },
    // useStomp 的连接状态字符串
    conn: { type: String, default: 'connecting' },
})

const emit = defineEmits(['update:modelValue', 'submit', 'cancel'])

const draft = computed({
    get: () => props.modelValue,
    set: (v) => emit('update:modelValue', v),
})

const connNotOk = computed(() => props.conn !== 'connected')
const placeholder = computed(() =>
    connNotOk.value ? '等待连接…' : '输入你想完成的任务，例如：帮我整理近期 AI 新闻热点…')

const inputRef = ref(null)

function send() {
    const text = draft.value.trim()
    if (!text || props.locked || connNotOk.value) return
    emit('submit', text)
}

// 供欢迎屏示例任务点击后聚焦输入框
function focus() {
    inputRef.value?.focus?.()
}

defineExpose({ focus })
</script>

<style scoped>
.chat-input-wrap {
    flex: none;
    width: min(760px, 100%);
    margin: 0 auto;
    padding: 12px 20px 14px;
}

.chat-input-box {
    display: flex;
    align-items: flex-end;
    gap: 10px;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 22px;
    padding: 8px 10px 8px 18px;
    box-shadow: var(--shadow-float);
    transition: border-color 0.2s;
}

.chat-input-box:focus-within {
    border-color: var(--primary);
}

.chat-input-box :deep(.el-textarea__inner) {
    background: transparent;
    color: var(--text-primary);
    font-size: 14.5px;
    padding: 6px 0;
    --el-input-border-color: transparent;
    box-shadow: none !important;
}

.round-btn {
    flex: none;
    width: 38px;
    height: 38px;
    border: none;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: transform 0.15s, opacity 0.15s;
}

.round-btn:active {
    transform: scale(0.94);
}

.round-btn.send {
    background: var(--bg-chat-user);
    color: var(--text-on-primary);
}

.round-btn.send:disabled {
    opacity: 0.45;
    cursor: not-allowed;
}

.round-btn.stop {
    background: var(--danger);
    color: #fff;
}

.input-hint {
    text-align: center;
    font-size: 12px;
    color: var(--text-tertiary);
    margin-top: 8px;
    user-select: none;
}
</style>
