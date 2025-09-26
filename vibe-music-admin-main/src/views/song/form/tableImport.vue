<script setup lang="ts">
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { batchImportSongs } from "@/api/system";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import Upload from "@iconify-icons/ri/upload-line";
import Delete from "@iconify-icons/ep/delete";
import Plus from "@iconify-icons/ep/plus";

interface FormProps {
  formInline: {
    title: string;
    artistId: number | null;
    artistName: string;
    albumName: string;
  };
}

const props = withDefaults(defineProps<FormProps>(), {
  formInline: () => ({
    title: "批量导入",
    artistId: null,
    artistName: "",
    albumName: ""
  })
});

const emit = defineEmits<{
  success: [];
  back: [];
}>();

// 表格列数配置（建议5-8列，避免页面卡顿）
const COLUMN_COUNT = 6;
const ROW_COUNT = 10; // 每页显示10行

// 歌曲风格选项
const styleOptions = [
  "节奏布鲁斯",
  "欧美流行",
  "华语流行",
  "粤语流行",
  "国风流行",
  "韩国流行",
  "日本流行",
  "嘻哈说唱",
  "非洲节拍",
  "原声带",
  "轻音乐",
  "摇滚",
  "朋克",
  "电子",
  "国风",
  "乡村",
  "古典"
];

// 内容类型
const contentType = ref<'song' | 'audiobook'>('song');

// 表格数据
const tableData = ref<any[]>([]);
const loading = ref(false);
const importResult = ref<any>(null);
const showResult = ref(false);

// 初始化表格数据
const initTableData = () => {
  tableData.value = [];
  for (let i = 0; i < ROW_COUNT; i++) {
    tableData.value.push({
      id: i + 1,
      songName: "",
      style: "",
      audioFile: null,
      lyricFile: null,
      audioUploading: false,
      lyricUploading: false,
      audioProgress: 0,
      lyricProgress: 0,
      audioUrl: "",
      lyricUrl: ""
    });
  }
};

// 添加新行
const addRow = () => {
  const newId = Math.max(...tableData.value.map(item => item.id), 0) + 1;
  tableData.value.push({
    id: newId,
    songName: "",
    style: "",
    audioFile: null,
    lyricFile: null,
    audioUploading: false,
    lyricUploading: false,
    audioProgress: 0,
    lyricProgress: 0,
    audioUrl: "",
    lyricUrl: ""
  });
};

// 删除行
const removeRow = (index: number) => {
  if (tableData.value.length > 1) {
    tableData.value.splice(index, 1);
  } else {
    ElMessage.warning("至少需要保留一行");
  }
};

// 处理音频文件拖拽
const handleAudioDrop = (event: DragEvent, rowIndex: number) => {
  event.preventDefault();
  const files = event.dataTransfer?.files;
  if (files && files.length > 0) {
    const file = files[0];
    if (isValidAudioFile(file)) {
      uploadAudioFile(file, rowIndex);
    } else {
      ElMessage.error("请上传MP3或FLAC格式的音频文件");
    }
  }
};

// 处理歌词文件拖拽
const handleLyricDrop = (event: DragEvent, rowIndex: number) => {
  event.preventDefault();
  const files = event.dataTransfer?.files;
  if (files && files.length > 0) {
    const file = files[0];
    if (isValidLyricFile(file)) {
      uploadLyricFile(file, rowIndex);
    } else {
      ElMessage.error("请上传LRC格式的歌词文件");
    }
  }
};

