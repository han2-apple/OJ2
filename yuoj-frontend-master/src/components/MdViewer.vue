<template>
  <div class="md-viewer" v-html="html"></div>
</template>

<script setup>
import { computed } from 'vue';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

marked.setOptions({ gfm: true, breaks: true });

const props = defineProps({
  content: {
    type: String,
    default: '',
  },
});

const html = computed(() => {
  if (!props.content) return '<p class="md-empty">（暂无内容）</p>';
  return DOMPurify.sanitize(marked.parse(props.content));
});
</script>

<style>
.md-viewer {
  font-size: 15px;
  line-height: 1.75;
  color: #1d2129;
  word-break: break-word;
}

.md-viewer h1,
.md-viewer h2,
.md-viewer h3,
.md-viewer h4 {
  margin: 22px 0 12px;
  line-height: 1.4;
}

.md-viewer h1 {
  font-size: 22px;
}

.md-viewer h2 {
  font-size: 19px;
  border-bottom: 1px solid #e5e6eb;
  padding-bottom: 8px;
}

.md-viewer h3 {
  font-size: 17px;
}

.md-viewer p {
  margin: 8px 0;
}

.md-viewer ul,
.md-viewer ol {
  padding-left: 24px;
}

.md-viewer code {
  font-family: Consolas, 'Courier New', monospace;
  background: #f2f3f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}

.md-viewer pre {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 14px 16px;
  overflow: auto;
}

.md-viewer pre code {
  background: transparent;
  color: inherit;
  padding: 0;
}

.md-viewer blockquote {
  margin: 12px 0;
  padding: 2px 14px;
  border-left: 4px solid #165dff;
  background: #f2f7ff;
  border-radius: 0 6px 6px 0;
  color: #4e5969;
}

.md-viewer table {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}

.md-viewer th,
.md-viewer td {
  border: 1px solid #e5e6eb;
  padding: 8px 12px;
}

.md-viewer th {
  background: #f7f8fa;
}

.md-viewer a {
  color: #165dff;
}

.md-empty {
  color: #86909c;
}
</style>
