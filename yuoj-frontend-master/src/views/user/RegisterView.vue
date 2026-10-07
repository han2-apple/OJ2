<template>
  <div class="auth-page">
    <a-card class="auth-card" :bordered="false">
      <h2 class="auth-title">注册</h2>
      <p class="auth-sub">创建账号，开始你的刷题之旅</p>

      <a-form ref="formRef" :model="form" layout="vertical">
        <a-form-item
          field="userAccount"
          label="账号"
          :rules="[
            {
              validator: (value, callback) => {
                if (!value) callback('请输入账号');
                else if (value.length < 4) callback('账号长度不能少于 4 位');
                else callback();
              },
            },
          ]"
          validate-trigger="blur"
        >
          <a-input v-model="form.userAccount" placeholder="4-16 位字母或数字" size="large">
            <template #prefix><icon-user /></template>
          </a-input>
        </a-form-item>

        <a-form-item
          field="userPassword"
          label="密码"
          :rules="[
            {
              validator: (value, callback) => {
                if (!value) callback('请输入密码');
                else if (value.length < 8) callback('密码长度不能少于 8 位');
                else callback();
              },
            },
          ]"
          validate-trigger="blur"
        >
          <a-input-password
            v-model="form.userPassword"
            placeholder="至少 8 位"
            size="large"
            allow-clear
          >
            <template #prefix><icon-lock /></template>
          </a-input-password>
        </a-form-item>

        <a-form-item
          field="checkPassword"
          label="确认密码"
          :rules="[
            { required: true, message: '请再次输入密码' },
            {
              validator: (value, callback) => {
                if (value !== form.userPassword) {
                  callback('两次输入的密码不一致');
                } else {
                  callback();
                }
              },
            },
          ]"
          validate-trigger="blur"
        >
          <a-input-password
            v-model="form.checkPassword"
            placeholder="请再次输入密码"
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
          注册
        </a-button>
      </a-form>

      <div class="auth-foot">
        已有账号？
        <router-link to="/user/login">去登录</router-link>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import { useUserStore } from '@/store/user';

const router = useRouter();
const userStore = useUserStore();

const formRef = ref();
const loading = ref(false);
const form = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
});

async function handleSubmit() {
  const errors = await formRef.value?.validate().catch((e) => e);
  if (errors) return;

  loading.value = true;
  try {
    await userStore.register(
      form.userAccount,
      form.userPassword,
      form.checkPassword
    );
    Message.success('注册成功，请登录');
    router.push('/user/login');
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
  max-width: 440px;
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
