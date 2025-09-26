<script setup lang="ts">
import { ref, reactive, watch } from "vue";
import AlbumSelector from "./albumSelector.vue";
import TableImport from "./tableImport.vue";

interface FormProps {
  formInline: {
    title: string;
    artistId: number | null;
    artistName: string;
  };
}

const props = withDefaults(defineProps<FormProps>(), {
  formInline: () => ({
    title: "批量导入",
    artistId: null,
    artistName: ""
  })
});

const emit = defineEmits<{
  success: [];
  cancel: [];
}>();

// 当前步骤：1-选择专辑，2-表格导入
const currentStep = ref(1);
const selectedAlbum = ref("");

// 表单数据
const form = reactive({
  artistId: props.formInline.artistId,
  artistName: props.formInline.artistName,
  albumName: ""
});

// 监听props变化
watch(
  () => props.formInline,
  (newVal) => {
    form.artistId = newVal.artistId;
    form.artistName = newVal.artistName;
  },
  { immediate: true, deep: true }
);

// 处理专辑选择完成
const handleAlbumSelected = (albumName: string) => {
  selectedAlbum.value = albumName;
  form.albumName = albumName;
  currentStep.value = 2;
};

// 处理返回专辑选择
const handleBackToAlbum = () => {
  currentStep.value = 1;
  selectedAlbum.value = "";
  form.albumName = "";
};

// 处理导入成功
const handleImportSuccess = () => {
  emit('success');
};

// 处理取消
const handleCancel = () => {
  emit('cancel');
};
</script>

<template>
  <div class="new-batch-import-container">
    <!-- 步骤指示器 -->
    <div class="step-indicator">
      <div class="step-item" :class="{ active: currentStep >= 1, completed: currentStep > 1 }">
        <div class="step-number">1</div>
        <div class="step-title">选择专辑</div>
      </div>
      <div class="step-line" :class="{ active: currentStep > 1 }"></div>
      <div class="step-item" :class="{ active: currentStep >= 2 }">
        <div class="step-number">2</div>
        <div class="step-title">批量导入</div>
      </div>
    </div>

    <!-- 步骤内容 -->
    <div class="step-content">
      <!-- 步骤1：选择专辑 -->
      <AlbumSelector
        v-if="currentStep === 1"
        :formInline="{
          title: '选择专辑',
          artistId: form.artistId,
          artistName: form.artistName
        }"
        @next="handleAlbumSelected"
        @cancel="handleCancel"
      />

      <!-- 步骤2：表格导入 -->
      <TableImport
        v-if="currentStep === 2"
        :formInline="{
          title: '批量导入',
          artistId: form.artistId,
          artistName: form.artistName,
          albumName: form.albumName
        }"
        @success="handleImportSuccess"
        @back="handleBackToAlbum"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.new-batch-import-container {
  .step-indicator {
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 30px;
    padding: 20px;
    background-color: #f5f7fa;
    border-radius: 8px;
    
    .step-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      position: relative;
      
      .step-number {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        background-color: #dcdfe6;
        color: #909399;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: bold;
        margin-bottom: 8px;
        transition: all 0.3s;
      }
      
      .step-title {
        font-size: 14px;
        color: #909399;
        transition: all 0.3s;
      }
      
      &.active {
        .step-number {
          background-color: #409eff;
          color: white;
        }
        
        .step-title {
          color: #409eff;
          font-weight: 500;
        }
      }
      
      &.completed {
        .step-number {
          background-color: #67c23a;
          color: white;
        }
        
        .step-title {
          color: #67c23a;
        }
      }
    }
    
    .step-line {
      width: 100px;
      height: 2px;
      background-color: #dcdfe6;
      margin: 0 20px;
      margin-top: -20px;
      transition: all 0.3s;
      
      &.active {
        background-color: #67c23a;
      }
    }
  }
  
  .step-content {
    min-height: 400px;
  }
}
</style>
