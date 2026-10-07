<template>
  <div class="page-container">
    <a-card :bordered="false" class="list-card">
      <div class="filter-bar">
        <div class="filter-title">用户管理</div>
        <a-space wrap>
          <a-input-search
            v-model="query.userName"
            placeholder="用户昵称"
            allow-clear
            style="width: 200px"
            @search="handleSearch"
            @press-enter="handleSearch"
          />
          <a-select
            v-model="query.userRole"
            placeholder="角色"
            :options="USER_ROLE_OPTIONS"
            allow-clear
            style="width: 140px"
            @change="handleSearch"
          />
          <a-button type="primary" @click="handleSearch">查询</a-button>
          <a-button @click="handleReset">重置</a-button>
          <a-button type="primary" status="success" @click="openCreate">
            新增用户
          </a-button>
        </a-space>
      </div>

      <a-table
        row-key="id"
        :data="userList"
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
          <a-table-column title="账号" data-index="userAccount" :width="150">
            <template #cell="{ record }">{{ record.userAccount }}</template>
          </a-table-column>
          <a-table-column title="昵称" data-index="userName" :width="150">
            <template #cell="{ record }">{{ record.userName || '-' }}</template>
          </a-table-column>
          <a-table-column title="头像" :width="80" align="center">
            <template #cell="{ record }">
              <a-avatar :size="32" :src="record.userAvatar">
                {{ (record.userName || 'U').slice(0, 1).toUpperCase() }}
              </a-avatar>
            </template>
          </a-table-column>
          <a-table-column title="角色" data-index="userRole" :width="110" align="center">
            <template #cell="{ record }">
              <a-tag :color="record.userRole === 'admin' ? 'gold' : 'green'">
                {{ USER_ROLE_MAP[record.userRole] || record.userRole }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="个人简介" data-index="userProfile">
            <template #cell="{ record }">
              <span class="profile-text">{{ record.userProfile || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="创建时间" :width="180" align="center">
            <template #cell="{ record }">
              {{ formatDate(record.createTime) }}
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="150" align="center">
            <template #cell="{ record }">
              <a-space>
                <a-button type="text" size="small" @click="openEdit(record)">
                  编辑
                </a-button>
                <a-popconfirm
                  content="确认删除该用户吗？删除后不可恢复"
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

    <a-modal
      v-model:visible="modalVisible"
      :title="modalMode === 'create' ? '新增用户' : '编辑用户'"
      :confirm-loading="saving"
      unmount-on-close
      @ok="handleSave"
      @cancel="resetForm"
    >
      <a-form :model="form" layout="vertical">
        <template v-if="modalMode === 'create'">
          <a-form-item label="账号" required>
            <a-input v-model="form.userAccount" placeholder="请输入登录账号" allow-clear />
          </a-form-item>
        </template>

        <a-form-item label="昵称" required>
          <a-input v-model="form.userName" placeholder="请输入昵称" allow-clear />
        </a-form-item>

        <a-form-item label="头像 URL">
          <a-input v-model="form.userAvatar" placeholder="请输入头像地址" allow-clear />
        </a-form-item>

        <a-form-item label="个人简介">
          <a-textarea
            v-model="form.userProfile"
            placeholder="请输入个人简介"
            :auto-size="{ minRows: 2, maxRows: 4 }"
            allow-clear
          />
        </a-form-item>

        <a-form-item label="角色" required>
          <a-radio-group v-model="form.userRole">
            <a-radio value="user">普通用户</a-radio>
            <a-radio value="admin">管理员</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import {
  addUser,
  deleteUser,
  listUserByPage,
  updateUser,
} from '@/api/user';
import { USER_ROLE_MAP, USER_ROLE_OPTIONS } from '@/constants/question';
import { formatDate } from '@/utils';

const loading = ref(false);
const userList = ref([]);
const modalVisible = ref(false);
const modalMode = ref('create');
const saving = ref(false);

const query = reactive({
  userName: '',
  userRole: undefined,
});

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
});

const form = reactive({
  id: undefined,
  userAccount: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
});

async function loadData() {
  loading.value = true;
  try {
    const payload = {
      current: pagination.current,
      pageSize: pagination.pageSize,
      sortField: 'createTime',
      sortOrder: 'descend',
    };
    if (query.userName) payload.userName = query.userName;
    if (query.userRole) payload.userRole = query.userRole;
    const res = await listUserByPage(payload);
    userList.value = res.data?.records || [];
    pagination.total = res.data?.total || 0;
  } catch (e) {
    userList.value = [];
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pagination.current = 1;
  loadData();
}

function handleReset() {
  query.userName = '';
  query.userRole = undefined;
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

function resetForm() {
  form.id = undefined;
  form.userAccount = '';
  form.userName = '';
  form.userAvatar = '';
  form.userProfile = '';
  form.userRole = 'user';
}

function openCreate() {
  resetForm();
  modalMode.value = 'create';
  modalVisible.value = true;
}

function openEdit(record) {
  resetForm();
  modalMode.value = 'edit';
  form.id = record.id;
  form.userName = record.userName || '';
  form.userAvatar = record.userAvatar || '';
  form.userProfile = record.userProfile || '';
  form.userRole = record.userRole || 'user';
  modalVisible.value = true;
}

async function handleSave() {
  if (modalMode.value === 'create' && !form.userAccount?.trim()) {
    Message.warning('请输入账号');
    return;
  }
  if (!form.userName?.trim()) {
    Message.warning('请输入昵称');
    return;
  }

  saving.value = true;
  try {
    if (modalMode.value === 'create') {
      await addUser({
        userAccount: form.userAccount.trim(),
        userName: form.userName.trim(),
        userAvatar: form.userAvatar,
        userRole: form.userRole,
      });
      Message.success('新增成功');
    } else {
      await updateUser({
        id: form.id,
        userName: form.userName.trim(),
        userAvatar: form.userAvatar,
        userProfile: form.userProfile,
        userRole: form.userRole,
      });
      Message.success('更新成功');
    }
    modalVisible.value = false;
    resetForm();
    loadData();
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    saving.value = false;
  }
}

async function handleDelete(record) {
  try {
    await deleteUser({ id: record.id });
    Message.success('删除成功');
    loadData();
  } catch (e) {
    // 错误提示已由拦截器统一处理
  }
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

.profile-text {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
</style>
