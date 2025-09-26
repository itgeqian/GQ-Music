<script setup lang="ts">
import { ref, defineProps, defineEmits, watch } from "vue";
import { updateSongAudio, updateSongLyric } from "@/api/system";
import { message } from "@/utils/message";
import UploadIcon from "@iconify-icons/ri/upload-2-line";

const props = defineProps({ songId: Number, visible: Boolean });
const emit = defineEmits(["update:visible", "success"]);

const fileList = ref([]);
const lyricList = ref([]);
const isVisible = ref(props.visible);
const audioUrl = ref(""); // 存储音频预览URL

watch(
  () => props.visible,
  newVal => {
    isVisible.value = newVal;
  }
);

const handleChange = file => {
  const name = file?.raw?.name?.toLowerCase?.() || "";
  if (!(name.endsWith('.mp3') || name.endsWith('.flac'))) {
    message("仅支持 .mp3 或 .flac 音频文件", { type: "warning" });
    fileList.value = [];
    return;
  }
  fileList.value = [file.raw]; // 只允许上传一个文件，替换掉之前的

  // 创建音频预览 URL
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value);
  }
  audioUrl.value = URL.createObjectURL(file.raw);
};

const submitForm = async () => {
  if (!props.songId) {
    message("请先选择一条歌曲记录", { type: "warning" });
    return;
  }
  if (!fileList.value.length && !lyricList.value.length) {
    message("请至少选择一个文件（音频或歌词）", { type: "warning" });
    return;
  }

  try {
    // 先传音频（如有）
    if (fileList.value.length) {
      const formData = new FormData();
      formData.append("audio", fileList.value[0]);
      const res = await updateSongAudio(props.songId, formData);
      if (res.code !== 0) throw new Error("音频上传失败");
    }
    // 再传歌词（如有）
    if (lyricList.value.length) {
      const lrc = new FormData();
      lrc.append("lyric", lyricList.value[0]);
      const lr = await updateSongLyric(props.songId, lrc);
      if (lr.code !== 0) throw new Error("歌词上传失败");
    }

    message("提交成功", { type: "success" });
    emit("update:visible", false);
    emit("success");
  } catch (error) {
    console.error("提交失败:", error);
    message(String(error?.message || "提交失败，请重试"), { type: "error" });
  }
};

// 处理歌词文件选择（避免在模板中访问 lyricList.value）
const handleLyricChange = (f: any) => {
  const raw = f?.raw as File | undefined;
  if (!raw || !raw.name?.endsWith(".lrc")) {
    message("仅支持 .lrc 文件", { type: "warning" });
    lyricList.value = [];
    return;
  }
  lyricList.value = [raw];
};
</script>

<template>
  <el-dialog
    v-model="isVisible"
    title="上传音频/歌词"
    @close="emit('update:visible', false)"
  >
    <el-upload
      :file-list="fileList"
      :auto-upload="false"
      :limit="1"
      action="#"
      drag
      accept=".mp3,.flac,audio/mpeg,audio/flac"
      @change="handleChange"
    >
      <div class="el-upload__text">
        <IconifyIconOffline :icon="UploadIcon" width="26" class="m-auto mb-2" />
        点击或拖拽上传（支持 .mp3 / .flac）
      </div>
    </el-upload>
    <audio v-if="audioUrl" :src="audioUrl" controls class="mt-3" />

    <div class="mt-6">
      <div class="mb-2 text-sm text-gray-500">可选：上传歌词（.lrc）</div>
      <el-upload
        :file-list="lyricList"
        :auto-upload="false"
        :limit="1"
        action="#"
        accept=".lrc,text/plain"
        @change="handleLyricChange"
      >
        <div class="el-upload__text">
          <IconifyIconOffline :icon="UploadIcon" width="22" class="m-auto mb-1" />
          点击或拖拽上传 .lrc 文件（可选）
        </div>
      </el-upload>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="submitForm">提交</el-button>
    </template>
  </el-dialog>
</template>

<style scoped></style>
