<template>
  <a-drawer
    :visible="visible"
    :width="860"
    :title="isEdit ? '编辑题目' : '创建题目'"
    unmount-on-close
    @cancel="handleCancel"
  >
    <a-spin :loading="loading" style="display: block">
      <a-form :model="form" layout="vertical">
        <a-form-item label="题目标题" required>
          <a-input
            v-model="form.title"
            placeholder="请输入题目标题"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="标签">
          <a-select
            v-model="form.tags"
            multiple
            allow-create
            allow-clear
            placeholder="输入标签后回车，例如：简单 / 数组 / 动态规划"
          />
        </a-form-item>

        <a-form-item label="题目内容（Markdown）" required>
          <a-radio-group v-model="contentMode" type="button" style="margin-bottom: 10px">
            <a-radio value="edit">编辑</a-radio>
            <a-radio value="preview">预览</a-radio>
          </a-radio-group>
          <a-textarea
            v-if="contentMode === 'edit'"
            v-model="form.content"
            placeholder="使用 Markdown 编写题目描述、输入输出格式与样例"
            :auto-size="{ minRows: 10, maxRows: 18 }"
            allow-clear
          />
          <div v-else class="preview-box">
            <MdViewer :content="form.content" />
          </div>
        </a-form-item>

        <a-form-item label="参考题解">
          <a-textarea
            v-model="form.answer"
            placeholder="题解/参考代码（仅管理员可见）"
            :auto-size="{ minRows: 4, maxRows: 12 }"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="判题配置">
          <a-row :gutter="12">
            <a-col :span="8">
              <div class="cfg-label">时间限制（ms）</div>
              <a-input-number
                v-model="form.judgeConfig.timeLimit"
                :min="100"
                :max="60000"
                :style="{ width: '100%' }"
              />
            </a-col>
            <a-col :span="8">
              <div class="cfg-label">内存限制（KB）</div>
              <a-input-number
                v-model="form.judgeConfig.memoryLimit"
                :min="1024"
                :max="1048576"
                :style="{ width: '100%' }"
              />
            </a-col>
            <a-col :span="8">
              <div class="cfg-label">栈限制（KB）</div>
              <a-input-number
                v-model="form.judgeConfig.stackLimit"
                :min="0"
                :max="1048576"
                :style="{ width: '100%' }"
              />
            </a-col>
          </a-row>
        </a-form-item>

        <a-form-item label="测试用例">
          <div class="case-toolbar">
            <span class="case-tip">每个用例包含标准输入与预期输出</span>
            <a-space>
              <a-button size="small" @click="openJsonImport">
                批量导入(JSON)
              </a-button>
              <a-button
                size="small"
                type="primary"
                status="success"
                @click="addJudgeCase"
              >
                添加用例
              </a-button>
            </a-space>
          </div>

          <div v-if="form.judgeCase.length === 0" class="case-empty">
            暂无测试用例，请添加或批量导入
          </div>

          <div
            v-for="(item, index) in form.judgeCase"
            :key="index"
            class="case-row"
          >
            <div class="case-index">用例 {{ index + 1 }}</div>
            <a-textarea
              v-model="item.input"
              placeholder="输入（input）"
              :auto-size="{ minRows: 2, maxRows: 6 }"
            />
            <a-textarea
              v-model="item.output"
              placeholder="输出（output）"
              :auto-size="{ minRows: 2, maxRows: 6 }"
            />
            <a-button
              type="text"
              status="danger"
              @click="removeJudgeCase(index)"
            >
              <template #icon><icon-delete /></template>
            </a-button>
          </div>
        </a-form-item>
      </a-form>
    </a-spin>

    <template #footer>
      <div class="drawer-footer">
        <a-space>
          <a-button @click="handleCancel">取消</a-button>
          <a-button type="primary" :loading="saving" @click="handleSave">
            保存
          </a-button>
        </a-space>
      </div>
    </template>

    <a-modal
      v-model:visible="jsonModalVisible"
      title="批量导入测试用例"
      @ok="importJson"
    >
      <p class="json-tip">
        粘贴 JSON 数组，例如：
        <code>[{"input":"1 2","output":"3"},{"input":"2 3","output":"5"}]</code>
      </p>
      <a-textarea
        v-model="jsonText"
        :auto-size="{ minRows: 6, maxRows: 12 }"
        placeholder='[{"input":"1 2","output":"3"}]'
      />
    </a-modal>
  </a-drawer>
</template>

<script setup>
import { reactive, ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import { addQuestion, getQuestionById, updateQuestion } from '@/api/question';
import { DEFAULT_JUDGE_CONFIG } from '@/constants/question';
import MdViewer from '@/components/MdViewer.vue';
import { parseJsonArray, parseJsonObject } from '@/utils';

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  questionId: {
    type: Number,
    default: null,
  },
});

const emit = defineEmits(['update:visible', 'success']);

