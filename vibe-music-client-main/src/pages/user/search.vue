<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchUsers } from '@/api/system'
import { ElNotification } from 'element-plus'
import defaultAvatar from '@/assets/user.jpg'

const route = useRoute()
const router = useRouter()

// 搜索关键词
const searchKeyword = ref('')
// 用户列表
const users = ref<any[]>([])
// 加载状态
const loading = ref(false)

// 分页组件状态
const currentPage = ref(1)
const pageSize = ref(20)
const state = reactive({
  size: 'default',
  disabled: false,
  background: false,
  layout: 'total, sizes, prev, pager, next, jumper',
  total: 0,
  pageSizes: [20, 30, 50],
})

// 获取用户搜索结果
const getUsers = async () => {
  if (!searchKeyword.value.trim()) {
    users.value = []
    state.total = 0
    return
  }

  loading.value = true
  try {
    const res = await searchUsers(searchKeyword.value, currentPage.value, pageSize.value)
    if (res.code === 0 && res.data) {
      const data = res.data as any
      users.value = data.items || []
      state.total = data.total || 0
    } else {
      ElNotification({
        type: 'error',
        message: '搜索用户失败',
        duration: 2000,
      })
    }
  } catch (error) {
    ElNotification({
      type: 'error',
      message: '搜索用户失败',
      duration: 2000,
    })
  } finally {
    loading.value = false
  }
}

// 监听分页变化
const handleSizeChange = () => {
  getUsers()
}

// 监听当前页变化
const handleCurrentChange = () => {
  getUsers()
}

// 处理搜索
const handleSearch = () => {
  currentPage.value = 1
  getUsers()
}

// 处理搜索框按下回车
const handleKeyPress = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    handleSearch()
  }
}

// 跳转到用户详情页
const goToUserProfile = (userId: number) => {
  router.push(`/profile/${userId}`)
}

// 监听路由查询参数变化
watch(
  () => route.query.query,
  (newQuery) => {
    if (newQuery) {
      searchKeyword.value = newQuery as string
      getUsers()
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="flex flex-col h-full flex-1 overflow-hidden px-4 py-2">
    <!-- 搜索栏 -->
    <div class="py-4">
      <div class="flex flex-col sm:flex-row gap-4">
        <div class="relative flex-grow">
          <icon-mdi:magnify
            class="lucide lucide-search absolute left-2 top-1/2 transform -translate-y-1/2 text-muted-foreground" />
          <input v-model="searchKeyword" @keydown="handleKeyPress"
            class="flex h-10 rounded-lg border border-input transform duration-300 bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-0 pl-10 w-72"
            placeholder="搜索用户..." type="search" />
        </div>
        <button @click="handleSearch"
          class="text-white inline-flex items-center justify-center gap-2 whitespace-nowrap text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-primary text-primary-foreground hover:bg-primary/90 h-10 rounded-lg px-8">
          <icon-mdi:magnify />
          搜索
        </button>
      </div>
    </div>

    <!-- 搜索结果 -->
    <div class="flex-grow flex flex-col overflow-hidden">
      <div v-if="loading" class="flex-1 flex items-center justify-center">
        <el-skeleton :loading="true" animated :count="5">
          <template #template>
            <div class="flex items-center space-x-4 p-4">
              <el-skeleton-item variant="image" style="width: 60px; height: 60px; border-radius: 50%" />
              <div class="flex-1">
                <el-skeleton-item variant="text" style="width: 200px" />
                <el-skeleton-item variant="text" style="width: 150px; margin-top: 8px" />
              </div>
            </div>
          </template>
        </el-skeleton>
      </div>

      <div v-else-if="users.length === 0 && searchKeyword" class="flex-1 flex items-center justify-center">
        <div class="text-center text-muted-foreground">
          <icon-mdi:account-search class="text-6xl mb-4 mx-auto opacity-50" />
          <p class="text-lg">未找到相关用户</p>
          <p class="text-sm">请尝试其他关键词</p>
        </div>
      </div>

      <div v-else-if="!searchKeyword" class="flex-1 flex items-center justify-center">
        <div class="text-center text-muted-foreground">
          <icon-mdi:account-search class="text-6xl mb-4 mx-auto opacity-50" />
          <p class="text-lg">搜索用户</p>
          <p class="text-sm">输入用户名进行搜索</p>
        </div>
      </div>

      <div v-else class="flex-1 overflow-y-auto">
        <div class="space-y-4">
          <div v-for="user in users" :key="user.userId"
            class="flex items-center p-4 rounded-lg hover:bg-hoverMenuBg cursor-pointer transition-colors"
            @click="goToUserProfile(user.userId)">
            <!-- 用户头像 -->
            <div class="flex-shrink-0 mr-4">
              <img :src="user.userAvatar || defaultAvatar" :alt="user.username"
                class="w-16 h-16 rounded-full object-cover border-2 border-border" />
            </div>
            
            <!-- 用户信息 -->
            <div class="flex-1 min-w-0">
              <h3 class="text-lg font-semibold text-primary truncate">{{ user.username }}</h3>
              <p v-if="user.introduction" class="text-sm text-muted-foreground mt-1 line-clamp-2">
                {{ user.introduction }}
              </p>
              <div class="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
                <span v-if="user.email" class="flex items-center gap-1">
                  <icon-mdi:email />
                  {{ user.email }}
                </span>
                <span v-if="user.phone" class="flex items-center gap-1">
                  <icon-mdi:phone />
                  {{ user.phone }}
                </span>
              </div>
            </div>
            
            <!-- 操作按钮 -->
            <div class="flex-shrink-0">
              <button class="text-primary hover:text-primary/80 transition-colors">
                <icon-mdi:arrow-right class="text-xl" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <nav v-if="users.length > 0" class="mx-auto flex w-full justify-center mt-6">
        <el-pagination v-model:page-size="pageSize" v-model:currentPage="currentPage" v-bind="state"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" class="mb-3" />
      </nav>
    </div>
  </div>
</template>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