// 批量拖拽音频：一次性拖多个音频文件，顺序填充到未占用行
const handleBatchAudioDrop = (event: DragEvent) => {
  event.preventDefault();
  const files = event.dataTransfer?.files;
  if (!files || files.length === 0) return;

  const droppedFiles = Array.from(files);

  const validAudioFiles: File[] = [];
  const invalidFiles: string[] = [];

  droppedFiles.forEach(file => {
    if (isValidAudioFile(file)) {
      validAudioFiles.push(file);
    } else {
      invalidFiles.push(file.name);
    }
  });

  if (validAudioFiles.length === 0) {
    ElMessage.error("未检测到有效的MP3/FLAC音频文件");
    return;
  }

  // 计算可用的空行索引（不覆盖已有文件）
  const availableRowIndexes: number[] = [];
  tableData.value.forEach((row, index) => {
    if (!row.audioFile) availableRowIndexes.push(index);
  });

  if (availableRowIndexes.length === 0) {
    ElMessage.warning("没有可用的空行，已存在的文件不会被覆盖");
    return;
  }

  // 可填充的最大数量
  const fillCount = Math.min(validAudioFiles.length, availableRowIndexes.length);

  for (let i = 0; i < fillCount; i++) {
    const file = validAudioFiles[i];
    const rowIndex = availableRowIndexes[i];
    uploadAudioFile(file, rowIndex);
  }

  // 处理超出未分配的文件（包括无效文件）
  const overflowFiles = validAudioFiles.slice(fillCount).map(f => f.name);

  if (invalidFiles.length > 0) {
    ElMessage.warning(`以下文件格式无效(已忽略)：${invalidFiles.join("、")}`);
  }

  if (overflowFiles.length > 0) {
    ElMessage.warning(`超过可用行数，以下文件未填充：${overflowFiles.join("、")}`);
  }
};

// 批量拖拽歌词：一次性拖多个LRC文件，顺序填充到未占用歌词列
const handleBatchLyricDrop = (event: DragEvent) => {
  event.preventDefault();
  const files = event.dataTransfer?.files;
  if (!files || files.length === 0) return;

  const droppedFiles = Array.from(files);

  const validLyricFiles: File[] = [];
  const invalidFiles: string[] = [];

  droppedFiles.forEach(file => {
    if (isValidLyricFile(file)) {
      validLyricFiles.push(file);
    } else {
      invalidFiles.push(file.name);
    }
  });

  if (validLyricFiles.length === 0) {
    ElMessage.error("未检测到有效的LRC歌词文件");
    return;
  }

  // 计算可用歌词空位（不覆盖已有歌词）
  const availableRowIndexes: number[] = [];
  tableData.value.forEach((row, index) => {
    if (!row.lyricFile) availableRowIndexes.push(index);
  });

  if (availableRowIndexes.length === 0) {
    ElMessage.warning("没有可用的歌词空位，已存在的歌词不会被覆盖");
    return;
  }

  const fillCount = Math.min(validLyricFiles.length, availableRowIndexes.length);

  for (let i = 0; i < fillCount; i++) {
    const file = validLyricFiles[i];
    const rowIndex = availableRowIndexes[i];
    uploadLyricFile(file, rowIndex);
  }

  const overflowFiles = validLyricFiles.slice(fillCount).map(f => f.name);

  if (invalidFiles.length > 0) {
    ElMessage.warning(`以下文件格式无效(已忽略)：${invalidFiles.join("、")}`);
  }

  if (overflowFiles.length > 0) {
    ElMessage.warning(`超过可用歌词空位，以下文件未填充：${overflowFiles.join("、")}`);
  }
};

// 验证音频文件
const isValidAudioFile = (file: File) => {
  const validTypes = ['audio/mpeg', 'audio/mp3', 'audio/flac'];
  const validExtensions = ['.mp3', '.flac'];
  return validTypes.includes(file.type) || validExtensions.some(ext => file.name.toLowerCase().endsWith(ext));
};

// 验证歌词文件
const isValidLyricFile = (file: File) => {
  return file.name.toLowerCase().endsWith('.lrc');
};

// 上传音频文件
const uploadAudioFile = async (file: File, rowIndex: number) => {
  const row = tableData.value[rowIndex];
  row.audioFile = file;
  row.audioUploading = true;
  row.audioProgress = 0;
  
  try {
    // 模拟上传进度
    const progressInterval = setInterval(() => {
      if (row.audioProgress < 90) {
        row.audioProgress += Math.random() * 20;
      }
    }, 200);
    
    // 这里暂时模拟上传成功，实际项目中可以调用单独的文件上传API
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    clearInterval(progressInterval);
    row.audioProgress = 100;
    row.audioUrl = `uploaded/audio/${file.name}`;
    row.audioUploading = false;
    
    ElMessage.success(`音频文件 "${file.name}" 准备就绪`);
  } catch (error) {
    row.audioUploading = false;
    row.audioProgress = 0;
    ElMessage.error(`音频文件 "${file.name}" 处理失败`);
  }
};

