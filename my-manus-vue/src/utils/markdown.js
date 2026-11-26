// markdown 渲染与代码高亮（全局单例）
import markdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/common'

// html:false 防止 XSS；代码块用 highlight.js 高亮，语言未知时自动转义
const md = markdownIt({
    html: false,
    linkify: true,
    breaks: true,
    highlight(str, lang) {
        if (lang && hljs.getLanguage(lang)) {
            try {
                return `<pre><code class="hljs">${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`
            } catch { /* 高亮失败降级为转义文本 */ }
        }
        return `<pre><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`
    },
})

// 结果气泡与产物预览共用同一个渲染器
export function renderMarkdown(text) {
    return md.render(text ?? '')
}
