<template>
  <div class="auth-page">
    <a-card class="auth-card" :bordered="false">
      <h2 class="auth-title">登录</h2>
      <p class="auth-sub">欢迎回到在线判题系统</p>

      <a-form ref="formRef" :model="form" layout="vertical">
        <a-form-item
          field="userAccount"
          label="账号"
          :rules="[{ required: true, message: '请输入账号' }]"
          validate-trigger="blur"
        >
          <a-input v-model="form.userAccount" placeholder="请输入账号" size="large">
            <template #prefix><icon-user /></template>
          </a-input>
        </a-form-item>

        <a-form-item
          field="userPassword"
          label="密码"
          :rules="[{ required: true, message: '请输入密码' }]"
          validate-trigger="blur"
        >
          <a-input-password
            v-model="form.userPassword"
            placeholder="请输入密码"
            size="large"
            allow-clear
          >
            <template #prefix><icon-lock /></template>
          </a-input-password>
        </a-form-item>

        <a-button
          type="primary"
          long
          size="large"
          :loading="loading"
          @click="handleSubmit"
        >
          登录
        </a-button>
      </a-form>

      <div class="auth-foot">
        还没有账号？
        <router-link to="/user/register">立即注册</router-link>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import { useUserStore } from '@/store/user';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const formRef = ref();
const loading = ref(false);
const form = reactive({
  userAccount: '',
  userPassword: '',
});

async function handleSubmit() {
  const errors = await formRef.value?.validate().catch((e) => e);
  if (errors) return;

  loading.value = true;
  try {
    await userStore.login(form.userAccount, form.userPassword);
    Message.success('登录成功');
    const redirect = route.query.redirect;
    router.replace(typeof redirect === 'string' && redirect ? redirect : '/');
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 116px);
  padding: 24px;
}

.auth-card {
  width: 100%;
  max-width: 420px;
  border-radius: 14px;
  box-shadow: 0 6px 24px rgba(0, 21, 41, 0.06);
}

.auth-title {
  text-align: center;
  margin: 8px 0 4px;
  font-size: 26px;
  color: #1d2129;
}

.auth-sub {
  text-align: center;
  color: #86909c;
  margin: 0 0 24px;
}

.auth-foot {
  text-align: center;
  margin-top: 18px;
  color: #4e5969;
  font-size: 14px;
}
</style>
