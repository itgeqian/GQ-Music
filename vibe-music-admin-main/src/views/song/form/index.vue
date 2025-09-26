<script setup lang="ts">
import { ref, watch, onMounted } from "vue";
import ReCol from "@/components/ReCol";
import { formRules } from "../utils/rule";
import { FormProps } from "../utils/types";
import { getAlbumsByArtist } from "@/api/system";
import { http } from "@/utils/http";

const props = withDefaults(defineProps<FormProps>(), {
  formInline: () => ({
    title: "新增",
    artistId: null,
    artistName: "",
    songId: null,
    songName: "",
    album: "",
    style: [],
    releaseTime: null
  })
});

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
const ruleFormRef = ref();
const newFormInline = ref(props.formInline);

// 专辑回显选项
const albumOptions = ref<string[]>([]);
const albumsLoading = ref(false);

async function loadAlbums(artistId: number | null) {
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
  } finally {
    albumsLoading.value = false;
  }
}

// 初始化与联动：artistId 变化时刷新专辑列表
watch(
  () => newFormInline.value.artistId,
  (val) => loadAlbums(val as any),
  { immediate: true }
);

// 回显函数：根据已选专辑拉取发行日期
async function echoReleaseFromAlbum() {
  const artistId = newFormInline.value.artistId as any
  const title = newFormInline.value.album as any
  console.log('[song-form] echoReleaseFromAlbum start', { artistId, title, currentRelease: newFormInline.value.releaseTime })
  if (!artistId || !title) { console.log('[song-form] skip: missing artistId or title'); return }
  if (newFormInline.value.releaseTime) { console.log('[song-form] skip: already has releaseTime'); return }
  try {
    const res: any = await http.get('/admin/getAlbumReleaseDate', { params: { artistId, title } })
    console.log('[song-form] getAlbumReleaseDate resp', res)
    if (res && res.code === 0 && res.data) {
      newFormInline.value.releaseTime = res.data
      // 触发一次校验，清除必填红框
      try { (ruleFormRef.value as any)?.validateField?.('releaseTime') } catch {}
    } else {
      console.log('[song-form] no release date found for album', { artistId, title })
    }
  } catch (e) {
    console.log('[song-form] getAlbumReleaseDate error', e)
  }
}

// 选择专辑后自动回显发行日期（若为空）
watch(() => newFormInline.value.album, (v) => { console.log('[song-form] album changed ->', v); echoReleaseFromAlbum() })
// 当歌手切换且已选择同名专辑时，也尝试回显
watch(() => newFormInline.value.artistId, (v) => { console.log('[song-form] artistId changed ->', v); echoReleaseFromAlbum() })

function getRef() {
  return ruleFormRef.value;
}

defineExpose({ getRef });
</script>

<template>
  <el-form
    ref="ruleFormRef"
    :model="newFormInline"
    :rules="formRules"
    label-width="82px"
  >
    <el-row :gutter="30">
      <re-col :value="12" :xs="24" :sm="24">
        <el-form-item label="歌手编号" prop="artistId">
          <el-input
            v-model="newFormInline.artistId"
            clearable
            disabled
            placeholder="newFormInline.artistId"
          />
        </el-form-item>
      </re-col>

      <re-col
        v-if="newFormInline.title === '修改'"
        :value="12"
        :xs="24"
        :sm="24"
      >
        <el-form-item label="歌手" prop="artistName">
          <el-input
            v-model="newFormInline.artistName"
            clearable
            disabled
            placeholder="newFormInline.artistName"
          />
        </el-form-item>
      </re-col>

      <re-col
        v-if="newFormInline.title === '修改'"
        :value="12"
        :xs="24"
        :sm="24"
      >
        <el-form-item label="歌曲编号" prop="songId">
          <el-input
            v-model="newFormInline.songId"
            clearable
            disabled
            placeholder="newFormInline.songId"
          />
        </el-form-item>
      </re-col>

      <re-col :value="12" :xs="24" :sm="24">
        <el-form-item label="歌名" prop="songName" required>
          <el-input
            v-model="newFormInline.songName"
            clearable
            placeholder="请输入歌名"
          />
        </el-form-item>
      </re-col>

      <re-col :value="12" :xs="24" :sm="24">
        <el-form-item label="专辑" prop="album" required>
          <el-select v-model="newFormInline.album" filterable allow-create default-first-option placeholder="请选择或手动输入专辑" class="w-full" :disabled="false">
            <el-option v-for="(item, idx) in albumOptions" :key="idx" :label="item" :value="item" />
          </el-select>
        </el-form-item>
      </re-col>
      <!-- image.png -->
      <re-col :value="12" :xs="24" :sm="24">
        <el-form-item label="发行日期" prop="releaseTime" required>
          <el-date-picker
            v-model="newFormInline.releaseTime"
            type="date"
            placeholder="请选择发行日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
      </re-col>

      <re-col :value="12" :xs="24" :sm="24">
        <el-form-item label="曲风">
          <el-select
            v-model="newFormInline.style"
            placeholder="请选择曲风"
            class="w-full"
            clearable
            multiple
          >
            <el-option
              v-for="(item, index) in styleOptions"
              :key="index"
              :label="item"
              :value="item"
            >
              {{ item }}
            </el-option>
          </el-select>
        </el-form-item>
      </re-col>
    </el-row>
  </el-form>
</template>
