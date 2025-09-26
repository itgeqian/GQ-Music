<template>
  <div class="image-upload">
    <!-- 上传按钮 -->
    <el-upload
      ref="uploadRef"
      :multiple="false"
      :show-file-list="false"
      :http-request="handleUpload"
      :accept="imageAccept"
      :before-upload="beforeUpload"
    >
      <div class="upload-trigger">
        <svg class="w-5 h-5 text-gray-500 hover:text-primary cursor-pointer" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M4 3a2 2 0 00-2 2v10a2 2 0 002 2h12a2 2 0 002-2V5a2 2 0 00-2-2H4zm12 12H4l4-8 3 6 2-4 3 6z" clip-rule="evenodd"></path>
        </svg>
      </div>
    </el-upload>

  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadCommentImage } from '@/api/system'

const emit = defineEmits<{
  upload: [imageUrl: string]
  remove: []
}>()

const uploadRef = ref()
const imageUrl = ref('')
const imageAccept = 'image/jpeg,image/jpg,image/png,image/gif,image/webp'

// 暴露图片URL给父组件
defineExpose({
  imageUrl: imageUrl
})

const beforeUpload = (file: File) => {
  // 检查文件类型
  const isValidType = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)
  if (!isValidType) {
    ElMessage.error('只能上传 JPG/PNG/GIF/WEBP 格式的图片!')
    return false
  }

  // 检查文件大小 (5MB)
  const isValidSize = file.size / 1024 / 1024 < 5
  if (!isValidSize) {
    ElMessage.error('图片大小不能超过 5MB!')
    return false
  }

  return true
}

const handleUpload = async (options: any) => {
  const file = options.file
  
  try {
    // 先显示本地预览
    const reader = new FileReader()
    reader.onload = (e) => {
      imageUrl.value = e.target?.result as string
    }
    reader.readAsDataURL(file)

    // 上传到服务器
    const response = await uploadCommentImage(file)
    console.log('图片上传响应:', response) // 调试日志
    
    if (response.code === 0) {
      // 上传成功后，使用服务器返回的URL
      // 如果data为空，尝试从message中获取URL
      let serverImageUrl = response.data
      if (!serverImageUrl && response.message) {
        // 检查message是否是URL格式
        if (response.message.startsWith('http')) {
          serverImageUrl = response.message
        }
      }
      
      console.log('解析后的图片URL:', serverImageUrl) // 调试日志
      
      if (serverImageUrl) {
        imageUrl.value = String(serverImageUrl)
        emit('upload', String(serverImageUrl))
        ElMessage.success('图片上传成功')
      } else {
        ElMessage.error('图片上传失败：未获取到图片URL')
        imageUrl.value = ''
      }
    } else {
      ElMessage.error(String(response.message) || '图片上传失败')
      imageUrl.value = ''
    }
  } catch (error) {
    console.error('图片上传失败:', error)
    ElMessage.error('图片上传失败')
    imageUrl.value = ''
  }
}

const removeImage = () => {
  imageUrl.value = ''
  emit('remove')
}
</script>

<style scoped>
.image-upload {
  position: relative;
}

.upload-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.upload-trigger:hover {
  background-color: rgba(0, 0, 0, 0.05);
}

.image-preview {
  position: relative;
  margin-top: 8px;
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-actions {
  position: absolute;
  top: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 0 8px 0 8px;
}

.remove-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  transition: background-color 0.2s;
}

.remove-btn:hover {
  background-color: rgba(255, 255, 255, 0.2);
}
</style>
