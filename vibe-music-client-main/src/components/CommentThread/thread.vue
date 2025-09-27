<script setup lang="ts">
import { ref, reactive } from 'vue'
import EmojiPicker from '@/components/EmojiPicker.vue'
import ImageUpload from '@/components/ImageUpload.vue'

// 为了递归自引用，必须声明组件名，模板里的 <CommentThread> 才能解析
defineOptions({ name: 'CommentThread' })

const props = defineProps<{
  list: Array<any>
  onReply: (payload: { content: string; imgPath?: string; replyCommentId?: number }) => Promise<void>
  currentUsername?: string
}>()

const emit = defineEmits<{ (e: 'delete', id: number): void; (e: 'like', comment: any): void; (e: 'profile', userId: number): void }>()

const replyBoxOf = reactive<Record<number, boolean>>({})
const content = ref('')
const img = ref('')
const imgRef = ref()

// 图片预览相关
const showImageDialog = ref(false)
const previewUrl = ref('')

function openReply(id: number) {
  for (const k in replyBoxOf) delete replyBoxOf[Number(k)]
  replyBoxOf[id] = true
  content.value = ''
  img.value = ''
  if (imgRef.value) imgRef.value.imageUrl = ''
}

// 关闭回复框
function closeReply(id: number) {
  replyBoxOf[id] = false
  content.value = ''
  img.value = ''
  if (imgRef.value) imgRef.value.imageUrl = ''
}

// 关闭所有回复框
function closeAllReplies() {
  for (const k in replyBoxOf) delete replyBoxOf[Number(k)]
  content.value = ''
  img.value = ''
  if (imgRef.value) imgRef.value.imageUrl = ''
}

function handleEmoji(e: string) { content.value += e }
function handleUpload(url: string) { img.value = url }
function handleRemove() { img.value = ''; if (imgRef.value) imgRef.value.imageUrl = '' }

// 图片预览处理
const previewImage = (imageUrl: string) => {
  previewUrl.value = imageUrl
  showImageDialog.value = true
}

async function submit(replyCommentId: number) {
  await props.onReply({ content: content.value.trim(), imgPath: img.value || undefined, replyCommentId })
  content.value = ''
  handleRemove()
  // 提交成功后收起当前回复框
  replyBoxOf[replyCommentId] = false
}
</script>

<template>
  <div class="space-y-4" @click="closeAllReplies">
    <template v-for="c in list" :key="c.commentId">
      <div class="flex items-start gap-3" @click.stop>
        <img :src="c.userAvatar || ''" class="w-10 h-10 rounded-full object-cover bg-muted cursor-pointer" @click="emit('profile', c.userId)" />
        <div class="flex-1 min-w-0">
          <div class="text-sm text-muted-foreground"><span class="text-blue-500 cursor-pointer" @click="emit('profile', c.userId)">{{ c.username }}</span> · {{ c.createTime }}</div>
          <p v-if="c.content || c.replyNickName" class="mt-1 text-sm whitespace-pre-wrap">
            <template v-if="c.replyNickName">回复 <span class="text-blue-500">@{{ c.replyNickName }}</span> </template>{{ c.content || '' }}
          </p>
          <div v-if="c.imgPath" class="mt-2">
            <img :src="c.imgPath" class="max-w-xs max-h-48 object-cover rounded-lg cursor-pointer hover:opacity-80 transition-opacity" @click="previewImage(c.imgPath)" />
          </div>
          <div class="mt-2 text-xs text-muted-foreground flex items-center gap-4">
            <button class="hover:text-foreground" title="回复" @click="openReply(c.commentId)">回复</button>
            <button class="hover:text-gray-600 inline-flex items-center gap-1" title="点赞" @click="emit('like', c)">
              <icon-material-symbols:thumb-up />
              <span>{{ c.likeCount || 0 }}</span>
            </button>
            <el-popconfirm
              v-if="currentUsername===c.username"
              title="确认删除该评论？"
              confirm-button-text="删除"
              cancel-button-text="取消"
              @confirm="emit('delete', c.commentId)"
            >
              <template #reference>
                <button class="hover:text-red-500 inline-flex items-center gap-1" title="删除">
                  <icon-material-symbols:delete-outline />
                  <span>删除</span>
                </button>
              </template>
            </el-popconfirm>
          </div>
          <div v-if="replyBoxOf[c.commentId]" class="mt-3" @click.stop>
            <el-input v-model="content" type="textarea" :rows="3" maxlength="180" show-word-limit placeholder="回复内容" />
            <div v-if="img" class="mt-2">
              <img :src="img" class="w-20 h-20 object-cover rounded-lg border cursor-pointer hover:opacity-80 transition-opacity" @click="previewImage(img)" />
            </div>
            <div class="flex items-center justify-between mt-2">
              <div class="flex items-center gap-2">
                <EmojiPicker @select="handleEmoji" />
                <ImageUpload ref="imgRef" @upload="handleUpload" @remove="handleRemove" />
              </div>
              <div class="flex items-center gap-2">
                <el-button @click="closeReply(c.commentId)">取消</el-button>
                <el-button type="primary" :disabled="!content.trim() && !img" @click="submit(c.commentId)">回复</el-button>
              </div>
            </div>
          </div>
          <div v-if="c.children && c.children.length" class="pl-6 mt-3 border-l border-gray-300/50">
            <CommentThread :list="c.children" :on-reply="props.onReply" @like="emit('like', $event)" @delete="emit('delete', $event)" @profile="emit('profile', $event)" :current-username="currentUsername" />
          </div>
        </div>
      </div>
    </template>
  </div>

  <!-- 图片预览对话框 -->
  <el-dialog v-model="showImageDialog" title="图片预览" width="600px" append-to-body>
    <div class="w-full flex justify-center items-center">
      <img v-if="showImageDialog" :src="previewUrl" style="max-width:100%;max-height:70vh;object-fit:contain;" />
    </div>
  </el-dialog>
</template>

<style scoped>
</style>


