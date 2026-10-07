<template>
  <div ref="containerRef" class="code-editor" :style="{ height }"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as monaco from 'monaco-editor';
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import 'monaco-editor/min/vs/editor/editor.main.css';

// 配置 Monaco 的 Web Worker
self.MonacoEnvironment = {
  getWorker() {
    return new EditorWorker();
  },
};

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
  },
  language: {
    type: String,
    default: 'java',
  },
  height: {
    type: String,
    default: '420px',
  },
  readOnly: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['update:modelValue']);

const containerRef = ref(null);
let editor = null;

onMounted(() => {
  editor = monaco.editor.create(containerRef.value, {
    value: props.modelValue,
    language: props.language,
    theme: 'vs',
    readOnly: props.readOnly,
    fontSize: 14,
    tabSize: 4,
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    renderLineHighlight: 'all',
    fixedOverflowWidgets: true,
  });

  editor.onDidChangeModelContent(() => {
    const value = editor.getValue();
    if (value !== props.modelValue) {
      emit('update:modelValue', value);
    }
  });

  watch(
    () => props.modelValue,
    (value) => {
      if (editor && value !== editor.getValue()) {
        editor.setValue(value || '');
      }
    }
  );

  watch(
    () => props.language,
    (language) => {
      if (editor) {
        monaco.editor.setModelLanguage(editor.getModel(), language);
      }
    }
  );

  watch(
    () => props.readOnly,
    (readOnly) => {
      editor.updateOptions({ readOnly });
    }
  );
});

onBeforeUnmount(() => {
  if (editor) {
    editor.dispose();
    editor = null;
  }
});
</script>

<style scoped>
.code-editor {
  width: 100%;
  min-height: 200px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  overflow: hidden;
}
</style>