// 上传歌词文件
const uploadLyricFile = async (file: File, rowIndex: number) => {
  const row = tableData.value[rowIndex];
  row.lyricFile = file;
  row.lyricUploading = true;
  row.lyricProgress = 0;
  
  try {
    // 模拟上传进度
    const progressInterval = setInterval(() => {
      if (row.lyricProgress < 90) {
        row.lyricProgress += Math.random() * 20;
      }
    }, 200);
    
    // 这里暂时模拟上传成功，实际项目中可以调用单独的文件上传API
    await new Promise(resolve => setTimeout(resolve, 1500));
    
    clearInterval(progressInterval);
    row.lyricProgress = 100;
    row.lyricUrl = `uploaded/lyric/${file.name}`;
    row.lyricUploading = false;
    
    ElMessage.success(`歌词文件 "${file.name}" 准备就绪`);
  } catch (error) {
    row.lyricUploading = false;
    row.lyricProgress = 0;
    ElMessage.error(`歌词文件 "${file.name}" 处理失败`);
  }
};

// 处理内容类型变化
const onContentTypeChange = () => {
  // 切换类型时清空所有风格选择
  tableData.value.forEach(row => {
    row.style = '';
  });
};

// 处理风格变化（听书模式下自动同步）
const handleStyleChange = (newStyle: string, currentIndex: number) => {
  if (contentType.value === 'audiobook' && currentIndex === 0 && newStyle) {
    // 听书模式且是第一行，同步所有其他行的风格
    tableData.value.forEach((row, index) => {
      if (index !== 0) {
        row.style = newStyle;
      }
    });
  }
};

// 计算有效数据行数
const validRows = computed(() => {
  return tableData.value.filter(row => row.songName.trim() && row.style && row.audioFile);
});

// 执行批量导入
const handleImport = async () => {
  const validData = validRows.value;
  
  if (validData.length === 0) {
    ElMessage.error("请至少填写一首歌曲的名称、选择风格并上传音频文件");
    return;
  }
  
  try {
    await ElMessageBox.confirm(
      `确认导入 ${validData.length} 首歌曲到专辑 "${props.formInline.albumName}" 吗？`,
      '确认导入',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );
    
    loading.value = true;
    
    // 准备FormData
    const formData = new FormData();
    formData.append('artistId', props.formInline.artistId!.toString());
    formData.append('albumName', props.formInline.albumName);
    
    // 准备歌曲名称列表
    const songNames = validData.map(row => row.songName.trim()).join(',');
    formData.append('songNames', songNames);
    
    // 准备歌曲风格列表
    const songStyles = validData.map(row => row.style).join(',');
    formData.append('songStyles', songStyles);
    
    // 添加音频文件
    validData.forEach((row, index) => {
      if (row.audioFile) {
        formData.append('audioFiles', row.audioFile);
      }
    });
    
    // 添加歌词文件（如果有）
    validData.forEach((row, index) => {
      if (row.lyricFile) {
        formData.append('lyricFiles', row.lyricFile);
      }
    });
    
    const response = await batchImportSongs(formData);
    
    if (response.code === 0) {
      importResult.value = response.data;
      showResult.value = true;
      const result = response.data as any;
      ElMessage.success(`导入完成！成功：${result.successCount}，失败：${result.failureCount}`);
      emit('success');
    } else {
      ElMessage.error(response.message || "导入失败");
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('导入失败:', error);
      ElMessage.error("导入失败，请稍后重试");
    }
  } finally {
    loading.value = false;
  }
};

// 返回上一步
const handleBack = () => {
  emit('back');
};

// 关闭结果对话框
const closeResult = () => {
  showResult.value = false;
  importResult.value = null;
};

onMounted(() => {
  initTableData();
});
</script>

