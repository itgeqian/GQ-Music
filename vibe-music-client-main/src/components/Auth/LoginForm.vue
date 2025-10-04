<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Message, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { UserStore } from '@/stores/modules/user'
import { getCaptcha } from '@/api/system'
import { httpGet } from '@/utils/http'

const emit = defineEmits(['success', 'switch-tab'])
const userStore = UserStore()

const loading = ref(false)
const loginFormRef = ref<FormInstance>()

const loginForm = reactive({
  email: '',
  password: '',
  checkCode: '',
  checkCodeKey: '',
  rememberMe: false,
})

// 表单验证规则
const loginRules = reactive<FormRules>({
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?![0-9]+$)(?![a-zA-Z]+$)[0-9A-Za-z\W]{8,18}$/,
      message: '密码格式：8-18位数字、字母、符号的任意两种组合',
      trigger: 'blur',
    },
  ],
  checkCode: [
    { required: true, message: '请输入图形验证码', trigger: 'blur' }
  ],
})
const captcha = reactive({ image: '', loading: false })

const refreshCaptcha = async () => {
  if (captcha.loading) return
  captcha.loading = true
  try {
    const res = await getCaptcha()
    if (res && res.code === 0 && res.data) {
      const data: any = res.data
      captcha.image = data.checkCode
      loginForm.checkCodeKey = data.checkCodeKey
      loginForm.checkCode = ''
    }
  } finally {
    captcha.loading = false
  }
}

// 登录处理
const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const result = await userStore.userLogin(loginForm as any)
        if (result.success) {
          // 记住邮箱
          if (loginForm.rememberMe && loginForm.email) {
            localStorage.setItem('remember_email', loginForm.email)
          } else {
            localStorage.removeItem('remember_email')
          }
          ElMessage.success(result.message)
          emit('success')
        } else {
          ElMessage.error(result.message)
          await refreshCaptcha()
        }
      } catch (error: any) {
        ElMessage.error(error.message || '登录失败')
        await refreshCaptcha()
      } finally {
        loading.value = false
      }
    }
  })
}

function switchToRegister() {
  // 通知父组件切换到注册标签
  emit('switch-tab', 'register')
}

function switchToReset() {
  // 通知父组件切换到重置密码标签
  emit('switch-tab', 'reset')
}

// 微信扫码登录：获取授权URL并跳转
const handleWxScanLogin = async () => {
  try {
    const res: any = await httpGet('/yungou/wx/getAuthorizationUrl')
    if (res && res.code === 0 && typeof res.data === 'string') {
      window.location.href = res.data
    } else {
      ElMessage.error(res?.message || '获取授权链接失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '获取授权链接失败')
  }
}

onMounted(() => {
  refreshCaptcha()
  // 读取记住的邮箱
  const saved = localStorage.getItem('remember_email')
  if (saved) {
    loginForm.email = saved
    loginForm.rememberMe = true
  }
})
</script>

<template>
  <div class="login-container">
    <p class="form-subtitle">输入您的邮箱以登录您的账户</p>

    <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-width="0" size="large"
      @keyup.enter="handleLogin">
      <el-form-item prop="email">
        <el-input v-model="loginForm.email" placeholder="邮箱" :prefix-icon="Message" />
      </el-form-item>

      <el-form-item prop="password" class="mt-6">
        <el-input v-model="loginForm.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
      </el-form-item>

      <el-form-item prop="checkCode" class="mt-6">
        <div style="display:flex; gap:8px; align-items:center; width:100%">
          <el-input v-model="loginForm.checkCode" placeholder="图形验证码" />
          <img v-if="captcha.image" :src="captcha.image" class="captcha-img" :key="captcha.image" @click="refreshCaptcha" title="点击刷新" />
          <el-button @click="refreshCaptcha" :loading="captcha.loading">换一张</el-button>
        </div>
      </el-form-item>

      <el-form-item>
        <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
      </el-form-item>

      <div class="forgot-password">
        <a href="#" @click.prevent="switchToReset">忘记密码？</a>
      </div>

      <el-form-item class="mt-6">
        <el-button class="submit-btn" type="primary" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form-item>

      <el-form-item>
        <el-button class="submit-btn" type="success" @click="handleWxScanLogin">
          微信扫码登录
        </el-button>
      </el-form-item>
    </el-form>

    <p class="signup-text">
      没有账户？
      <a href="#" @click.prevent="switchToRegister">注册</a>
    </p>
  </div>
</template>

<style scoped>
.login-container {
  width: 100%;
  max-width: 400px;
  margin: 0 auto;
  padding: 20px;
}

.form-subtitle {
  color: #666;
  margin-bottom: 24px;
  font-size: 14px;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
}

.submit-btn {
  width: 100%;
  border-radius: 8px;
  height: 40px;
  font-size: 16px;
}

.signup-text {
  text-align: center;
  margin-top: 16px;
  color: #666;
}

.signup-text a {
  color: #2a68fa;
  font-weight: 600;
  text-decoration: none;
}

.signup-text a:hover {
  text-decoration: underline;
}

.forgot-password {
  text-align: right;
  margin: -10px 0 10px;
}

.forgot-password a {
  color: #666;
  font-size: 14px;
  text-decoration: none;
}

.forgot-password a:hover {
  color: #2a68fa;
  text-decoration: underline;
}
.captcha-img { height: 42px; border: 1px solid #eee; cursor: pointer; }
</style>
