<template>
  <div class="page-container">
    <a-card :bordered="false" class="list-card">
      <div class="filter-bar">
        <div class="filter-title">题库</div>
        <a-space wrap>
          <a-input-search
            v-model="query.title"
            placeholder="输入题目名称搜索"
            allow-clear
            style="width: 260px"
            @search="handleSearch"
            @press-enter="handleSearch"
          />
          <a-select
            v-model="query.tags"
            multiple
            allow-create
            allow-clear
            placeholder="按标签筛选（可输入自定义）"
            :options="tagOptions"
            style="width: 300px"
          />
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
        </a-space>
      </div>

      <a-table
        row-key="id"
        :data="questionList"
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
          <a-table-column title="标题" data-index="title" min-width="220">
            <template #cell="{ record }">
              <a-link @click="goDetail(record.id)">{{ record.title }}</a-link>
            </template>
          </a-table-column>
          <a-table-column title="标签" data-index="tags" :width="260">
            <template #cell="{ record }">
              <a-space wrap>
                <a-tag
                  v-for="tag in record.tags || []"
                  :key="tag"
                  :color="tagColor(tag)"
                >
                  {{ tag }}
                </a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="通过率" :width="120" align="center">
            <template #cell="{ record }">
              <span :class="rateClass(record)">
                {{ calcPassRate(record.acceptedNum, record.submitNum) }}
              </span>
            </template>
          </a-table-column>
          <a-table-column title="提交数" :width="100" align="center">
            <template #cell="{ record }">{{ record.submitNum || 0 }}</template>
          </a-table-column>
          <a-table-column title="创建时间" :width="180" align="center">
            <template #cell="{ record }">
              {{ formatDate(record.createTime) }}
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="100" align="center">
            <template #cell="{ record }">
              <a-button type="text" @click="goDetail(record.id)">开始做题</a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listQuestionVOByPage } from '@/api/question';
import { calcPassRate, formatDate } from '@/utils';

const router = useRouter();

const loading = ref(false);
const questionList = ref([]);
const tagSet = ref(new Set());

const query = reactive({
  title: '',
  tags: [],
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
});

const TAG_COLORS = {
  简单: 'green',
  中等: 'orange',
  困难: 'red',
};

const tagOptions = computed(() =>
  Array.from(tagSet.value).map((tag) => ({ label: tag, value: tag }))
);

function tagColor(tag) {
  return TAG_COLORS[tag] || 'arcoblue';
}

function rateClass(record) {
  if (!record.submitNum) return 'rate-default';
  const rate = record.acceptedNum / record.submitNum;
  if (rate >= 0.7) return 'rate-good';
  if (rate >= 0.4) return 'rate-mid';
  return 'rate-bad';
}

function goDetail(id) {
  router.push(`/question/view/${id}`);
}

async function loadData() {
  loading.value = true;
  try {
    const payload = {
      current: pagination.current,
      pageSize: pagination.pageSize,
      sortField: 'createTime',
      sortOrder: 'descend',
    };
    if (query.title) payload.title = query.title;
    if (query.tags && query.tags.length > 0) payload.tags = query.tags;

    const res = await listQuestionVOByPage(payload);
    questionList.value = res.data?.records || [];
    pagination.total = res.data?.total || 0;

    const set = new Set();
    questionList.value.forEach((item) => {
      (item.tags || []).forEach((tag) => set.add(tag));
    });
    tagSet.value = set;
  } catch (e) {
    questionList.value = [];
  } finally {
    loading.value = false;
  }
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

function handleSearch() {
  pagination.current = 1;
  loadData();
}

function handleReset() {
  query.title = '';
  query.tags = [];
  pagination.current = 1;
  loadData();
}

onMounted(loadData);
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

.rate-good {
  color: #00b42a;
  font-weight: 600;
}

.rate-mid {
  color: #ff7d00;
  font-weight: 600;
}

.rate-bad {
  color: #f53f3f;
  font-weight: 600;
}

.rate-default {
  color: #86909c;
}
</style>
