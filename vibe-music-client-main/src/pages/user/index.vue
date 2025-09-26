<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserStore } from '@/stores/modules/user'
import defaultAvatar from '@/assets/user.jpg'
import { updateUserInfo, updateUserAvatar, deleteUser, getUserInfo, getUserProfile } from '@/api/system'
import 'vue-cropper/dist/index.css'
import { VueCropper } from "vue-cropper";
import { useRouter } from 'vue-router'
import AuthTabs from '@/components/Auth/AuthTabs.vue'
import { AudioStore } from '@/stores/modules/audio'

const router = useRouter()
const userStore = UserStore()
const loading = ref(false)
const userFormRef = ref<FormInstance>()
const cropperVisible = ref(false)
const cropperImg = ref('')
const cropper = ref<any>(null)
const authVisible = ref(false)
const audioStore = AudioStore()

// 本地偏好
const playerVolume = ref<number>(Number(localStorage.getItem('vmc:pref:volume') || audioStore.volume || 50))
const autoplay = ref<boolean>(localStorage.getItem('vmc:pref:autoplay') === '1')
const resumeProgress = ref<boolean>(localStorage.getItem('vmc:pref:resume') === '1')
const homeRecommend = ref<boolean>(localStorage.getItem('vmc:pref:homeRecommend') !== '0')
const hotkeyHints = ref<boolean>(localStorage.getItem('vmc:pref:hotkeyHints') === '1')
const hotkeysInit = (() => {
  try {
    const raw = localStorage.getItem('vmc:pref:hotkeys')
    if (raw) return JSON.parse(raw)
  } catch {}
  return { playPause: 'Space', prev: 'KeyJ', next: 'KeyL' }
})()
const hotkeys = reactive<{ playPause: string; prev: string; next: string }>(hotkeysInit)
// 外观与布局（已取消UI）保留占位以便未来扩展，但不生效

const userForm = reactive({
  userId: userStore.userInfo.userId,
  username: userStore.userInfo.username || '',
  phone: userStore.userInfo.phone || '',
  email: userStore.userInfo.email || '',
  introduction: userStore.userInfo.introduction || '',
  publicProfile: undefined as undefined | number
})

// 展示用的额外信息（不改库）
const userMeta = reactive({
  status: undefined as undefined | number,
  publicProfile: undefined as undefined | number,
  createTime: '' as string,
  updateTime: '' as string,
})

// 记录原始值，提交时仅发送变更字段，避免后端误判用户名重复
const originalUser = reactive({
  username: userForm.username,
  phone: userForm.phone,
  email: userForm.email,
  introduction: userForm.introduction,
  publicProfile: userForm.publicProfile as any
})

// 已加入天数（根据创建时间）
const daysSince = ref<number>(0)
const updateDaysSince = () => {
  try {
    let basis = userMeta.createTime
    if (!basis) {
      // 后端未返回创建时间，则使用本地首次访问时间作为替代，仅用于“第X天”
      const key = 'vmc:user:firstSeen'
      const cached = localStorage.getItem(key)
      if (cached) basis = cached
      else {
        basis = new Date().toISOString().slice(0, 19).replace('T', ' ')
        localStorage.setItem(key, basis)
      }
    }
    const norm = String(basis).replace('T', ' ').replace(/-/g, '/')
    const created = new Date(norm)
    const now = new Date()
    const diff = Math.max(0, now.getTime() - created.getTime())
    daysSince.value = Math.floor(diff / 86400000) + 1
  } catch { daysSince.value = 0 }
}

// 显示用时间格式化（YYYY-MM-DD HH:mm:ss）
const formatDateTime = (s?: string) => {
  if (!s) return '—'
  const str = String(s).replace('T', ' ')
  return str.length > 19 ? str.slice(0, 19) : str
}

// 表单验证规则
const userRules = reactive<FormRules>({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_-]{4,16}$/,
      message: '用户名格式：4-16位字符（字母、数字、下划线、连字符）',
      trigger: 'blur',
    },
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
  introduction: [
    { max: 100, message: '简介不能超过100个字符', trigger: 'blur' },
  ],
})

