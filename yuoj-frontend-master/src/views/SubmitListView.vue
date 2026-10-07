<template>
  <div class="page-container">
    <a-card :bordered="false" class="list-card">
      <div class="filter-bar">
        <div class="filter-title">提交记录</div>
        <a-space wrap>
          <a-input-number
            v-model="query.questionId"
            placeholder="题目 ID"
            :style="{ width: 120 }"
            allow-clear
            @change="handleFilterChange"
          />
          <a-select
            v-model="query.status"
            placeholder="判题状态"
            :options="STATUS_OPTIONS"
            allow-clear
            :style="{ width: 130 }"
            @change="handleFilterChange"
          />
          <a-select
            v-model="query.language"
            placeholder="语言"
            :options="LANGUAGE_OPTIONS"
            allow-clear
            :style="{ width: 120 }"
            @change="handleFilterChange"
          />
          <a-input-number
            v-if="userStore.isAdmin"
            v-model="query.userId"
            placeholder="用户 ID（空=全部）"
            :style="{ width: 180 }"
            allow-clear
            @change="handleFilterChange"
          />
          <a-button type="primary" @click="handleFilterChange">查询</a-button>
          <a-button @click="loadData">刷新</a-button>
        </a-space>
      </div>

      <a-table
        row-key="id"
        :data="submitList"
        :loading="loading"
        :pagination="pagination"
        :bordered="false"
        @page-change="onPageChange"
        @page-size-change="onPageSizeChange"
      >
        <template #columns>
          <a-table-column title="ID" data-index="id" :width="80" align="center">
            <template #cell="{ record }">{{ record.id }}</template>
          </a-table-column>
          <a-table-column title="题目" data-index="questionId" min-width="180">
            <template #cell="{ record }">
              <a-link
                @click="
                  router.push(`/question/view/${record.questionId}`)
                "
              >
                {{ record.questionVO?.title || `题目 ${record.questionId}` }}
              </a-link>
            </template>
          </a-table-column>
          <a-table-column title="用户" data-index="userId" :width="120">
            <template #cell="{ record }">
              {{ record.userVO?.userName || record.userId }}
            </template>
          </a-table-column>
          <a-table-column title="语言" data-index="language" :width="100" align="center">
            <template #cell="{ record }">
              {{ LANGUAGE_MAP[record.language] || record.language }}
            </template>
          </a-table-column>
          <a-table-column title="状态" data-index="status" :width="100" align="center">
            <template #cell="{ record }">
              <StatusTag :status="record.status" />
            </template>
          </a-table-column>
          <a-table-column title="判题信息" :width="200">
            <template #cell="{ record }">
              <span :class="judgeClass(record)">
                {{ formatJudgeMessage(record.judgeInfo?.message) }}
              </span>
            </template>
          </a-table-column>
          <a-table-column title="耗时/内存" :width="150" align="center">
            <template #cell="{ record }">
              <template v-if="record.judgeInfo">
                {{ record.judgeInfo.time || 0 }}ms /
                {{ formatMemoryLimit(record.judgeInfo.memory) }}
              </template>
              <span v-else>-</span>
            </template>
          </a-table-column>
          <a-table-column title="提交时间" :width="180" align="center">
            <template #cell="{ record }">
              {{ formatDate(record.createTime) }}
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="100" align="center">
            <template #cell="{ record }">
              <a-button type="text" @click="openDetail(record)">查看代码</a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <a-drawer
      v-model:visible="drawerVisible"
      :width="680"
      title="提交详情"
      unmount-on-close
    >
      <template v-if="detail">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="提交 ID">{{ detail.id }}</a-descriptions-item>
          <a-descriptions-item label="题目">
            <a-link @click="router.push(`/question/view/${detail.questionId}`)">
              {{ detail.questionVO?.title || `题目 ${detail.questionId}` }}
            </a-link>
          </a-descriptions-item>
          <a-descriptions-item label="语言">
            {{ LANGUAGE_MAP[detail.language] || detail.language }}
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <StatusTag :status="detail.status" />
          </a-descriptions-item>
          <a-descriptions-item label="提交用户">
            {{ detail.userVO?.userName || detail.userId }}
          </a-descriptions-item>
          <a-descriptions-item label="提交时间">
            {{ formatDate(detail.createTime) }}
          </a-descriptions-item>
          <a-descriptions-item label="判题信息" :span="2">
            {{ formatJudgeMessage(detail.judgeInfo?.message) }}
          </a-descriptions-item>
          <a-descriptions-item label="耗时">
            {{ detail.judgeInfo?.time }} ms
          </a-descriptions-item>
          <a-descriptions-item label="内存">
            {{ formatMemoryLimit(detail.judgeInfo?.memory) }}
          </a-descriptions-item>
        </a-descriptions>

        <div class="detail-title">提交代码</div>
        <pre class="code-block">{{ detail.code }}</pre>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { listQuestionSubmitByPage } from '@/api/question';
