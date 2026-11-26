// STOMP over WebSocket 连接管理：连接、自动重连、订阅下行队列、发布上行消息
import { ref } from 'vue'
import { Client } from '@stomp/stompjs'

export const CONN_STATE = {
    CONNECTING: 'connecting',     // 首次连接中
    CONNECTED: 'connected',       // 已连接
    RECONNECTING: 'reconnecting', // 断线自动重连中
    CLOSED: 'closed',             // 已主动断开
}

// 计算 broker 地址：优先环境变量 VITE_WS_URL；默认同源相对路径（开发环境经 vite proxy 转发）
function brokerUrl() {
    if (import.meta.env.VITE_WS_URL) {
        return import.meta.env.VITE_WS_URL
    }
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${proto}://${window.location.host}/bs-dialog-websocket`
}

/**
 * @param {(rawMessage: object) => void} onMessage 下行消息回调（已 JSON.parse，调用方负责 normalizeMessage）
 */
export function useStomp(onMessage) {
    const state = ref(CONN_STATE.CONNECTING)
    let client = null

    function connect() {
        client = new Client({
            brokerURL: brokerUrl(),
            // 断线后 3 秒自动重连
            reconnectDelay: 3000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            onConnect: () => {
                state.value = CONN_STATE.CONNECTED
                // 订阅用户专属下行队列（重连成功后会再次触发，需重新订阅）
                client.subscribe('/user/queue/dialog', (message) => {
                    try {
                        onMessage(JSON.parse(message.body))
                    } catch (e) {
                        // 单条消息解析失败不应中断会话
                        console.error('下行消息解析失败:', e, message.body)
                    }
                })
            },
            onWebSocketClose: () => {
                // 非主动断开时标记为重连中（stompjs 会按 reconnectDelay 自动重连）
                if (state.value !== CONN_STATE.CLOSED) {
                    state.value = CONN_STATE.RECONNECTING
                }
            },
            onWebSocketError: (error) => {
                console.error('WebSocket 连接错误:', error)
            },
            onStompError: (frame) => {
                console.error('STOMP 协议错误:', frame.headers['message'], frame.body)
            },
        })
        client.activate()
    }

    /** 发布上行消息（未连接时静默丢弃，UI 层由输入锁与连接状态提示兜底） */
    function publish(destination, body) {
        if (client && client.connected) {
            client.publish({ destination, body: JSON.stringify(body) })
        }
    }

    /** 主动断开（页面卸载时调用） */
    function disconnect() {
        state.value = CONN_STATE.CLOSED
        client?.deactivate()
    }

    return { state, connect, publish, disconnect }
}