// 检查登录状态
onMounted(async () => {
  if (!userStore.isLoggedIn) {
    authVisible.value = true
    return
  }
  // 拉取一次，补充 meta 展示
  try {
    const res = await getUserInfo()
    if (res.code === 0 && res.data) {
      const d: any = res.data
      userMeta.status = d.status
      userMeta.publicProfile = d.publicProfile ?? d.public_profile
      userMeta.createTime = d.createTime || d.create_time || ''
      userMeta.updateTime = d.updateTime || d.update_time || ''
      // 填充表单；若后端未返回公开字段，默认开启（1）
      userForm.username = String(d.username || userForm.username || '')
      userForm.phone = String(d.phone || userForm.phone || '')
      userForm.email = String(d.email || userForm.email || '')
      userForm.introduction = String(d.introduction || userForm.introduction || '')
      userForm.publicProfile = (userMeta.publicProfile ?? 1) as any
      // 记录原始值
      originalUser.username = userForm.username
      originalUser.phone = userForm.phone
      originalUser.email = userForm.email
      originalUser.introduction = userForm.introduction
      originalUser.publicProfile = userForm.publicProfile as any
      updateDaysSince()
    }
  } catch {}
  // 如果仍无时间信息，降级调用 profile 接口
  if (!userMeta.createTime || !userMeta.updateTime) {
    try {
      const uid = Number(userStore.userInfo.userId)
      if (uid) {
        const prof: any = await getUserProfile(uid)
        if (prof?.code === 0 && prof.data) {
          const p = prof.data
          userMeta.createTime = userMeta.createTime || p.createTime || p.create_time || ''
          userMeta.updateTime = userMeta.updateTime || p.updateTime || p.update_time || ''
          updateDaysSince()
        }
      }
    } catch {}
  }
  // 外观与布局偏好已移除，不再应用
})

// 定时刷新“第X天”（每天变更一次，按小时刷新足够）
setInterval(updateDaysSince, 60 * 60 * 1000)

// 处理头像上传
const handleAvatarClick = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = (e: Event) => {
    const target = e.target as HTMLInputElement
    const file = target.files?.[0]
    if (file) {
      const reader = new FileReader()
      reader.onload = (e) => {
        const result = e.target?.result
        if (typeof result === 'string') {
          cropperImg.value = result
          cropperVisible.value = true
        }
      }
      reader.readAsDataURL(file)
    }
  }
  input.click()
}

// 重置裁剪
const reset = () => {
  if (cropper.value) {
    cropper.value.refresh()
  }
}

// 缩放
const changeScale = (num: number) => {
  if (cropper.value) {
    cropper.value.changeScale(num)
  }
}

// 向左旋转
const rotateLeft = () => {
  if (cropper.value) {
    cropper.value.rotateLeft()
  }
}

// 向右旋转
const rotateRight = () => {
  if (cropper.value) {
    cropper.value.rotateRight()
  }
}

// 确认裁剪
const handleCropConfirm = async () => {
  if (!cropper.value) return
  cropper.value.getCropData(async (base64: string) => {
    try {
      const response = await fetch(base64)
      const blob = await response.blob()

      const formData = new FormData()
      formData.append('avatar', blob, 'avatar.png')

      const res = await updateUserAvatar(formData)

      if (res.code === 0) {
        // 重新获取用户信息以更新头像URL
        const userInfoResponse = await getUserInfo()
        if (userInfoResponse.code === 0) {
          userStore.setUserInfo(userInfoResponse.data, userStore.userInfo.token)
          ElMessage.success('头像更新成功')
          cropperVisible.value = false
          cropperImg.value = ''
        } else {
          ElMessage.error(userInfoResponse.message || '获取用户信息失败')
        }
      } else {
        ElMessage.error(res.message || '头像更新失败')
      }
    } catch (error: any) {
      console.error('头像更新错误:', error)
      ElMessage.error(error.message || '头像更新失败')
    }
  })
}

// 处理表单提交
const handleSubmit = async () => {
  if (!userFormRef.value) return
  await userFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // 构建仅包含变更项的 payload，避免未改动的用户名触发唯一校验
        const payload: any = { userId: userForm.userId }
        const trim = (v: any) => typeof v === 'string' ? v.trim() : v
        // 始终携带用户名与邮箱，后端为必填
        payload.username = trim(userForm.username)
        payload.email = trim(userForm.email)
        if (trim(userForm.phone) !== trim(originalUser.phone)) payload.phone = trim(userForm.phone)
        if (trim(userForm.introduction) !== trim(originalUser.introduction)) payload.introduction = trim(userForm.introduction)
        // 公开资料：总是携带（默认1）
        const publicVal = (userForm.publicProfile ?? 1)
        payload.publicProfile = publicVal
        payload.public_profile = publicVal
        const response = await updateUserInfo(payload)
        if (response.code === 0) {
          const userInfoResponse = await getUserInfo()
          userStore.setUserInfo(userInfoResponse.data, userStore.userInfo.token)
          // 同步原始值基线
          originalUser.username = userForm.username
          originalUser.phone = userForm.phone
          originalUser.email = userForm.email
          originalUser.introduction = userForm.introduction
          originalUser.publicProfile = userForm.publicProfile as any
          ElMessage.success('更新成功')
        } else {
          ElMessage.error(response.message || '更新失败')
        }
      } catch (error: any) {
        ElMessage.error(error.message || '更新失败')
      } finally {
        loading.value = false
      }
    }
  })
}

