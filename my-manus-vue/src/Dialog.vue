<template>
    <div class="main-page">
        <div class="messages">
            <div v-for="(message, index) in messages" :key="index" :class="message.type">
                <strong>{{ message.type === 'user' ? 'User' : 'Server' }}:</strong>
                <span>{{ message.text }}
                    <a v-if="message.imageUrl" :href="message.imageUrl" target="_blank"><br /><img
                            :src="message.imageUrl" /></a>
                    <a v-if="message.fileUrl" :href="message.fileUrl" target="_blank"><br />👇download👇</a>
                    <a v-if="message.openUrl" :href="message.openUrl" target="_blank"><br />在线预览</a>
                </span>
            </div>
        </div>
        <div class="message-input">
            <input type="text" v-model="newMessage" placeholder="Type your message..." @keyup.enter="sendMessage" />
            <button @click="sendMessage" :disabled="disableInput">Send</button>
        </div>
    </div>
</template>

<script>
// 引入 STOMP 客户端，用于通过 WebSocket 与服务端进行基于帧（frame）的消息收发
import { Client } from '@stomp/stompjs';

export default {
    data() {
        return {
            // 是否禁用输入框（服务端处理任务期间置为 true，防止重复提交）
            disableInput: false,
            // 对话框是否可见
            isVisible: true,
            // 消息列表，元素包含 type（user/server）、text 及可选的链接字段
            messages: [
                { type: 'user', text: 'Hello, server!' },
                { type: 'server', text: 'Hello, user!' },
            ],
            // 输入框当前绑定的文本
            newMessage: '',
            // STOMP 客户端实例
            stompClient: null
        };
    },
    methods: {
        // 发送用户消息：本地追加展示 + 通过 STOMP 发布到服务端 /app/enhanced-dialog
        sendMessage() {
            if (this.newMessage.trim()) {
                // 1. 构造用户消息并立即渲染到界面
                var msg = { type: 'user', text: this.newMessage };
                this.messages.push(msg);
                // 2. 清空输入框
                this.newMessage = '';
                // 3. 发布到后端 @MessageMapping("/enhanced-dialog")，目的地前缀 /app 对应服务端应用路由前缀
                this.stompClient.publish({ destination: '/app/enhanced-dialog', body: JSON.stringify(msg) });
                // 4. 滚动到底部展示最新消息
                this.scrollToBottom();
            }
        },
        // 关闭对话框
        closeDialog() {
            this.isVisible = false;
        },
        // 处理服务端下行消息：解析 JSON、渲染内容，并根据 serverStatusHint 控制输入框可用状态
        handleMessage(playload) {
            const message = JSON.parse(playload.body);
            // 有文本内容则追加到消息列表
            if (message.text) {
                this.messages.push(message);
                this.scrollToBottom();
            }
            // 根据服务端状态提示切换输入框禁用状态：0 可用，1 禁用
            if (message.meta) {
                if (message.meta.serverStatusHint == 0) {
                    this.disableInput = false;
                } else if (message.meta.serverStatusHint == 1) {
                    this.disableInput = true;
                }
            }
        },
        // 将消息容器滚动到最底部，确保最新消息可见
        scrollToBottom() {
            this.$nextTick(() => {
                const messagesContainer = this.$el.querySelector('.messages');
                messagesContainer.scrollTop = messagesContainer.scrollHeight;
            });
        }
    },
    // 组件创建时建立 STOMP 连接：指定端点、注册连接/错误回调、订阅下行队列并激活连接
    created() {
        console.log("Starting connection to WebSocket Server")
        // 1. 指向后端注册的 STOMP 端点 /bs-dialog-websocket
        this.stompClient = new Client({
            brokerURL: 'ws://localhost:18081/bs-dialog-websocket'
        });
        // 2. 连接成功后订阅用户专属队列 /user/queue/dialog，接收服务端推送的消息
        this.stompClient.onConnect = (frame) => {
            console.log('Connected: ' + frame);
            this.stompClient.subscribe('/user/queue/dialog', this.handleMessage);
        };
        // 3. WebSocket 层错误回调
        this.stompClient.onWebSocketError = (error) => {
            console.error('Error with websocket', error);
        };

        // 4. STOMP 协议层错误回调
        this.stompClient.onStompError = (frame) => {
            console.error('Broker reported error: ' + frame.headers['message']);
            console.error('Additional details: ' + frame.body);
        };
        // 5. 激活连接（建立 WebSocket 并完成 STOMP 握手）
        this.stompClient.activate();
    }
};
</script>

<style scoped>
img {
    max-width: 100px;
    max-height: 100px;
    margin-left: 10px;
}

.main-page {
    display: flex;
    flex-direction: column;
    height: 90vh;
    width: 100%;
    background: #f5f5f5;
}

.messages {
    flex: 1;
    overflow-y: auto;
    padding: 10px;
    background: white;
    border-bottom: 1px solid #ddd;
}

.messages .user {
    text-align: right;
    margin: 5px 0;
}

.messages .user span {
    display: inline-block;
    background: #007bff;
    color: white;
    padding: 10px;
    border-radius: 15px;
    max-width: 70%;
    word-wrap: break-word;
}

.messages .server {
    text-align: left;
    margin: 5px 0;
}

.messages .server span {
    display: inline-block;
    background: #e5e5ea;
    color: black;
    padding: 10px;
    border-radius: 15px;
    max-width: 70%;
    word-wrap: break-word;
}

.message-input {
    display: flex;
    padding: 10px;
    background: #fff;
    border-top: 1px solid #ddd;
}

.message-input input {
    flex: 1;
    padding: 10px;
    border: 1px solid #ddd;
    border-radius: 4px;
    margin-right: 10px;
}

.message-input button {
    padding: 10px 20px;
    background: #007bff;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
}

.message-input button:hover {
    background: #0056b3;
}

.dialog {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 1000;
}

.dialog-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.5);
}

.dialog-content {
    position: relative;
    background: white;
    padding: 20px;
    border-radius: 8px;
    width: 300px;
    max-width: 90%;
    box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.messages {
    max-height: 100%;
    overflow-y: auto;
    margin-bottom: 10px;
}

.messages .user {
    text-align: right;
    color: blue;
}

.messages .server {
    text-align: left;
    color: green;
}

.dialog-actions {
    display: flex;
    gap: 10px;
}

.dialog-actions input {
    flex: 1;
    padding: 5px;
}

.dialog-actions button {
    padding: 5px 10px;
}

.main-page {
    display: flex;
    flex-direction: column;
    height: 90vh;
    width: 100%;
    background: #f5f5f5;
}

.messages {
    flex: 1;
    overflow-y: auto;
    padding: 10px;
    background: white;
    border-bottom: 1px solid #ddd;
}

.messages .user {
    text-align: right;
    color: blue;
    margin: 5px 0;
}

.messages .server {
    text-align: left;
    color: green;
    margin: 5px 0;
}

.message-input {
    display: flex;
    padding: 10px;
    background: #fff;
    border-top: 1px solid #ddd;
}

.message-input input {
    flex: 1;
    padding: 10px;
    border: 1px solid #ddd;
    border-radius: 4px;
    margin-right: 10px;
}

.message-input button {
    padding: 10px 20px;
    background: #007bff;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
}

.message-input button:hover {
    background: #0056b3;
}

.message-input button:disabled,
.message-input button[disabled] {
    border: 1px solid #999999;
    background-color: #cccccc;
    color: #666666;
}
</style>
