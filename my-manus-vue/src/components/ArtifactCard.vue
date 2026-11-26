<template>
    <div class="artifact-card">
        <span class="artifact-emoji">{{ meta.emoji }}</span>
        <div class="artifact-info">
            <div class="artifact-title">{{ meta.label }}已生成</div>
            <div class="artifact-sub">{{ agentLabel }}</div>
        </div>
        <div class="artifact-actions">
            <el-button v-if="artifact.openUrl" size="small" type="primary" plain round @click="previewVisible = true">
                <el-icon style="margin-right: 4px">
                    <View />
                </el-icon>
                在线预览
            </el-button>
            <a v-if="artifact.fileUrl" :href="downloadHref" :download="downloadName" class="download-link">
                <el-button size="small" round>
                    <el-icon style="margin-right: 4px">
                        <Download />
                    </el-icon>
                    下载
                </el-button>
            </a>
            <a v-if="artifact.openUrl" :href="artifact.openUrl" target="_blank" class="download-link">
                <el-button size="small" text round>新窗口打开</el-button>
            </a>
        </div>

        <!-- 内嵌预览弹窗：直接通过 /content/open/{uuid} 回流文件内容 -->
        <el-dialog v-model="previewVisible" :title="meta.label + '预览'" width="min(960px, 92%)" destroy-on-close>
            <iframe v-if="artifact.openUrl" :src="artifact.openUrl" class="preview-frame"
                :title="meta.label + '预览'"></iframe>
        </el-dialog>
    </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { View, Download } from '@element-plus/icons-vue'
import { agentMeta, artifactMeta } from '@/utils/protocol'

const props = defineProps({
    artifact: { type: Object, required: true },
})

const previewVisible = ref(false)

const meta = computed(() => artifactMeta(props.artifact.artifactType))
const agentLabel = computed(() =>
    props.artifact.agentName ? `由 ${agentMeta(props.artifact.agentName).label}智能体生成` : '由智能体生成')
const downloadHref = computed(() => {
    // 下载接口支持 ?name= 自定义文件名，按产物类型给出更友好的默认名
    const url = props.artifact.fileUrl
    if (!url) return url
    const ext = props.artifact.artifactType === 'file' ? 'html' : props.artifact.artifactType
    const sep = url.includes('?') ? '&' : '?'
    return `${url}${sep}name=${meta.value.label}_${new Date().toISOString().slice(0, 10)}.${ext}`
})
const downloadName = computed(() => `${meta.value.label}.html`)
</script>

<style scoped>
.artifact-card {
    display: flex;
    align-items: center;
    gap: 12px;
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    background: var(--bg-card);
    padding: 12px 14px;
    box-shadow: var(--shadow-card);
}

.artifact-emoji {
    flex: none;
    width: 40px;
    height: 40px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    background: var(--primary-light);
    border-radius: var(--radius-sm);
}

.artifact-info {
    flex: 1;
    min-width: 0;
}

.artifact-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
}

.artifact-sub {
    font-size: 12px;
    color: var(--text-secondary);
    margin-top: 2px;
}

.artifact-actions {
    flex: none;
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    justify-content: flex-end;
}

.download-link {
    text-decoration: none;
}

.preview-frame {
    width: 100%;
    height: 70vh;
    border: 0;
    border-radius: var(--radius-sm);
    background: #fff;
}
</style>