// 重置表单到当前登录信息
const handleReset = () => {
  userForm.username = userStore.userInfo.username || ''
  userForm.email = userStore.userInfo.email || ''
  userForm.phone = userStore.userInfo.phone || ''
  userForm.introduction = userStore.userInfo.introduction || ''
  userForm.publicProfile = userMeta.publicProfile
}

// 偏好：事件处理
const onVolumeChange = (v: number) => {
  playerVolume.value = v
  localStorage.setItem('vmc:pref:volume', String(v))
  audioStore.setAudioStore('volume', v)
}

const onAutoplayChange = (val: boolean) => {
  localStorage.setItem('vmc:pref:autoplay', val ? '1' : '0')
}

const onResumeChange = (val: boolean) => {
  localStorage.setItem('vmc:pref:resume', val ? '1' : '0')
}

// 外观与布局开关已移除
const onHomeRecommendChange = (val: boolean) => {
  localStorage.setItem('vmc:pref:homeRecommend', val ? '1' : '0')
}

const onHotkeyHintsChange = (val: boolean) => {
  localStorage.setItem('vmc:pref:hotkeyHints', val ? '1' : '0')
}

// 是否显示播放栏音频条
const visualizerEnabled = ref<boolean>(localStorage.getItem('vmc:pref:visualizer') !== '0')
const onVisualizerChange = (val: boolean) => {
  localStorage.setItem('vmc:pref:visualizer', val ? '1' : '0')
  // 通知底部组件更新（不强依赖路由刷新）
  try { window.dispatchEvent(new CustomEvent('pref:visualizer:update', { detail: { enabled: val } })) } catch {}
}

const saveHotkeys = () => {
  // 规范化用户输入为 KeyboardEvent.code
  const norm = (v: string) => {
    const s = (v || '').trim()
    if (!s) return 'Space'
    const lower = s.toLowerCase()
    if (lower === 'space' || lower === '空格' || s === ' ') return 'Space'
    if (/^key[a-z]$/i.test(s)) return s[0].toUpperCase() + s.slice(1)
    if (/^[a-z]$/i.test(s)) return 'Key' + s.toUpperCase()
    return s
  }
  hotkeys.playPause = norm(hotkeys.playPause)
  hotkeys.prev = norm(hotkeys.prev)
  hotkeys.next = norm(hotkeys.next)
  localStorage.setItem('vmc:pref:hotkeys', JSON.stringify(hotkeys))
  // 通知全局更新快捷键映射（避免必须刷新）
  try { (window as any).dispatchEvent(new CustomEvent('hotkeys:update', { detail: hotkeys })) } catch {}
  ElMessage.success('快捷键已保存')
}

