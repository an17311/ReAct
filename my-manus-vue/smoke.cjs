// 冒烟脚本：通过 STOMP 直连后端，验证任务链路与 P1 行为
// 用法：
//   node smoke.cjs ["任务文本"]              —— 基础模式：完整跑一个任务，验证正常链路
//   node smoke.cjs --p1 ["任务文本"]         —— P1 模式：验证会话单飞（并发消息被拒）+ 取消机制
const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');

const URL = 'ws://localhost:18081/bs-dialog-websocket';
const args = process.argv.slice(2);
const P1 = args.includes('--p1');
const TASK = args.filter(a => a !== '--p1')[0]
    || '请把以下成绩数据整理成表格：张三 85分，李四 92分，王五 78分';
const TIMEOUT_MS = 240000; // 4 分钟兜底退出

// 断言标记：收到的关键消息
const seen = {
    hint1: false,        // serverStatusHint=1（禁用输入）
    startMsg: false,     // "任务开始执行"
    runningLock: false,  // 单飞拒绝："当前已有任务在执行中"（仅 P1）
    cancelAck: false,    // 取消确认："已收到取消请求"（仅 P1）
    cancelDone: false,   // 取消终止："[系统]任务已被取消"（仅 P1）
    endSignal: false,    // "任务流程结束"
    hint0: false,        // serverStatusHint=0（恢复输入）
};

const client = new Client({
    brokerURL: URL,
    webSocketFactory: () => new WebSocket(URL),
    reconnectDelay: 0, // 冒烟不重连，失败即退
    onConnect: () => {
        console.log('[smoke] connected (mode=' + (P1 ? 'p1' : 'basic') + ')');
        client.subscribe('/user/queue/dialog', (msg) => {
            let parsed = null;
            try { parsed = JSON.parse(msg.body); } catch (e) { console.log('[recv-raw] ' + msg.body); }
            if (!parsed) return;
            if (parsed.meta && parsed.meta.serverStatusHint === 1) seen.hint1 = true;
            if (parsed.meta && parsed.meta.serverStatusHint === 0) seen.hint0 = true;
            const text = parsed.text || '(no text)';
            if (text.includes('任务开始执行')) seen.startMsg = true;
            if (text.includes('当前已有任务在执行中')) seen.runningLock = true;
            if (text.includes('已收到取消请求')) seen.cancelAck = true;
            if (text.includes('任务已被取消')) seen.cancelDone = true;
            if (text.includes('任务流程结束')) seen.endSignal = true;
            const extra = [parsed.openUrl && ('openUrl=' + parsed.openUrl), parsed.fileUrl && ('fileUrl=' + parsed.fileUrl)]
                .filter(Boolean).join(' ');
            console.log('[recv] ' + text + (extra ? '  |  ' + extra : ''));
            if (seen.endSignal && seen.hint0) {
                finish();
            }
        });

        // t+500ms 发送任务
        setTimeout(() => {
            console.log('[send] ' + TASK);
            client.publish({ destination: '/app/enhanced-dialog', body: JSON.stringify({ type: 'user', text: TASK }) });
        }, 500);

        if (P1) {
            // t+2500ms 同会话发第二条消息：预期被单飞拒绝
            setTimeout(() => {
                console.log('[send] (dup) 再来一个任务（预期被单飞拒绝）');
                client.publish({
                    destination: '/app/enhanced-dialog',
                    body: JSON.stringify({ type: 'user', text: '再来一个任务' })
                });
            }, 2500);
            // t+9000ms 发送取消请求：预期任务在最近检查点终止
            setTimeout(() => {
                console.log('[send] (cancel) 取消任务');
                client.publish({ destination: '/app/cancel', body: JSON.stringify({ type: 'user', text: '__cancel__' }) });
            }, 9000);
        }
    },
    onStompError: (f) => { console.error('[stomp-error] ' + f.headers['message'] + ' ' + f.body); process.exit(1); },
    onWebSocketError: (e) => { console.error('[ws-error] ' + e.message); process.exit(1); },
});

function finish() {
    console.log('================ smoke summary ================');
    const required = P1
        ? ['hint1', 'startMsg', 'runningLock', 'cancelAck', 'cancelDone', 'endSignal', 'hint0']
        : ['hint1', 'startMsg', 'endSignal', 'hint0'];
    let pass = true;
    for (const k of required) {
        console.log((seen[k] ? '✅' : '❌') + ' ' + k);
        if (!seen[k]) pass = false;
    }
    console.log('==============================================');
    console.log(pass ? '[smoke] PASS' : '[smoke] FAIL');
    client.deactivate().then(() => process.exit(pass ? 0 : 3));
}

const killer = setTimeout(() => {
    console.error('[smoke] TIMEOUT after ' + TIMEOUT_MS + 'ms');
    finish();
}, TIMEOUT_MS);
client.activate();