const isEdit = ref(false);
const loading = ref(false);
const saving = ref(false);
const contentMode = ref('edit');
const jsonModalVisible = ref(false);
const jsonText = ref('');

const form = reactive({
  id: undefined,
  title: '',
  content: '',
  tags: [],
  answer: '',
  judgeConfig: { ...DEFAULT_JUDGE_CONFIG },
  judgeCase: [],
});

function resetForm() {
  form.id = undefined;
  form.title = '';
  form.content = '';
  form.tags = [];
  form.answer = '';
  form.judgeConfig = { ...DEFAULT_JUDGE_CONFIG };
  form.judgeCase = [];
  contentMode.value = 'edit';
}

function addJudgeCase() {
  form.judgeCase.push({ input: '', output: '' });
}

function removeJudgeCase(index) {
  form.judgeCase.splice(index, 1);
}

function openJsonImport() {
  jsonText.value = '';
  jsonModalVisible.value = true;
}

function importJson() {
  try {
    const list = JSON.parse(jsonText.value || '[]');
    if (!Array.isArray(list)) {
      Message.error('请输入 JSON 数组');
      return;
    }
    form.judgeCase = list.map((item) => ({
      input: item.input !== undefined ? String(item.input) : '',
      output: item.output !== undefined ? String(item.output) : '',
    }));
    Message.success(`成功导入 ${list.length} 条测试用例`);
    jsonModalVisible.value = false;
  } catch (e) {
    Message.error('JSON 解析失败，请检查格式');
  }
}

async function loadQuestion() {
  if (!props.questionId) return;
  loading.value = true;
  try {
    const res = await getQuestionById(props.questionId);
    const data = res.data;
    if (!data) {
      Message.error('题目不存在');
      return;
    }
    form.id = data.id;
    form.title = data.title || '';
    form.content = data.content || '';
    form.tags = parseJsonArray(data.tags);
    form.answer = data.answer || '';
    form.judgeConfig = parseJsonObject(
      data.judgeConfig,
      DEFAULT_JUDGE_CONFIG
    );
    form.judgeCase = parseJsonArray(data.judgeCase).map((item) => ({
      input: item.input !== undefined ? String(item.input) : '',
      output: item.output !== undefined ? String(item.output) : '',
    }));
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      isEdit.value = !!props.questionId;
      resetForm();
      if (props.questionId) {
        loadQuestion();
      }
    }
  }
);

watch(
  () => props.questionId,
  (questionId) => {
    // 抽屉已打开时切换题目（如编辑另一道题）也要重新加载
    if (props.visible && questionId) {
      isEdit.value = true;
      resetForm();
      loadQuestion();
    }
  }
);

async function handleSave() {
  if (!form.title?.trim()) {
    Message.warning('请输入题目标题');
    return;
  }
  if (!form.content?.trim()) {
    Message.warning('请输入题目内容');
    return;
  }

  const payload = {
    title: form.title.trim(),
    content: form.content,
    tags: form.tags || [],
    answer: form.answer,
    judgeConfig: {
      timeLimit: Number(form.judgeConfig.timeLimit) || DEFAULT_JUDGE_CONFIG.timeLimit,
      memoryLimit:
        Number(form.judgeConfig.memoryLimit) || DEFAULT_JUDGE_CONFIG.memoryLimit,
      stackLimit:
        Number(form.judgeConfig.stackLimit) || DEFAULT_JUDGE_CONFIG.stackLimit,
    },
    judgeCase: form.judgeCase.map((item) => ({
      input: String(item.input || ''),
      output: String(item.output || ''),
    })),
  };

  saving.value = true;
  try {
    if (isEdit.value) {
      await updateQuestion({ ...payload, id: form.id });
      Message.success('更新成功');
    } else {
      await addQuestion(payload);
      Message.success('创建成功');
    }
    emit('success');
    emit('update:visible', false);
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    saving.value = false;
  }
}

function handleCancel() {
  emit('update:visible', false);
}
</script>

<style scoped>
.cfg-label {
  font-size: 13px;
  color: #4e5969;
  margin-bottom: 6px;
}

.case-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  width: 100%;
}

.case-tip {
  color: #86909c;
  font-size: 13px;
}

.case-empty {
  padding: 20px;
  text-align: center;
  color: #86909c;
  background: #f7f8fa;
  border-radius: 8px;
  width: 100%;
}

.case-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  width: 100%;
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  background: #fafbfc;
}

.case-row :deep(.arco-textarea) {
  flex: 1;
}

.case-index {
  min-width: 56px;
  padding-top: 6px;
  font-weight: 600;
  color: #1d2129;
}

.preview-box {
  width: 100%;
  min-height: 180px;
  padding: 12px 16px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  background: #fff;
}

.json-tip {
  color: #4e5969;
  font-size: 13px;
}

.json-tip code {
  background: #f2f3f5;
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
