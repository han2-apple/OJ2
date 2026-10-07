<template>
  <div class="page-container do-question">
    <a-spin :loading="loading" style="display: block; min-height: 300px">
      <a-empty v-if="!loading && !question" description="题目不存在或已被删除" />

      <template v-else-if="question">
        <div class="do-header">
          <div>
            <div class="title-row">
              <h1 class="do-title">{{ question.id }}. {{ question.title }}</h1>
              <a-button
                v-if="userStore.isAdmin"
                size="small"
                @click="goManage"
              >
                编辑题目
              </a-button>
            </div>
            <a-space wrap size="medium">
              <a-space wrap>
                <a-tag
                  v-for="tag in question.tags || []"
                  :key="tag"
                  :color="tagColor(tag)"
                >
                  {{ tag }}
                </a-tag>
              </a-space>
              <span class="meta-item">
                通过率：{{ calcPassRate(question.acceptedNum, question.submitNum) }}
              </span>
              <span class="meta-item">提交：{{ question.submitNum || 0 }}</span>
              <span class="meta-item">
                时间限制：{{
                  formatTimeLimit(question.judgeConfig?.timeLimit)
                }}
              </span>
              <span class="meta-item">
                内存限制：{{
                  formatMemoryLimit(question.judgeConfig?.memoryLimit)
                }}
              </span>
            </a-space>
          </div>
        </div>

        <a-row :gutter="16">
          <a-col :xs="24" :lg="13">
            <a-card title="题目描述" :bordered="false" class="panel-card">
              <MdViewer :content="question.content" />
            </a-card>
          </a-col>

          <a-col :xs="24" :lg="11">
            <a-card :bordered="false" class="panel-card">
              <template #title>
                <div class="editor-title">
                  <span>在线答题</span>
                  <a-button
                    v-if="userStore.isLogin"
                    type="text"
                    @click="
                      router.push(
                        `/submit/list?questionId=${question.id}`
                      )
                    "
                  >
                    我的提交记录
                  </a-button>
                </div>
              </template>

              <div class="editor-toolbar">
                <a-select
                  v-model="language"
                  :options="LANGUAGE_OPTIONS"
                  style="width: 130px"
                />
                <a-button
                  type="primary"
                  :loading="submitting"
                  :disabled="!userStore.isLogin"
                  @click="handleSubmit"
                >
                  提交代码
                </a-button>
              </div>

              <a-alert
                v-if="!userStore.isLogin"
                type="warning"
                style="margin-bottom: 12px"
              >
                <template #title>
                  <span>请先</span>
                  <a-link @click="goLogin">登录</a-link>
                  <span>后再提交代码</span>
                </template>
              </a-alert>

              <CodeEditor v-model="code" :language="editorLanguage" height="440px" />

              <a-card
                v-if="lastSubmit"
                class="result-panel"
                size="small"
                :bordered="false"
              >
                <template #title>
                  <div class="editor-title">
                    <span>判题结果</span>
                    <StatusTag :status="lastSubmit.status" />
                  </div>
                </template>

                <a-alert
                  v-if="lastSubmit.status === 2"
                  type="success"
                  message="恭喜，判题通过！"
                />
                <a-alert
                  v-else-if="lastSubmit.status === 3"
                  type="error"
                  :message="
                    formatJudgeMessage(lastSubmit.judgeInfo?.message)
                  "
                />
                <a-alert
                  v-else-if="lastSubmit.status === 1"
                  type="info"
                  message="正在判题中，请稍候…"
                />
                <a-alert v-else type="warning" message="已提交，等待进入判题队列…" />

                <a-descriptions
                  v-if="lastSubmit.status >= 2 && lastSubmit.judgeInfo"
                  :column="3"
                  size="small"
                  style="margin-top: 14px"
                >
                  <a-descriptions-item label="判题信息">
                    {{ formatJudgeMessage(lastSubmit.judgeInfo.message) }}
                  </a-descriptions-item>
                  <a-descriptions-item label="耗时">
                    {{ lastSubmit.judgeInfo.time }} ms
                  </a-descriptions-item>
                  <a-descriptions-item label="内存">
                    {{ formatMemoryLimit(lastSubmit.judgeInfo.memory) }}
                  </a-descriptions-item>
                </a-descriptions>

                <div class="result-foot">
                  提交时间：{{ formatDate(lastSubmit.createTime) }}
                  <a-link
                    @click="
                      router.push(
                        `/submit/list?questionId=${question.id}`
                      )
                    "
                  >
                    查看提交记录
                  </a-link>
                </div>
              </a-card>
            </a-card>
          </a-col>
        </a-row>
      </template>
    </a-spin>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import {
  doQuestionSubmit,
  getQuestionVOById,
  listQuestionSubmitByPage,
} from '@/api/question';
import { DEFAULT_CODE, LANGUAGE_OPTIONS } from '@/constants/question';
import { useUserStore } from '@/store/user';
import CodeEditor from '@/components/CodeEditor.vue';
import MdViewer from '@/components/MdViewer.vue';
import StatusTag from '@/components/StatusTag.vue';
import {
  calcPassRate,
  formatDate,
  formatJudgeMessage,
  formatMemoryLimit,
  formatTimeLimit,
} from '@/utils';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const questionId = computed(() => Number(route.params.id));
const loading = ref(false);
const question = ref(null);

