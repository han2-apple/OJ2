<template>
  <div class="page-container center-page">
    <a-row :gutter="16">
      <a-col :xs="24" :md="8">
        <a-card :bordered="false" class="info-card">
          <div class="avatar-wrap">
            <a-avatar :size="72" :src="userStore.loginUser?.userAvatar">
              {{ avatarText }}
            </a-avatar>
          </div>
          <div class="nickname">
            {{ userStore.loginUser?.userName || '未设置昵称' }}
          </div>
          <div class="role-tag">
            <a-tag :color="userStore.isAdmin ? 'gold' : 'green'">
              {{ userStore.isAdmin ? '管理员' : '普通用户' }}
            </a-tag>
          </div>
          <a-divider />
          <a-descriptions :column="1" size="small">
            <a-descriptions-item label="账号">
              {{ userStore.loginUser?.userAccount || '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="用户 ID">
              {{ userStore.loginUser?.id }}
            </a-descriptions-item>
            <a-descriptions-item label="注册时间">
              {{ formatDate(userStore.loginUser?.createTime) }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>
      </a-col>

      <a-col :xs="24" :md="16">
        <a-card :bordered="false" class="form-card">
          <template #title>个人资料</template>
          <a-form :model="form" layout="vertical">
            <a-form-item label="用户昵称" :rules="[{ required: true, message: '请输入昵称' }]">
              <a-input v-model="form.userName" placeholder="请输入昵称" allow-clear />
            </a-form-item>

            <a-form-item label="头像地址">
              <a-input v-model="form.userAvatar" placeholder="请输入图片 URL" allow-clear />
            </a-form-item>

            <a-form-item label="头像预览">
              <a-avatar :size="48" :src="form.userAvatar">{{ avatarText }}</a-avatar>
            </a-form-item>

            <a-form-item label="个人简介">
              <a-textarea
                v-model="form.userProfile"
                placeholder="介绍一下自己吧"
                :auto-size="{ minRows: 3, maxRows: 6 }"
                allow-clear
              />
            </a-form-item>

            <a-button type="primary" long :loading="saving" @click="handleSave">
              保存修改
            </a-button>
          </a-form>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import { useUserStore } from '@/store/user';
import { formatDate } from '@/utils';

const userStore = useUserStore();

const saving = ref(false);
const form = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
});

const avatarText = computed(() => {
  const name = form.userName || userStore.loginUser?.userName || 'U';
  return name.trim().slice(0, 1).toUpperCase();
});

function fillForm() {
  const user = userStore.loginUser;
  if (!user) return;
  form.userName = user.userName || '';
  form.userAvatar = user.userAvatar || '';
  form.userProfile = user.userProfile || '';
}

async function handleSave() {
  if (!form.userName?.trim()) {
    Message.warning('请输入昵称');
    return;
  }
  saving.value = true;
  try {
    await userStore.updateMyUser({
      userName: form.userName.trim(),
      userAvatar: form.userAvatar,
      userProfile: form.userProfile,
    });
    Message.success('保存成功');
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    saving.value = false;
  }
}

onMounted(fillForm);
</script>

<style scoped>
.center-page {
  max-width: 960px;
}

.info-card,
.form-card {
  border-radius: 12px;
  margin-bottom: 16px;
}

.avatar-wrap {
  text-align: center;
  margin: 8px 0 12px;
}

.nickname {
  text-align: center;
  font-size: 18px;
  font-weight: 600;
  color: #1d2129;
}

.role-tag {
  text-align: center;
  margin-top: 8px;
}
</style>
