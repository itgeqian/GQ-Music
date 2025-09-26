<script setup lang="ts">
import { ref, reactive, watch, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { getAlbumsByArtist } from "@/api/system";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import Add from "@iconify-icons/ri/add-circle-line";

interface FormProps {
  formInline: {
    title: string;
    artistId: number | null;
    artistName: string;
  };
}

const props = withDefaults(defineProps<FormProps>(), {
  formInline: () => ({
    title: "选择专辑",
    artistId: null,
    artistName: ""
  })
});

const emit = defineEmits<{
  next: [albumName: string];
  cancel: [];
}>();

const formRef = ref();
const albumOptions = ref<string[]>([]);
const albumsLoading = ref(false);
const newAlbumName = ref("");

// 表单数据
const form = reactive({
  selectedAlbum: "",
  isNewAlbum: false
});

// 监听props变化
watch(
  () => props.formInline,
  (newVal) => {
    if (newVal.artistId) {
      loadAlbums(newVal.artistId);
    }
  },
  { immediate: true, deep: true }
);

// 加载专辑列表
async function loadAlbums(artistId: number) {
  if (!artistId) {
    albumOptions.value = [];
    return;
  }
  albumsLoading.value = true;
  try {
    const res: any = await getAlbumsByArtist(artistId);
    albumOptions.value = res && res.code === 0 && Array.isArray(res.data) ? (res.data as string[]) : [];
  } catch (e) {
    albumOptions.value = [];
    ElMessage.error("加载专辑列表失败");
  } finally {
    albumsLoading.value = false;
  }
}

// 选择现有专辑
const selectExistingAlbum = (albumName: string) => {
  form.selectedAlbum = albumName;
  form.isNewAlbum = false;
  newAlbumName.value = "";
};

// 选择新建专辑
const selectNewAlbum = () => {
  form.isNewAlbum = true;
  form.selectedAlbum = "";
};

// 下一步
const handleNext = () => {
  if (form.isNewAlbum) {
    if (!newAlbumName.value.trim()) {
      ElMessage.error("请输入新专辑名称");
      return;
    }
    emit('next', newAlbumName.value.trim());
  } else {
    if (!form.selectedAlbum) {
      ElMessage.error("请选择专辑");
      return;
    }
    emit('next', form.selectedAlbum);
  }
};

// 取消
const handleCancel = () => {
  emit('cancel');
};
</script>

<template>
  <div class="album-selector-container">
    <el-form ref="formRef" :model="form" label-width="100px">
      <el-form-item label="歌手信息">
        <el-input 
          :value="`${props.formInline.artistName} (ID: ${props.formInline.artistId})`" 
          disabled 
          placeholder="请先在左侧选择歌手"
        />
      </el-form-item>
      
      <el-form-item label="选择专辑" required>
        <div class="album-selection">
          <!-- 现有专辑列表 -->
          <div v-if="albumOptions.length > 0" class="existing-albums">
            <h4>选择现有专辑：</h4>
            <div class="album-grid">
              <div 
                v-for="album in albumOptions" 
                :key="album"
                class="album-item"
                :class="{ active: form.selectedAlbum === album && !form.isNewAlbum }"
                @click="selectExistingAlbum(album)"
              >
                {{ album }}
              </div>
            </div>
          </div>
          
          <!-- 新建专辑 -->
          <div class="new-album">
            <h4>或创建新专辑：</h4>
            <div class="new-album-input">
              <el-button 
                type="primary" 
                :icon="useRenderIcon(Add)"
                @click="selectNewAlbum"
                :class="{ active: form.isNewAlbum }"
              >
                新建专辑
              </el-button>
              <el-input 
                v-if="form.isNewAlbum"
                v-model="newAlbumName"
                placeholder="请输入新专辑名称"
                style="margin-top: 10px;"
                @keyup.enter="handleNext"
              />
            </div>
          </div>
          
          <!-- 加载状态 -->
          <div v-if="albumsLoading" class="loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>加载专辑列表中...</span>
          </div>
          
          <!-- 无专辑提示 -->
          <div v-if="!albumsLoading && albumOptions.length === 0" class="no-albums">
            <el-empty description="该歌手暂无专辑，请创建新专辑" />
          </div>
        </div>
      </el-form-item>
      
      <el-form-item>
        <el-button @click="handleCancel">取消</el-button>
        <el-button 
          type="primary" 
          @click="handleNext"
          :disabled="!form.selectedAlbum && !form.isNewAlbum"
        >
          下一步
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.album-selector-container {
  .album-selection {
    .existing-albums {
      margin-bottom: 20px;
      
      h4 {
        margin-bottom: 10px;
        color: #606266;
        font-size: 14px;
      }
      
      .album-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
        gap: 10px;
        
        .album-item {
          padding: 12px 16px;
          border: 1px solid #dcdfe6;
          border-radius: 6px;
          text-align: center;
          cursor: pointer;
          transition: all 0.3s;
          background-color: #fafafa;
          
          &:hover {
            border-color: #409eff;
            background-color: #ecf5ff;
          }
          
          &.active {
            border-color: #409eff;
            background-color: #409eff;
            color: white;
          }
        }
      }
    }
    
    .new-album {
      h4 {
        margin-bottom: 10px;
        color: #606266;
        font-size: 14px;
      }
      
      .new-album-input {
        .el-button {
          &.active {
            background-color: #67c23a;
            border-color: #67c23a;
          }
        }
      }
    }
    
    .loading {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
      color: #909399;
      
      .el-icon {
        margin-right: 8px;
      }
    }
    
    .no-albums {
      padding: 20px;
    }
  }
}
</style>
