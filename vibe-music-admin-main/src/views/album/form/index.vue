<script setup lang="ts">
import { ref, watch } from "vue";
import ReCol from "@/components/ReCol";
import { http } from "@/utils/http";

const props = withDefaults(defineProps<{
  formInline: {
    title: string;
    artistId: number;
    artistName: string;
    albumId: number;
    name: string;
    releaseDate: string | null;
    category: string;
    introduction: string;
    details?: string;
  };
}>(), {
  formInline: () => ({
    title: "新增",
    artistId: 0,
    artistName: "",
    albumId: 0,
    name: "",
    releaseDate: null,
    category: "",
    introduction: "",
    details: ""
  })
});

const ruleFormRef = ref();
const newFormInline = ref(props.formInline);

// 固定唱片类型选项
const albumCategories = [
  "录音室专辑",
  "现场专辑",
  "精选集",
  "原声带",
  "迷你专辑（EP）",
  "合辑",
  "混音专辑",
  "纪念专辑",
  "试音碟",
  "单曲专辑",
  "混录带（Mixtape）",
  "影视原声专辑",
  "翻唱专辑",
  "主题专辑",
  "特别版专辑"
];

const formRules = {
  name: [{ required: true, message: "专辑名称为必填项", trigger: "blur" }],
  releaseDate: [{ required: true, message: "发行日期为必填项", trigger: "change" }]
};

function getRef() {
  return ruleFormRef.value;
}

defineExpose({ getRef });

// 当输入专辑名称且发行日期为空时，尝试从已存在歌曲中回显最早发行日期
watch(
  () => newFormInline.value.name,
  async (title) => {
    const artistId = newFormInline.value.artistId
    if (!artistId || !title) return
    if (newFormInline.value.releaseDate) return
    try {
      const res: any = await http.get('/admin/guessAlbumReleaseDate', { params: { artistId, title } })
      if (res && res.code === 0 && res.data) newFormInline.value.releaseDate = res.data
    } catch {}
  }
)
</script>

<template>
  <el-form ref="ruleFormRef" :model="newFormInline" :rules="formRules" label-width="82px">
    <el-row :gutter="30">
      <re-col v-if="newFormInline.title === '修改'" :value="12" :xs="24" :sm="24">
        <el-form-item label="专辑编号" prop="albumId">
          <el-input v-model="newFormInline.albumId" clearable disabled placeholder="newFormInline.albumId" />
        </el-form-item>
      </re-col>

      <re-col :value="12" :xs="24" :sm="24">
        <el-form-item label="歌手编号" prop="artistId">
          <el-input v-model="newFormInline.artistId" clearable disabled placeholder="newFormInline.artistId" />
        </el-form-item>
      </re-col>

      <re-col v-if="newFormInline.title === '修改'" :value="12" :xs="24" :sm="24">
        <el-form-item label="歌手" prop="artistName">
          <el-input v-model="newFormInline.artistName" clearable disabled placeholder="newFormInline.artistName" />
        </el-form-item>
      </re-col>

      <re-col :value="12" :xs="24" :sm="24">
        <el-form-item label="专辑名称" prop="name" required>
          <el-input v-model="newFormInline.name" clearable placeholder="请输入专辑名称" />
        </el-form-item>
      </re-col>

      <re-col :value="12" :xs="24" :sm="24">
        <el-form-item label="发行日期" prop="releaseDate" required>
          <el-date-picker v-model="newFormInline.releaseDate" type="date" placeholder="请选择发行日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" />
        </el-form-item>
      </re-col>

      <re-col :value="12" :xs="24" :sm="24">
        <el-form-item label="类型" prop="category">
          <el-select v-model="newFormInline.category" clearable filterable placeholder="请选择专辑类型">
            <el-option v-for="c in albumCategories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
      </re-col>

      <re-col>
        <el-form-item label="简介" prop="introduction">
          <el-input v-model="newFormInline.introduction" placeholder="请输入简介" type="textarea" line-number :autosize="{ minRows: 4, maxRows: 10 }" />
        </el-form-item>
      </re-col>

      <re-col>
        <el-form-item label="详情" prop="details">
          <el-input v-model="newFormInline.details" placeholder="请输入详情（专辑内歌曲的详细介绍）" type="textarea" line-number :autosize="{ minRows: 6, maxRows: 16 }" />
        </el-form-item>
      </re-col>
    </el-row>
  </el-form>
</template>

<style scoped></style>

