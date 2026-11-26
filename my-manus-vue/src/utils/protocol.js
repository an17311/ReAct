/**
 * 与后端 DialogMessageDTO 对应的消息协议解析层。
 * <p>eventType 优先；缺失时（连接旧版后端）按旧文本协议回退解析：
 * [系统] 前缀 → 系统事件；[Agent名] 前缀 → 进度；带链接 → 产物；其余纯文本 → 最终结果。</p>
 */

// 与后端常量一一对应的事件类型
export const EVENT = {
    LIFECYCLE_START: 'lifecycle_start',
    LIFECYCLE_END: 'lifecycle_end',
    PROGRESS: 'progress',
    RESULT: 'result',
    ARTIFACT: 'artifact',
    SYSTEM_INFO: 'system_info',
    SYSTEM_ERROR: 'system_error',
    CANCELLED: 'cancelled',
    PLAN_UPDATE: 'plan_update',
}

// Agent 名称 → 展示信息（中文名 + emoji 图标）
const AGENT_META = {
    ReActPlanningAgent: { label: '任务规划', emoji: '🧠' },
    ReActBrowserAgent: { label: '网页浏览', emoji: '🌐' },
    AMAPAgent: { label: '地图查询', emoji: '🗺️' },
    ChartAgent: { label: '图表生成', emoji: '📊' },
    TableAgent: { label: '表格整理', emoji: '📑' },
    HtmlDocAgent: { label: '文档生成', emoji: '📄' },
}

export function agentMeta(name) {
    return AGENT_META[name] || { label: name || '智能体', emoji: '🤖' }
}

// 产物类型 → 展示信息
const ARTIFACT_META = {
    chart: { label: '图表', emoji: '📊' },
    table: { label: '表格', emoji: '📑' },
    doc: { label: '文档', emoji: '📄' },
    file: { label: '文件', emoji: '📎' },
}

export function artifactMeta(type) {
    return ARTIFACT_META[type] || ARTIFACT_META.file
}

const AGENT_PREFIX_RE = /^\[([A-Za-z][A-Za-z0-9_]*)\]/
const SYSTEM_PREFIX = '[系统]'

let seq = 0

// 从文本中提取 [Agent名] 前缀的 Agent 名称
function extractAgentName(text) {
    const m = text.match(AGENT_PREFIX_RE)
    return m ? m[1] : null
}

// 去掉文本中的 [Agent名] / [系统] 前缀，仅保留正文
export function stripPrefix(text) {
    return (text || '').replace(/^\[(?:[A-Za-z][A-Za-z0-9_]*|系统)\]/, '')
}

// 推断产物类型（回退场景：旧后端不传 artifactType）
function guessArtifactType(agentName) {
    if (!agentName) return 'file'
    if (agentName.includes('Chart')) return 'chart'
    if (agentName.includes('Table')) return 'table'
    if (agentName.includes('HtmlDoc')) return 'doc'
    return 'file'
}

/**
 * 归一化一条服务端下行消息：补充 id/time，eventType 缺失时按旧文本协议回退解析。
 */
export function normalizeMessage(raw) {
    const msg = { ...raw, id: ++seq, time: Date.now() }
    const text = msg.text || ''

    if (msg.eventType) {
        return msg
    }

    if (msg.openUrl || msg.fileUrl || msg.imageUrl) {
        // 携带产物链接 → 产物事件
        msg.eventType = EVENT.ARTIFACT
        msg.agentName = msg.agentName || extractAgentName(text)
        msg.artifactType = msg.artifactType || guessArtifactType(msg.agentName)
    } else if (text.startsWith(SYSTEM_PREFIX)) {
        // [系统] 前缀 → 取消确认 / 系统错误 / 系统提示
        const body = text.slice(SYSTEM_PREFIX.length)
        if (body.includes('已被取消')) {
            msg.eventType = EVENT.CANCELLED
        } else if (/异常|失败|错误/.test(body)) {
            msg.eventType = EVENT.SYSTEM_ERROR
        } else {
            msg.eventType = EVENT.SYSTEM_INFO
        }
    } else {
        const name = extractAgentName(text)
        if (name) {
            msg.eventType = EVENT.PROGRESS
            msg.agentName = name
        } else if (/^任务开始执行/.test(text)) {
            msg.eventType = EVENT.LIFECYCLE_START
        } else if (text === '任务流程结束') {
            msg.eventType = EVENT.LIFECYCLE_END
        } else if (text) {
            // 无前缀纯文本视为最终结果
            msg.eventType = EVENT.RESULT
        }
    }
    return msg
}