const language = ref('java');
const code = ref(DEFAULT_CODE.java);
const editorLanguage = computed(() =>
  language.value === 'cpp' ? 'cpp' : language.value
);

const submitting = ref(false);
const lastSubmitId = ref(null);
const lastSubmit = ref(null);
const pollCount = ref(0);
let pollTimer = null;

const TAG_COLORS = {
  简单: 'green',
  中等: 'orange',
  困难: 'red',
};

function tagColor(tag) {
  return TAG_COLORS[tag] || 'arcoblue';
}

function goLogin() {
  router.push({
    path: '/user/login',
    query: { redirect: route.fullPath },
  });
}

function goManage() {
  router.push(`/admin/question?edit=${questionId.value}`);
}

async function loadQuestion() {
  loading.value = true;
  question.value = null;
  lastSubmit.value = null;
  stopPolling();
  try {
    const res = await getQuestionVOById(questionId.value);
    question.value = res.data || null;
    if (question.value) {
      language.value = 'java';
      code.value = DEFAULT_CODE.java;
    }
  } catch (e) {
    question.value = null;
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!userStore.isLogin) {
    goLogin();
    return;
  }
  if (!question.value) return;
  if (!code.value.trim()) {
    Message.warning('请先编写代码');
    return;
  }

  submitting.value = true;
  lastSubmit.value = null;
  stopPolling();
  try {
    const res = await doQuestionSubmit({
      questionId: questionId.value,
      language: language.value,
      code: code.value,
    });
    lastSubmitId.value = res.data;
    pollCount.value = 0;
    Message.success('提交成功，正在判题…');
    schedulePoll();
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    submitting.value = false;
  }
}

function schedulePoll() {
  stopPolling();
  pollTimer = setTimeout(pollOnce, 1500);
}

async function pollOnce() {
  if (pollCount.value >= 30) {
    stopPolling();
    Message.warning('判题超时，请到提交记录中查看最新结果');
    return;
  }
  pollCount.value += 1;
  try {
    const res = await listQuestionSubmitByPage({
      current: 1,
      pageSize: 1,
      questionId: questionId.value,
      userId: userStore.loginUser?.id,
      sortField: 'createTime',
      sortOrder: 'descend',
    });
    const records = res.data?.records || [];
    if (records.length > 0) {
      const current = records[0];
      // 等后端提交记录中包含本次提交后再展示，避免展示历史结果
      if (current.id === lastSubmitId.value) {
        lastSubmit.value = current;
        if (current.status >= 2) {
          stopPolling();
          return;
        }
      }
    }
  } catch (e) {
    // 忽略轮询过程中的单次错误，继续等待
  }
  pollTimer = setTimeout(pollOnce, 1500);
}

function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
}

watch(
  () => route.params.id,
  () => loadQuestion(),
  { immediate: true }
);

onBeforeUnmount(stopPolling);
</script>

<style scoped>
.do-question {
  max-width: 1400px;
}

.do-header {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  margin-bottom: 16px;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.do-title {
  font-size: 22px;
  margin: 0 0 12px;
  color: #1d2129;
}

.meta-item {
  color: #4e5969;
  font-size: 13px;
}

.panel-card {
  border-radius: 12px;
  margin-bottom: 16px;
}

.editor-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.result-panel {
  margin-top: 14px;
  background: #f7f8fa;
  border-radius: 10px;
}

.result-foot {
  margin-top: 12px;
  font-size: 13px;
  color: #86909c;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
