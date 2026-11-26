<template>
    <div class="app-shell">
        <AppSidebar :running="isRunning" :is-dark="isDark" @new-task="onNewTask" @toggle-dark="toggleDark" />

        <main class="main-area">
            <!-- 连接状态横幅 -->
            <div v-if="connState !== 'connected'" class="conn-banner">
                <el-icon class="spin">
                    <Loading />
                </el-icon>
                {{ connState === 'connecting' ? '正在连接服务…' : '连接已断开，正在自动重连…' }}
            </div>

            <!-- 空态：欢迎屏（功能介绍 + 示例任务） -->
            <WelcomeScreen v-if="taskState.items.length === 0" @use-prompt="onUsePrompt" />

            <!-- 对话态 -->
            <div v-else ref="scrollEl" class="chat-scroll">
                <div class="chat-inner">
                    <template v-for="item in taskState.items" :key="item.id">
                        <TaskCard v-if="item.kind === 'task'" :task="item" />
                        <MessageItem v-else :item="item" />
                    </template>
                </div>
            </div>

            <!-- 底部输入区 -->
            <ChatInput ref="chatInputRef" v-model="draft" :locked="taskState.inputLocked" :conn="connState"
                @submit="onSubmit" @cancel="onCancel" />
        </main>
    </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { useStomp } from '@/composables/useStomp'
import { useAgentTask } from '@/composables/useAgentTask'
import AppSidebar from '@/components/AppSidebar.vue'
import WelcomeScreen from '@/components/WelcomeScreen.vue'
import TaskCard from '@/components/TaskCard.vue'
import MessageItem from '@/components/MessageItem.vue'
import ChatInput from '@/components/ChatInput.vue'

// ---------- 主题（暗色切换 + 记忆） ----------
const isDark = ref(localStorage.getItem('manus-theme')
    ? localStorage.getItem('manus-theme') === 'dark'
    : window.matchMedia('(prefers-color-scheme: dark)').matches)

function applyTheme() {
    document.documentElement.classList.toggle('dark', isDark.value)
}

function toggleDark() {
    isDark.value = !isDark.value
    localStorage.setItem('manus-theme', isDark.value ? 'dark' : 'light')
    applyTheme()
}

// ---------- 连接与任务 ----------
const { state: taskState, submitTask, requestCancel, handleServerMessage, reset } = useAgentTask()
const { state: connState, connect, publish, disconnect } = useStomp(handleServerMessage)

onMounted(() => {
    applyTheme()
    connect()
})

onBeforeUnmount(disconnect)

const draft = ref('')
const chatInputRef = ref(null)
const scrollEl = ref(null)

// 任务执行中：以服务端输入锁为准，任务卡片 running 状态兜底（提交后、生命周期事件到达前）
const isRunning = computed(() =>
    taskState.inputLocked || (taskState.currentTask && taskState.currentTask.status === 'running'))

// 消息/进度更新后自动滚动到底部
watch(() => taskState.items, () => {
    nextTick(() => {
        if (scrollEl.value) {
            scrollEl.value.scrollTop = scrollEl.value.scrollHeight
        }
    })
}, { deep: true })

function onSubmit(text) {
    if (isRunning.value) return
    submitTask(text, publish)
    draft.value = ''
}

function onCancel() {
    requestCancel(publish)
}

// 欢迎屏示例任务：填入输入框并聚焦，由用户确认后发送
function onUsePrompt(text) {
    draft.value = text
    nextTick(() => chatInputRef.value?.focus())
}

function onNewTask() {
    reset()
}
</script>

<style scoped>
.app-shell {
    display: flex;
    height: 100%;
}

.main-area {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    position: relative;
}

/* ---- 连接横幅 ---- */
.conn-banner {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    font-size: 13px;
    color: var(--warning);
    background: color-mix(in srgb, var(--warning) 10%, var(--bg-page));
    border-bottom: 1px solid color-mix(in srgb, var(--warning) 30%, transparent);
    padding: 8px 16px;
}

/* ---- 对话滚动区 ---- */
.chat-scroll {
    flex: 1;
    overflow-y: auto;
    padding: 20px 20px 0;
}

.chat-inner {
    width: min(760px, 100%);
    margin: 0 auto;
    padding-bottom: 12px;
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
