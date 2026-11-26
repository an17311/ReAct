// 任务状态机：管理消息流渲染列表、当前任务卡片（计划/进度/产物）、输入锁
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { EVENT, normalizeMessage, agentMeta, stripPrefix } from '@/utils/protocol'

let uid = 0

export function useAgentTask() {
    const state = reactive({
        // 渲染列表，元素 kind：user 用户消息 / task 任务卡片 / result 结果 / system 系统提示
        items: [],
        // 进行中的任务卡片对象（也存在于 items 中，便于事件就地更新）
        currentTask: null,
        // 输入锁：服务端通过 meta.serverStatusHint 控制（1 执行中禁用 / 0 恢复）
        inputLocked: false,
    })

    // 获取当前任务卡片，不存在则创建并挂入渲染列表
    function ensureTask() {
        if (!state.currentTask) {
            const task = reactive({
                kind: 'task',
                id: ++uid,
                prompt: '',
                status: 'running',     // running / done / cancelled / failed
                statusText: '正在启动任务…',
                plan: [],              // 子任务链：{agent, subTask, status}
                steps: [],             // 思考步骤：{id, agentName, text}
                artifacts: [],         // 产物：{id, openUrl, fileUrl, imageUrl, agentName, artifactType, text}
                startedAt: Date.now(),
            })
            state.currentTask = task
            state.items.push(task)
        }
        return state.currentTask
    }

    /** 提交任务：渲染用户消息 + 创建任务卡片 + 发布到后端 */
    function submitTask(text, publish) {
        state.items.push({ kind: 'user', id: ++uid, text, time: Date.now() })
        const task = ensureTask()
        task.prompt = text
        task.statusText = '正在连接智能体…'
        publish('/app/enhanced-dialog', { type: 'user', text })
    }

    /** 请求取消当前任务 */
    function requestCancel(publish) {
        publish('/app/cancel', { type: 'user', text: '__cancel__' })
    }

    /** 服务端下行消息统一入口（raw 为原始 JSON 对象） */
    function handleServerMessage(raw) {
        const msg = normalizeMessage(raw)

        // 状态提示只控制输入锁，不参与渲染（结束信号是两条消息：文本 + 仅 meta）
        if (raw.meta?.serverStatusHint === 1) {
            state.inputLocked = true
        } else if (raw.meta?.serverStatusHint === 0) {
            state.inputLocked = false
        }

        switch (msg.eventType) {
            case EVENT.LIFECYCLE_START: {
                const task = ensureTask()
                task.status = 'running'
                task.statusText = '正在规划任务…'
                break
            }
            case EVENT.PLAN_UPDATE: {
                const task = ensureTask()
                task.plan = msg.plan || []
                break
            }
            case EVENT.PROGRESS: {
                const task = ensureTask()
                task.steps.push({ id: ++uid, agentName: msg.agentName, text: stripPrefix(msg.text) })
                const meta = agentMeta(msg.agentName)
                task.statusText = `${meta.emoji} ${meta.label}中…`
                break
            }
            case EVENT.ARTIFACT: {
                const task = ensureTask()
                task.artifacts.push({
                    id: ++uid,
                    openUrl: msg.openUrl,
                    fileUrl: msg.fileUrl,
                    imageUrl: msg.imageUrl,
                    agentName: msg.agentName,
                    artifactType: msg.artifactType,
                    text: stripPrefix(msg.text),
                })
                break
            }
            case EVENT.RESULT: {
                state.items.push({ kind: 'result', id: ++uid, text: msg.text, time: Date.now() })
                break
            }
            case EVENT.SYSTEM_INFO: {
                state.items.push({ kind: 'system', id: ++uid, text: msg.text })
                break
            }
            case EVENT.SYSTEM_ERROR: {
                state.items.push({ kind: 'system', id: ++uid, text: msg.text, error: true })
                ElMessage.error({ message: stripPrefix(msg.text), grouping: true })
                if (state.currentTask) state.currentTask.status = 'failed'
                break
            }
            case EVENT.CANCELLED: {
                state.items.push({ kind: 'system', id: ++uid, text: msg.text })
                if (state.currentTask) state.currentTask.status = 'cancelled'
                break
            }
            case EVENT.LIFECYCLE_END: {
                if (state.currentTask && state.currentTask.status === 'running') {
                    state.currentTask.status = 'done'
                }
                state.currentTask = null
                break
            }
            default:
                break
        }
    }

    /** 新建任务视图：清空渲染列表（历史本次不做持久化，仅保留当前会话） */
    function reset() {
        state.items = []
        state.currentTask = null
        state.inputLocked = false
    }

    return { state, submitTask, requestCancel, handleServerMessage, reset }
}
