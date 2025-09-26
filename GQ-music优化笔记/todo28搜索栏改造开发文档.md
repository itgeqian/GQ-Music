# todo28搜索栏改造开发文档

## 项目概述

将原有的多类型搜索栏（歌曲/歌手/专辑/用户）简化为两个选项：**音乐**和**用户**，其中音乐搜索统一使用library页面，用户搜索使用独立的用户搜索页面。

## 功能需求

- 简化搜索类型选择，只保留"音乐"和"用户"两个选项
- 音乐搜索（歌曲/歌手/专辑）统一跳转到 `/library?query=xxx`
- 用户搜索跳转到 `/user/search?query=xxx`
- 保持原有的热搜功能和搜索体验

## 技术实现

### 1. 前端搜索栏改造

#### 文件位置
`vibe-music-client-main/src/layout/components/header/index.vue`

#### 关键代码实现

```vue
<script setup lang="ts">
// 搜索相关状态
const searchText = ref('')
const searchType = ref('music') // 默认搜索音乐
const hotVisible = ref(false)
const hotList = ref<string[]>([])
let hotTimer: number | null = null

// 搜索类型选项 - 简化为两个选项
const searchTypes = [
  { label: '音乐', value: 'music' },
  { label: '用户', value: 'users' }
]

// 搜索处理逻辑
function handleEnter(val?: string) {
  const q = (val ?? searchText.value).toString()
  if (!q.trim()) return
  
  // 主动上报一次，确保计数即时+1
  reportKeyword(q).catch(() => {})
  
  // 根据搜索类型跳转到不同页面
  let target = ''
  if (searchType.value === 'users') {
    target = '/user/search?query=' + encodeURIComponent(q)
  } else {
    // 音乐搜索（歌曲/歌手/专辑）都使用library页面
    target = '/library?query=' + encodeURIComponent(q)
  }
  
  // 若已在相同页面且关键词相同，强制刷新一次（附带时间戳）
  const currentPath = route.path.split('?')[0]
  const targetPath = target.split('?')[0]
  if (currentPath === targetPath && (route.query.query as string) === q) {
    router.replace(target + '&t=' + Date.now())
  } else {
    router.push(target)
  }
}
</script>

<template>
  <header class="px-4 py-2 flex items-center bg-transparent">
    <!-- Logo区域 -->
    <button class="flex relative w-60" @click="router.push('/')">
      <img src="\logo.svg" alt="logo" class="w-10 h-10 ml-2" />
      <span class="ml-3 text-2xl font-bold flex justify-center items-center">GQ Music</span>
    </button>
    
    <!-- 搜索区域 -->
    <div class="flex items-center gap-3">
      <div class="relative mr-6 flex items-center">
        <!-- 搜索类型选择器 -->
        <el-select v-model="searchType" class="w-20 mr-2" size="small">
          <el-option
            v-for="type in searchTypes"
            :key="type.value"
            :label="type.label"
            :value="type.value"
          />
        </el-select>
        
        <!-- 搜索输入框 -->
        <div class="relative">
          <Icon
            icon="mdi:magnify"
            class="absolute left-2 top-1/2 transform -translate-y-1/2 text-gray-500 text-xl"
          />
          <input
            v-model="searchText"
            type="text"
            class="mt-0.5 w-64 text-sm pl-8 pr-2 py-2 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary transition-all duration-300 focus:w-80 search-bg"
            :placeholder="`搜索${searchTypes.find(t => t.value === searchType)?.label || '音乐'}...`"
            @focus="onFocus"
            @input="onInput"
            @blur="onBlur"
            @keyup.enter="handleEnter()"
          />
          
          <!-- 热搜下拉框 -->
          <div v-show="hotVisible && hotList.length" class="absolute left-0 right-0 top-10 bg-white dark:bg-zinc-800 rounded-md shadow-lg z-50">
            <div class="px-3 py-2 text-xs text-gray-500">热搜</div>
            <div class="px-2 pb-2 grid grid-cols-2 gap-x-4">
              <ul>
                <li v-for="(k,i) in hotLeft" :key="'l-'+i"
                    class="flex items-center px-1 py-2 hover:bg-gray-100 dark:hover:bg-zinc-700 cursor-pointer text-sm rounded"
                    @mousedown.prevent @click="useHot(k)">
                  <span class="mr-2 inline-flex w-5 justify-center text-[10px] font-semibold"
                        :class="(i*2+1) <= 3 ? 'text-red-500' : 'text-gray-400'">{{ i*2+1 }}</span>
                  <span class="truncate">{{ k }}</span>
                </li>
              </ul>
              <ul>
                <li v-for="(k,i) in hotRight" :key="'r-'+i"
                    class="flex items-center px-1 py-2 hover:bg-gray-100 dark:hover:bg-zinc-700 cursor-pointer text-sm rounded"
                    @mousedown.prevent @click="useHot(k)">
                  <span class="mr-2 inline-flex w-5 justify-center text-[10px] font-semibold"
                        :class="(i*2+2) <= 3 ? 'text-red-500' : 'text-gray-400'">{{ i*2+2 }}</span>
                  <span class="truncate">{{ k }}</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 其他功能按钮 -->
      <button @click="toggleMode">
        <Icon class="text-xl" :icon="currentIcon" />
      </button>
    </div>
    
    <!-- 用户头像区域 -->
    <div class="ml-auto flex items-center gap-3">
      <ThemeSwitcher />
      <Avatar />
    </div>
  </header>
</template>
```

