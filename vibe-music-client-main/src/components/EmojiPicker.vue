<template>
  <el-popover
    ref="popoverRef"
    :width="400"
    trigger="click"
    :show-arrow="false"
    :offset="5"
    placement="bottom-start"
  >
    <template #reference>
      <div class="emoji-trigger">
        <svg class="w-5 h-5 text-gray-500 hover:text-primary cursor-pointer" fill="currentColor" viewBox="0 0 20 20">
          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM7 9a1 1 0 100-2 1 1 0 000 2zm7-1a1 1 0 11-2 0 1 1 0 012 0zm-.464 5.535a1 1 0 10-1.415-1.414 3 3 0 01-4.242 0 1 1 0 00-1.415 1.414 5 5 0 007.072 0z" clip-rule="evenodd"></path>
        </svg>
      </div>
    </template>
    <template #default>
      <div class="emoji-picker" @click.stop>
        <el-tabs v-model="activeEmoji" class="emoji-tabs">
          <el-tab-pane
            v-for="emoji in emojiList"
            :key="emoji.name"
            :label="emoji.name"
            :name="emoji.name"
          >
            <div class="emoji-list">
              <div
                v-for="item in emoji.emojiList"
                :key="item"
                class="emoji-item"
                @click="selectEmoji(item)"
              >
                {{ item }}
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
  </el-popover>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import emojiList from '@/utils/Emoji.js'

const emit = defineEmits<{
  select: [emoji: string]
}>()

const popoverRef = ref()
const activeEmoji = ref('笑脸')

const selectEmoji = (emoji: string) => {
  emit('select', emoji)
  popoverRef.value.hide()
}
</script>

<style scoped>
.emoji-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.emoji-trigger:hover {
  background-color: rgba(0, 0, 0, 0.05);
}

.emoji-picker {
  max-height: 300px;
  overflow: hidden;
}

.emoji-tabs {
  height: 100%;
}

.emoji-tabs :deep(.el-tabs__content) {
  height: 240px;
  overflow-y: auto;
}

.emoji-list {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  padding: 8px;
}

.emoji-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 20px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.emoji-item:hover {
  background-color: #f0f0f0;
}

/* 滚动条样式 */
.emoji-tabs :deep(.el-tabs__content)::-webkit-scrollbar {
  width: 6px;
}

.emoji-tabs :deep(.el-tabs__content)::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.emoji-tabs :deep(.el-tabs__content)::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

.emoji-tabs :deep(.el-tabs__content)::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}
</style>