import {
  LANGUAGE_MAP,
  LANGUAGE_OPTIONS,
  STATUS_OPTIONS,
} from '@/constants/question';
import { useUserStore } from '@/store/user';
import StatusTag from '@/components/StatusTag.vue';
import { formatDate, formatJudgeMessage, formatMemoryLimit } from '@/utils';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const loading = ref(false);
const submitList = ref([]);
const drawerVisible = ref(false);
const detail = ref(null);
let refreshTimer = null;

const query = reactive({
  questionId: undefined,
  status: undefined,
  language: undefined,
  userId: undefined,
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
});

if (route.query.questionId) {
  query.questionId = Number(route.query.questionId);
}

function judgeClass(record) {
  if (record.status === 2) return 'judge-pass';
  if (record.status === 3) return 'judge-fail';
  return 'judge-wait';
}

function buildPayload() {
  const payload = {
    current: pagination.current,
    pageSize: pagination.pageSize,
    sortField: 'createTime',
    sortOrder: 'descend',
  };
  if (query.questionId) payload.questionId = query.questionId;
  if (query.status !== undefined && query.status !== null && query.status !== '') {
    payload.status = query.status;
  }
  if (query.language) payload.language = query.language;

  // 普通用户只能查看自己的提交；管理员留空可查看全部
  if (userStore.isAdmin) {
    if (query.userId) payload.userId = query.userId;
  } else {
    payload.userId = userStore.loginUser?.id;
  }
  return payload;
}

async function loadData() {
  loading.value = true;
  try {
    const res = await listQuestionSubmitByPage(buildPayload());
    submitList.value = res.data?.records || [];
    pagination.total = res.data?.total || 0;
    scheduleAutoRefresh();
  } catch (e) {
    submitList.value = [];
  } finally {
    loading.value = false;
  }
}

function hasPending() {
  return submitList.value.some(
    (item) => item.status === 0 || item.status === 1
  );
}

let refreshCount = 0;
function scheduleAutoRefresh() {
  clearAutoRefresh();
  if (hasPending()) {
    refreshTimer = setTimeout(() => {
      refreshCount += 1;
      if (refreshCount > 20) {
        refreshCount = 0;
        clearAutoRefresh();
        return;
      }
      loadData();
    }, 3000);
  } else {
    refreshCount = 0;
  }
}

function clearAutoRefresh() {
  if (refreshTimer) {
    clearTimeout(refreshTimer);
    refreshTimer = null;
  }
}

function handleFilterChange() {
  pagination.current = 1;
  loadData();
}

function onPageChange(page) {
  pagination.current = page;
  loadData();
}

function onPageSizeChange(size) {
  pagination.pageSize = size;
  pagination.current = 1;
  loadData();
}

function openDetail(record) {
  detail.value = record;
  drawerVisible.value = true;
}

onMounted(loadData);
onBeforeUnmount(clearAutoRefresh);
</script>

<style scoped>
.list-card {
  border-radius: 12px;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.filter-title {
  font-size: 20px;
  font-weight: 600;
  color: #1d2129;
}

.judge-pass {
  color: #00b42a;
}

.judge-fail {
  color: #f53f3f;
}

.judge-wait {
  color: #86909c;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  margin: 20px 0 10px;
}
</style>