// 处理账号注销
const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(
      '注销账号后，所有数据将被清除且无法恢复，是否确认注销？',
      '警告',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    loading.value = true
    const response = await deleteUser()
    if (response.code === 0) {
      userStore.clearUserInfo()
      ElMessage.success('账号已注销')
      router.push('/')
    } else {
      ElMessage.error(response.message || '注销失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '注销失败')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="user-container">
    <div class="top-card">
      <div class="top-card-left" @click="handleAvatarClick">
        <div class="avatar-wrapper">
          <el-avatar :src="userStore.userInfo.avatarUrl || defaultAvatar" :size="88" />
          <div class="avatar-hover">
            <icon-ic:outline-photo-camera class="camera-icon" />
            <span>更换头像</span>
          </div>
        </div>
      </div>
      <div class="top-card-right">
        <div class="name-line">
          <span class="display-name">{{ userStore.userInfo.username || '未命名' }}</span>
          <el-tag size="small" type="success" v-if="userMeta.status===0">正常</el-tag>
          <el-tag size="small" type="info" v-else-if="userMeta.status===1">禁用</el-tag>
        </div>
        <div class="meta-line">
          <span v-if="userStore.userInfo.email">{{ userStore.userInfo.email }}</span>
          <span v-if="userStore.userInfo.phone" class="dot">·</span>
          <span v-if="userStore.userInfo.phone">{{ userStore.userInfo.phone }}</span>
        </div>
        <div class="meta-line">
          <span>创建于：{{ formatDateTime(userMeta.createTime) }}</span>
          <span class="dot">·</span>
          <span>更新于：{{ formatDateTime(userMeta.updateTime) }}</span>
        </div>
        <div class="meta-line" v-if="daysSince > 0">
          <icon-mdi:clock-time-eight-outline class="mr-1" /> 已在 GQ Music 的第 {{ daysSince }} 天
        </div>
      </div>
    </div>

    <!-- 头像裁剪弹窗 -->
    <el-dialog v-model="cropperVisible" title="裁剪头像" width="600px" :close-on-click-modal="false"
      :close-on-press-escape="false">
      <div class="cropper-container">
        <vue-cropper ref="cropper" :img="cropperImg" :info="true" :canScale="true" :autoCrop="true" :fixedBox="true"
          :canMove="true" :canMoveBox="true" :centerBox="true" :infoTrue="true" :fixed="true" :fixedNumber="[1, 1]"
          :high="true" mode="cover" :round="true" />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <div class="flex justify-between items-center w-full">
            <div class="flex">
              <el-button size="mini" type="info" @click="reset" class="mr-1">重置</el-button>
              <el-button size="mini" plain @click="changeScale(1)" class="mr-1">
                <icon-ph:magnifying-glass-plus-light class="mr-0.5" />放大
              </el-button>
              <el-button size="mini" plain @click="changeScale(-1)" class="mr-1">
                <icon-ph:magnifying-glass-minus-light class="mr-0.5" />缩小
              </el-button>
              <el-button size="mini" plain @click="rotateLeft" class="mr-1">
                <icon-grommet-icons:rotate-left class="mr-0.5" />左旋转
              </el-button>
              <el-button size="mini" plain @click="rotateRight" class="mr-1">
                <icon-grommet-icons:rotate-right class="mr-0.5" />右旋转
              </el-button>
            </div>
            <div class="flex">
              <el-button size="mini" type="warning" plain @click="cropperVisible = false" class="mr-1">取消</el-button>
              <el-button size="mini" type="primary" @click="handleCropConfirm">确认</el-button>
            </div>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="0" size="large" class="user-form grid">
      <div class="grid-2col">
        <div class="section">
          <div class="section-title">用户名</div>
          <el-form-item prop="username">
            <el-input v-model="userForm.username" placeholder="用户名" />
          </el-form-item>
        </div>
        <div class="section">
          <div class="section-title">邮箱</div>
          <el-form-item prop="email">
            <el-input v-model="userForm.email" placeholder="邮箱" />
          </el-form-item>
        </div>
        <div class="section">
          <div class="section-title">联系电话</div>
          <el-form-item prop="phone">
            <el-input v-model="userForm.phone" placeholder="联系电话" />
          </el-form-item>
        </div>
        <div class="section">
          <div class="section-title">简介</div>
          <el-form-item prop="introduction">
            <el-input v-model="userForm.introduction" type="textarea" :rows="4" placeholder="编辑个人简介" maxlength="100" show-word-limit />
          </el-form-item>
        </div>

        <!-- 隐私与展示 -->
        <div class="section">
          <div class="section-title">隐私与展示</div>
          <el-form-item>
            <el-switch v-model="userForm.publicProfile" :active-value="1" :inactive-value="0" />
            <span class="ml-2 text-sm">公开我的资料（个人主页可见）</span>
          </el-form-item>
        </div>

        <!-- 播放器偏好 -->
        <div class="section">
          <div class="section-title">播放器偏好</div>
          <div class="flex items-center gap-4">
            <div class="flex items-center gap-2">
              <span class="text-sm w-20 text-right">默认音量</span>
              <el-slider v-model="playerVolume" :max="100" class="!w-56" @change="onVolumeChange" />
              <span class="text-xs w-8">{{ playerVolume }}%</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-sm">自动播放</span>
              <el-switch v-model="autoplay" @change="onAutoplayChange" />
            </div>
            <div class="flex items-center gap-2">
              <span class="text-sm">恢复进度</span>
              <el-switch v-model="resumeProgress" @change="onResumeChange" />
            </div>
            <div class="flex items-center gap-2">
              <span class="text-sm">播放栏音频条</span>
              <el-switch v-model="visualizerEnabled" @change="onVisualizerChange" />
            </div>
          </div>
        </div>

        <!-- 列表与布局 -->
        <div class="section">
          <div class="section-title">列表与布局</div>
          <div class="flex items-center gap-4">
            <div class="flex items-center gap-2">
              <span class="text-sm">首页“今日为你推荐”</span>
              <el-switch v-model="homeRecommend" @change="onHomeRecommendChange" />
            </div>
          </div>
        </div>

        <!-- 辅助功能 -->
        <div class="section">
          <div class="section-title">辅助功能</div>
          <div class="flex items-center gap-4">
            <div class="flex items-center gap-2">
              <span class="text-sm">显示快捷键提示</span>
              <el-switch v-model="hotkeyHints" @change="onHotkeyHintsChange" />
            </div>
            <div class="flex items-center gap-2">
              <span class="text-sm">快捷键</span>
              <el-input v-model="hotkeys.playPause" style="width: 110px" placeholder="播放/暂停" />
              <el-input v-model="hotkeys.prev" style="width: 110px" placeholder="上一首" />
              <el-input v-model="hotkeys.next" style="width: 110px" placeholder="下一首" />
              <el-button size="small" @click="saveHotkeys">保存快捷键</el-button>
            </div>
          </div>
        </div>

        
      </div>

      <div class="action-bar">
        <div class="action-inner">
          <div class="left">
            <el-button @click="handleReset">重置</el-button>
          </div>
          <div class="right">
            <el-button type="danger" :loading="loading" @click="handleDelete">注销账号</el-button>
            <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
          </div>
        </div>
      </div>
    </el-form>

    <!-- 登录对话框 -->
    <AuthTabs v-model="authVisible" />
  </div>
</template>

<style scoped>
.user-container {
  max-width: 100%;
  margin: 12px 16px 0 16px;
  padding: 20px 24px 12px;
  background-color: transparent;
  backdrop-filter: none;
  border-radius: 12px;
  box-shadow: none;
}

.top-card {
  display: flex;
  align-items: center;
  padding: 16px;
  border-radius: 12px;
  background-color: var(--el-fill-color-blank);
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
  margin-bottom: 16px;
}

.top-card-left {
  margin-right: 16px;
}

.top-card-right {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.display-name {
  font-size: 18px;
  font-weight: 600;
}

.meta-line {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.meta-line .dot {
  margin: 0 6px;
}

.user-header {
  text-align: left;
  margin-bottom: 20px;
  display: flex;
}

.username {
  margin: 0 0 16px 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
  font-weight: normal;
}

.user-form {
  max-width: 100%;
  margin: 0;
}

.grid-2col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 24px;
}

@media (max-width: 900px) {
  .grid-2col {
    grid-template-columns: 1fr;
  }
}

:deep(.el-form-item) {
  margin-bottom: 24px;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
  background-color: var(--el-fill-color-blank);
  box-shadow: 0 0 0 1px var(--el-border-color) inset !important;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color-hover) inset !important;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset !important;
}

.submit-btn {
  border-radius: 8px;
  width: 140px;
}

:deep(.el-textarea__inner) {
  border-radius: 8px;
  resize: none;
  background-color: var(--el-fill-color-blank);
  box-shadow: 0 0 0 1px var(--el-border-color) inset !important;
}

:deep(.el-textarea__inner:hover) {
  box-shadow: 0 0 0 1px var(--el-border-color-hover) inset !important;
}

:deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset !important;
}

:deep(.el-input.is-disabled .el-input__wrapper) {
  background-color: var(--el-fill-color-blank);
  box-shadow: 0 0 0 1px var(--el-border-color-light) inset !important;
  cursor: not-allowed;
}

.section {
  margin-bottom: 24px;
}

.section-title {
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.avatar-wrapper {
  position: relative;
  cursor: pointer;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  overflow: hidden;
}

.avatar-hover {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  opacity: 0;
  transition: opacity 0.3s;
  color: white;
  font-size: 14px;
}

.avatar-hover .camera-icon {
  font-size: 24px;
  margin-bottom: 4px;
}

.avatar-wrapper:hover .avatar-hover {
  opacity: 1;
}

.button-group {
  margin-top: 40px;
}

.action-bar {
  position: sticky;
  bottom: 8px;
  margin-top: 12px;
}

.action-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 10px;
  background-color: var(--el-fill-color-blank);
  box-shadow: 0 0 0 1px var(--el-border-color) inset, 0 6px 20px rgba(0,0,0,0.08);
}

/* 外观与布局样式已移除 */

.cropper-container {
  width: 100%;
  height: 400px;
}

:deep(.el-dialog__body) {
  padding-top: 10px;
}
</style>