<template>
  <div class="table-import-container">
    <div class="import-header">
      <div class="header-info">
        <h3>批量导入歌曲</h3>
        <p>歌手：{{ props.formInline.artistName }} | 专辑：{{ props.formInline.albumName }}</p>
      </div>
      <div class="header-actions">
        <el-button @click="handleBack">返回</el-button>
        <el-button type="primary" @click="addRow" :icon="useRenderIcon(Plus)">
          添加行
        </el-button>
        <el-button 
          type="success" 
          :loading="loading"
          :disabled="validRows.length === 0"
          @click="handleImport"
        >
          {{ loading ? '处理中…' : `批量导入 (${validRows.length}首)` }}
        </el-button>
      </div>
    </div>
    
    <!-- 内容类型选择 -->
    <div class="content-type-selector">
      <el-radio-group v-model="contentType" @change="onContentTypeChange">
        <el-radio value="song">歌曲</el-radio>
        <el-radio value="audiobook">听书</el-radio>
      </el-radio-group>
      <div class="type-tip">
        <el-text v-if="contentType === 'song'" type="info" size="small">
          歌曲模式：每首歌曲可以有不同的风格
        </el-text>
        <el-text v-else type="info" size="small">
          听书模式：第一行选择风格后，其他行会自动同步相同风格
        </el-text>
      </div>
    </div>
    
    <!-- 批量拖拽提示与区域（一次性拖入多个音频文件，顺序填充空行） -->
    <div 
      class="bulk-drop-area"
      @dragover.prevent
      @drop="handleBatchAudioDrop"
    >
      <div class="bulk-drop-content">
        <el-icon><Upload /></el-icon>
        <div class="bulk-drop-text">
          将多个MP3/FLAC文件一次性拖拽到此，系统会按顺序填充到未占用的行中。
        </div>
        <div class="bulk-drop-subtext">
          若文件数量超过可用行数，超出部分将自动忽略并提示；已填充行不会被覆盖。
        </div>
      </div>
    </div>

    <!-- 批量拖拽歌词区域（一次性拖入多个LRC文件，顺序填充空行） -->
    <div 
      class="bulk-drop-area"
      @dragover.prevent
      @drop="handleBatchLyricDrop"
    >
      <div class="bulk-drop-content">
        <el-icon><Upload /></el-icon>
        <div class="bulk-drop-text">
          将多个LRC文件一次性拖拽到此，系统会按顺序填充到未占用的歌词列中。
        </div>
        <div class="bulk-drop-subtext">
          若文件数量超过可用歌词空位，超出部分将自动忽略并提示；已填充歌词不会被覆盖。
        </div>
      </div>
    </div>

    <div class="import-table">
      <el-table :data="tableData" border style="width: 100%">
        <el-table-column prop="id" label="序号" width="80" align="center" />
        
        <el-table-column label="歌曲名称" width="180">
          <template #default="{ row, $index }">
            <el-input 
              v-model="row.songName" 
              placeholder="请输入歌曲名称"
              size="small"
            />
          </template>
        </el-table-column>
        
        <el-table-column label="歌曲风格" width="150">
          <template #default="{ row, $index }">
            <el-select 
              v-model="row.style" 
              placeholder="请选择风格"
              size="small"
              style="width: 100%"
              @change="(value) => handleStyleChange(value, $index)"
            >
              <el-option
                v-for="style in styleOptions"
                :key="style"
                :label="style"
                :value="style"
              />
            </el-select>
          </template>
        </el-table-column>
        
        <el-table-column label="音频文件" width="280">
          <template #default="{ row, $index }">
            <div 
              class="upload-area"
              :class="{ 
                'has-file': row.audioFile, 
                'uploading': row.audioUploading 
              }"
              @dragover.prevent
              @drop="handleAudioDrop($event, $index)"
            >
              <div v-if="row.audioUploading" class="upload-progress">
                <el-progress 
                  :percentage="row.audioProgress" 
                  :show-text="false"
                  size="small"
                />
                <span class="progress-text">上传中...</span>
              </div>
              <div v-else-if="row.audioFile" class="file-info">
                <el-icon><Document /></el-icon>
                <span>{{ row.audioFile.name }}</span>
                <el-button 
                  type="danger" 
                  size="small" 
                  :icon="useRenderIcon(Delete)"
                  @click="row.audioFile = null; row.audioUrl = ''"
                />
              </div>
              <div v-else class="upload-placeholder">
                <el-icon><Upload /></el-icon>
                <span>拖拽MP3/FLAC文件到此处</span>
              </div>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="歌词文件(可选)" width="280">
          <template #default="{ row, $index }">
            <div 
              class="upload-area"
              :class="{ 
                'has-file': row.lyricFile, 
                'uploading': row.lyricUploading 
              }"
              @dragover.prevent
              @drop="handleLyricDrop($event, $index)"
            >
              <div v-if="row.lyricUploading" class="upload-progress">
                <el-progress 
                  :percentage="row.lyricProgress" 
                  :show-text="false"
                  size="small"
                />
                <span class="progress-text">上传中...</span>
              </div>
              <div v-else-if="row.lyricFile" class="file-info">
                <el-icon><Document /></el-icon>
                <span>{{ row.lyricFile.name }}</span>
                <el-button 
                  type="danger" 
                  size="small" 
                  :icon="useRenderIcon(Delete)"
                  @click="row.lyricFile = null; row.lyricUrl = ''"
                />
              </div>
              <div v-else class="upload-placeholder">
                <el-icon><Upload /></el-icon>
                <span>拖拽LRC文件到此处(可选)</span>
              </div>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ $index }">
            <el-button 
              type="danger" 
              size="small" 
              :icon="useRenderIcon(Delete)"
              @click="removeRow($index)"
              :disabled="tableData.length <= 1"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>
    
    <!-- 导入结果对话框 -->
    <el-dialog 
      v-model="showResult" 
      title="导入结果" 
      width="60%"
      :close-on-click-modal="false"
    >
      <div v-if="importResult" class="import-result">
        <el-alert 
          :title="`导入完成！总计：${importResult.totalCount}首，成功：${importResult.successCount}首，失败：${importResult.failureCount}首`"
          :type="importResult.failureCount > 0 ? 'warning' : 'success'"
          show-icon
          :closable="false"
        />
        
        <div v-if="importResult.failures && importResult.failures.length > 0" class="failures-list">
          <h4>失败详情：</h4>
          <el-table :data="importResult.failures" size="small" max-height="300">
            <el-table-column prop="rowNumber" label="行号" width="80" />
            <el-table-column prop="songName" label="歌名" />
            <el-table-column prop="reason" label="失败原因" />
          </el-table>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="closeResult">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.table-import-container {
  .bulk-drop-area {
    margin: 12px 0 16px 0;
    border: 2px dashed #dcdfe6;
    border-radius: 8px;
    padding: 14px;
    background-color: var(--el-fill-color-lighter);
    transition: all 0.2s ease;
    cursor: copy;

    &:hover {
      border-color: #409eff;
      background-color: #ecf5ff;
    }

    .bulk-drop-content {
      display: flex;
      align-items: center;
      gap: 10px;
      .el-icon {
        font-size: 20px;
        color: #409eff;
      }
      .bulk-drop-text {
        font-size: 13px;
        color: #606266;
      }
    }

    .bulk-drop-subtext {
      margin-top: 6px;
      font-size: 12px;
      color: #909399;
    }
  }
  .import-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding: 16px;
    background-color: #f5f7fa;
    border-radius: 6px;
    
    .header-info {
      h3 {
        margin: 0 0 8px 0;
        color: #303133;
      }
      
      p {
        margin: 0;
        color: #606266;
        font-size: 14px;
      }
    }
    
    .header-actions {
      display: flex;
      gap: 10px;
    }
  }
  
  .content-type-selector {
    margin: 20px 0;
    padding: 15px;
    background-color: var(--el-fill-color-lighter);
    border-radius: 8px;
    
    .type-tip {
      margin-top: 8px;
    }
  }
  
  .import-table {
    .upload-area {
      min-height: 60px;
      border: 2px dashed #dcdfe6;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.3s;
      padding: 8px;
      
      &:hover {
        border-color: #409eff;
        background-color: #ecf5ff;
      }
      
      &.has-file {
        border-color: #67c23a;
        background-color: #f0f9ff;
      }
      
      &.uploading {
        border-color: #e6a23c;
        background-color: #fdf6ec;
      }
      
      .upload-progress {
        width: 100%;
        text-align: center;
        
        .progress-text {
          display: block;
          margin-top: 4px;
          font-size: 12px;
          color: #e6a23c;
        }
      }
      
      .file-info {
        display: flex;
        align-items: center;
        gap: 8px;
        width: 100%;
        
        .el-icon {
          color: #67c23a;
        }
        
        span {
          flex: 1;
          font-size: 12px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
      
      .upload-placeholder {
        text-align: center;
        color: #909399;
        
        .el-icon {
          font-size: 24px;
          margin-bottom: 4px;
        }
        
        span {
          display: block;
          font-size: 12px;
        }
      }
    }
  }
  
  .import-result {
    .failures-list {
      margin-top: 20px;
      
      h4 {
        margin-bottom: 10px;
        color: #f56c6c;
      }
    }
  }
}
</style>