### 2. 后端用户搜索API

#### 文件位置
`src/main/java/cn/edu/seig/vibemusic/controller/UserController.java`

#### 关键代码实现

```java
/** 搜索用户 */
@GetMapping("/search")
public Result<PageResult<UserVO>> searchUsers(@RequestParam("keyword") String keyword,
                                              @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                              @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
    return userService.searchUsers(keyword, pageNum, pageSize);
}
```

#### 服务层实现
`src/main/java/cn/edu/seig/vibemusic/service/impl/UserServiceImpl.java`

```java
/**
 * 搜索用户
 *
 * @param keyword 搜索关键词
 * @param pageNum 页码
 * @param pageSize 每页大小
 * @return 结果
 */
@Override
public Result<PageResult<UserVO>> searchUsers(String keyword, Integer pageNum, Integer pageSize) {
    // 分页查询
    Page<User> page = new Page<>(pageNum, pageSize);
    QueryWrapper<User> queryWrapper = new QueryWrapper<>();
    
    // 根据关键词搜索用户名
    if (keyword != null && !keyword.trim().isEmpty()) {
        queryWrapper.like("username", keyword.trim());
    }
    
    // 只查询启用的用户
    queryWrapper.eq("status", UserStatusEnum.ENABLE.getId());
    
    // 倒序排序
    queryWrapper.orderByDesc("create_time");

    IPage<User> userPage = userMapper.selectPage(page, queryWrapper);
    if (userPage.getRecords().size() == 0) {
        return Result.success(MessageConstant.DATA_NOT_FOUND, new PageResult<>(0L, null));
    }

    // 转换为 UserVO
    List<UserVO> userVOList = userPage.getRecords().stream()
            .map(user -> {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
                return userVO;
            }).toList();

    return Result.success(new PageResult<>(userPage.getTotal(), userVOList));
}
```

### 3. 前端API接口

#### 文件位置
`vibe-music-client-main/src/api/system.ts`

```typescript
/** 搜索用户 */
export const searchUsers = (keyword: string, pageNum: number = 1, pageSize: number = 20) => {
  return http<Result>('get', '/user/search', { params: { keyword, pageNum, pageSize } })
}
```

### 4. 用户搜索页面

#### 文件位置
`vibe-music-client-main/src/pages/user/search.vue`

#### 关键功能实现

```vue
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
```

### 5. 路由配置

#### 文件位置
`vibe-music-client-main/src/routers/index.ts`

```typescript
{
  path: '/user/search',
  component: () => import('@/pages/user/search.vue'),
}
```

## 功能特点

### 1. 搜索类型简化
- **音乐搜索**: 统一处理歌曲、歌手、专辑搜索，都跳转到library页面
- **用户搜索**: 独立的用户搜索功能，跳转到专门的用户搜索页面

### 2. 搜索体验优化
- 动态占位符文本，根据选择的搜索类型显示对应提示
- 保持原有的热搜功能
- 支持回车键搜索和点击搜索按钮

### 3. 用户搜索功能
- 支持按用户名模糊搜索
- 分页显示搜索结果
- 显示用户头像、用户名、简介等信息
- 点击用户可跳转到用户详情页

## 使用示例

### 音乐搜索
```
选择类型: 音乐
搜索关键词: 周杰伦
跳转URL: /library?query=周杰伦
```

### 用户搜索
```
选择类型: 用户
搜索关键词: 张三
跳转URL: /user/search?query=张三
```

## 技术栈

- **前端**: Vue 3 + TypeScript + Element Plus
- **后端**: Spring Boot + MyBatis Plus
- **数据库**: MySQL
- **状态管理**: Pinia

## 注意事项

1. 确保后端UserService接口正确实现
2. 前端API调用需要处理错误情况
3. 用户搜索页面需要处理空结果状态
4. 保持与现有library搜索功能的兼容性

## 扩展建议

1. 可以添加搜索历史功能
2. 支持搜索建议和自动完成
3. 添加搜索结果的排序和筛选功能
4. 支持高级搜索选项
