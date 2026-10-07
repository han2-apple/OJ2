<template>
  <div class="page-container">
    <a-card :bordered="false" class="list-card">
      <div class="filter-bar">
        <div class="filter-title">题目管理</div>
        <a-space wrap>
          <a-input-search
            v-model="query.title"
            placeholder="题目名称"
            allow-clear
            style="width: 220px"
            @search="handleSearch"
            @press-enter="handleSearch"
          />
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" status="success" @click="openCreate">
            创建题目
          </a-button>
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
          <a-table-column title="标题" data-index="title" min-width="200">
            <template #cell="{ record }">
              <a-link @click="goView(record.id)">{{ record.title }}</a-link>
            </template>
          </a-table-column>
          <a-table-column title="标签" :width="240">
            <template #cell="{ record }">
              <a-space wrap>
                <a-tag
                  v-for="tag in parseJsonArray(record.tags)"
                  :key="tag"
                  :color="tagColor(tag)"
                >
                  {{ tag }}
                </a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="提交 / 通过" :width="130" align="center">
            <template #cell="{ record }">
              {{ record.submitNum || 0 }} / {{ record.acceptedNum || 0 }}
            </template>
          </a-table-column>
          <a-table-column title="创建用户 ID" data-index="userId" :width="120" align="center">
            <template #cell="{ record }">{{ record.userId }}</template>
          </a-table-column>
          <a-table-column title="创建时间" :width="180" align="center">
            <template #cell="{ record }">
              {{ formatDate(record.createTime) }}
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="240" align="center">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="goView(record.id)">
                  做题页
                </a-button>
                <a-button type="text" size="small" @click="openEdit(record.id)">
                  编辑
                </a-button>
                <a-popconfirm
                  content="确认删除该题目吗？删除后不可恢复"
                  @ok="handleDelete(record)"
                >
                  <a-button type="text" status="danger" size="small">
                    删除
                  </a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <QuestionFormDrawer
      v-model:visible="drawerVisible"
      :question-id="editId"
      @success="loadData"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import { deleteQuestion, listQuestionByPage } from '@/api/question';
import QuestionFormDrawer from '@/components/question/QuestionFormDrawer.vue';
import { formatDate, parseJsonArray } from '@/utils';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const questionList = ref([]);
const drawerVisible = ref(false);
const editId = ref(null);

const query = reactive({
  title: '',
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

function tagColor(tag) {
  return TAG_COLORS[tag] || 'arcoblue';
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
    const res = await listQuestionByPage(payload);
    questionList.value = res.data?.records || [];
    pagination.total = res.data?.total || 0;
  } catch (e) {
    questionList.value = [];
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pagination.current = 1;
  loadData();
}

function handleReset() {
  query.title = '';
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

function goView(id) {
  router.push(`/question/view/${id}`);
}

function openCreate() {
  editId.value = null;
  drawerVisible.value = true;
}

function openEdit(id) {
  editId.value = id;
  drawerVisible.value = true;
}

async function handleDelete(record) {
  try {
    await deleteQuestion({ id: record.id });
    Message.success('删除成功');
    loadData();
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
}

onMounted(() => {
  loadData();
  // 支持从做题页“编辑题目”跳转进来并直接打开编辑抽屉
  if (route.query.edit) {
    openEdit(Number(route.query.edit));
  }
});
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
</style>
