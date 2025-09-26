<script setup lang="ts">
import { ref, reactive, onMounted, h, computed } from "vue";
import { deviceDetection } from "@pureadmin/utils";
import { PureTableBar } from "@/components/RePureTableBar";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import { addDialog } from "@/components/ReDialog";
import AddFill from "@iconify-icons/ri/add-circle-line";
import Refresh from "@iconify-icons/ep/refresh";
import EditPen from "@iconify-icons/ep/edit-pen";
import Delete from "@iconify-icons/ep/delete";
import Upload from "@iconify-icons/ri/upload-line";
import More from "@iconify-icons/ep/more-filled";
import ChatLineSquare from "@iconify-icons/ep/chat-line-square";
import { message } from "@/utils/message";
import type { PaginationProps, TableColumns } from "@pureadmin/table";
import tree from "@/views/song/tree.vue";
import { getAllArtists, getAlbumList, addAlbum, updateAlbum, deleteAlbum, deleteAlbums, updateAlbumCover } from "@/api/system";
import editForm from "./form/index.vue";
import ReCropperPreview from "@/components/ReCropperPreview";
import songCover from "@/assets/song.jpg";

defineOptions({ name: "AlbumManagement" });

const formRef = ref();
const tableRef = ref();
const treeRef = ref();

const form = reactive({
  pageNum: 1,
  pageSize: 10,
  artistId: null as number | null,
  artistName: "",
  title: ""
});

const loading = ref(false);
const dataList = ref<any[]>([]);
const artists = ref<any[]>([]);
const treeData = ref<any[]>([]);
const treeLoading = ref(false);
const selectedNum = ref(0);
const pagination = reactive<PaginationProps>({ total: 0, pageSize: 10, currentPage: 1, background: true });

const columns: TableColumns[] = [
  { type: "selection", align: "left", width: 50, reserveSelection: true },
  { label: "专辑编号", prop: "albumId", width: 90 },
  { label: "封面", prop: "coverUrl", width: 100, slot: "cover", align: "center", headerAlign: "center" },
  { label: "专辑名", prop: "title", minWidth: 200, className: "album-title-col", labelClassName: "album-title-header" },
  { label: "歌手", prop: "artistName", width: 160 },
  { label: "发行时间", prop: "releaseDate", width: 120 },
  { label: "类型", prop: "category", width: 120 },
  { label: "操作", fixed: "right", width: 220, slot: "operation", align: "center", headerAlign: "center" }
];

const fetchArtists = async () => {
  const res = await getAllArtists();
  const list = (res?.data as any[]) || [];
  artists.value = list;
  treeData.value = list.map((a: any) => ({ label: a.artistName, value: a.artistId, children: [] }));
};

const onSearch = async () => {
  loading.value = true;
  try {
    const { pageNum, pageSize, artistId, title, artistName } = form as any;
    // 将筛选条件完整传给后端，保持与歌曲/歌手管理一致的分页逻辑
    const res: any = await getAlbumList({ pageNum, pageSize, artistId, title, artistName });
    if (res?.code === 0 && res?.data) {
      const items = (res.data.items as any[]) || [];
      // 为每条记录填充歌手名（从左侧 artists 列表映射），并保持 artistId
      const idToName = new Map(artists.value.map((a: any) => [a.artistId, a.artistName]));
      const list = items.map((it: any) => ({
        ...it,
        artistName: it.artistName || idToName.get(it.artistId) || '',
      }));
      dataList.value = list;
      // 关键修复：使用后端返回的总条数，保证分页可翻页
      pagination.total = Number(res.data.total) || list.length;
    }
  } finally {
    loading.value = false;
  }
};

const resetForm = () => {
  form.pageNum = 1;
  form.pageSize = 10;
  form.artistId = null;
  form.artistName = "";
  form.title = "";
  onSearch();
};

const handleSizeChange = (val: number) => {
  form.pageSize = val;
  onSearch();
};

const handleCurrentChange = (val: number) => {
  form.pageNum = val;
  onSearch();
};

const handleSelectionChange = (rows: any[]) => {
  selectedNum.value = rows.length;
};

const buttonClass = computed(() => ["!h-[20px]","reset-margin","!text-gray-500","dark:!text-white","dark:hover:!text-primary"]);

const openDialog = (dialogTitle: any = "新增", row?: any) => {
  // 处理未加括号触发点击事件导致传入 PointerEvent 的情况
  if (typeof dialogTitle !== "string") {
    row = undefined;
    dialogTitle = "新增";
  }
  // 修改时允许未选歌手；新增时要求先选歌手
  if (dialogTitle === "新增" && !form.artistId) {
    message("请先在左侧选择歌手", { type: "warning" });
    return;
  }
  addDialog({
    title: `${dialogTitle}专辑`,
    props: {
      formInline: {
        title: dialogTitle,
        artistId: (dialogTitle === "新增" ? (form.artistId ?? 0) : (row?.artistId ?? form.artistId ?? 0)),
        artistName: row?.artistName ?? "",
        albumId: row?.albumId ?? 0,
        name: row?.title ?? "",
        releaseDate: row?.releaseDate ?? "",
        category: row?.category ?? "",
        introduction: row?.introduction ?? "",
        details: row?.details ?? ""
      }
    },
    width: "46%",
    draggable: true,
    fullscreen: deviceDetection(),
    fullscreenIcon: true,
    closeOnClickModal: false,
    contentRenderer: () => h(editForm, { ref: formRef, formInline: null }),
    beforeSure: (done, { options }) => {
      const FormRef = (formRef as any).value.getRef();
      const curData = options.props.formInline as any;
      function afterOk() {
        message(`您${dialogTitle}了专辑：${curData.name}`, { type: "success" });
        done();
        onSearch();
      }
      FormRef.validate((valid: boolean) => {
        if (valid) {
          const payload = {
            artistId: curData.artistId,
            title: curData.name,
            releaseDate: curData.releaseDate,
            category: curData.category,
            introduction: curData.introduction,
            details: curData.details,
            albumId: curData.albumId
          };
          if (dialogTitle === "新增") {
            addAlbum(payload).then((res: any) => { if (res.code === 0) afterOk(); else message("新增失败," + res.message, { type: "error" }); });
          } else {
            updateAlbum({ ...payload, albumId: row?.albumId ?? payload.albumId }).then((res: any) => { if (res.code === 0) afterOk(); else message("修改失败," + res.message, { type: "error" }); });
          }
        }
      });
    }
  });
};

const handleUpdate = (row: any) => openDialog("修改", row);

const onbatchDel = (ids: number[]) => {
  deleteAlbums(ids).then((res: any) => {
    if (res.code === 0) {
      message("删除成功", { type: "success" });
      onSearch();
    }
  });
};

const handleDelete = (row: any) => {
  deleteAlbum(row.albumId).then((res: any) => {
    if (res.code === 0) {
      message("删除成功", { type: "success" });
      onSearch();
    }
  });
};

// 上传封面（裁剪）
const cropRef = ref();
const handleUpload = (row: any) => {
  addDialog({
    title: "裁剪、上传封面",
    width: "40%",
    closeOnClickModal: false,
    fullscreen: deviceDetection(),
    contentRenderer: () =>
      h(ReCropperPreview, {
        ref: cropRef,
        imgSrc: row.coverUrl || songCover,
        onCropper: (info: any) => (coverInfo.value = info)
      }),
    beforeSure: async done => {
      if (!coverInfo.value?.blob) {
        message("图片裁剪失败，请重试", { type: "error" });
        return;
      }
      const formData = new FormData();
      formData.append("cover", coverInfo.value.blob, "cover.jpg");
      try {
        const res = await updateAlbumCover(row.albumId, formData as any);
        if (res.code === 0) {
          message("上传封面成功", { type: "success" });
          done();
          onSearch();
        } else {
          message("上传封面失败", { type: "error" });
        }
      } catch (e) {
        message("上传失败，请重试", { type: "error" });
      }
    },
    closeCallBack: () => (cropRef as any).value?.hidePopover?.()
  });
};
const coverInfo = ref<any>();

onMounted(async () => {
  treeLoading.value = true;
  await fetchArtists();
  await onSearch();
  treeLoading.value = false;
});
</script>

<template>
  <div class="flex justify-between" :class="deviceDetection() && 'flex-wrap'">
    <tree
      ref="treeRef"
      :class="['mr-2', deviceDetection() ? 'w-full' : 'min-w-[180px]']"
      :treeData="treeData"
      :treeLoading="treeLoading"
          @tree-select="({ artistId, selected }) => { form.artistId = selected ? artistId : null; form.pageNum = 1; pagination.currentPage = 1; onSearch(); }"
    />
    <div :class="deviceDetection() ? ['w-full','mt-2'] : 'w-[calc(100%-180px)]'">
      <el-form ref="formRef" :inline="true" :model="form" class="search-form bg-bg_color w-[99/100] pl-8 pt-[12px] overflow-auto">
        <el-form-item label="歌手：" prop="artistName">
          <el-input v-model="form.artistName" placeholder="请输入歌手名字" clearable class="!w-[180px]" />
        </el-form-item>
        <el-form-item label="专辑名称：" prop="title">
          <el-input v-model="form.title" placeholder="请输入专辑名称" clearable class="!w-[180px]" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="useRenderIcon('ri:search-line')" :loading="loading" @click="onSearch">搜索</el-button>
          <el-button :icon="useRenderIcon(Refresh)" @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>

      <PureTableBar title="专辑管理（在对专辑进行操作前，请在左侧选择歌手）" :columns="columns" @refresh="onSearch">
      <template #buttons>
        <el-button :icon="useRenderIcon(AddFill)" type="primary" @click="openDialog">新增专辑</el-button>
        <el-button :icon="useRenderIcon(Delete)" type="danger" :disabled="selectedNum === 0" @click="onbatchDel(tableRef.getTableRef().getSelectionRows().map((r:any)=>r.albumId))">批量删除</el-button>
      </template>
      <template #default="{ size, dynamicColumns }">
        <pure-table
          ref="tableRef"
          :data="dataList"
          :columns="dynamicColumns"
          :size="size"
          :pagination="pagination"
          :loading="loading"
          row-key="albumId"
          @selection-change="handleSelectionChange"
          @page-size-change="handleSizeChange"
          @page-current-change="handleCurrentChange"
        >
          <template #cover="{ row }">
            <el-image fit="cover" :src="row.coverUrl || songCover" :preview-src-list="[row.coverUrl || songCover]" class="w-[72px] h-[72px] rounded-lg align-middle" />
          </template>
          <template #operation="{ row }">
            <el-button link type="primary" :icon="useRenderIcon(EditPen)" @click="handleUpdate(row)">修改</el-button>
            <el-popconfirm :title="`该操作将删除专辑：${row.title}，且会清空其下歌曲的专辑字段，是否继续？`" @confirm="handleDelete(row)">
              <template #reference>
                <el-button link type="danger" :icon="useRenderIcon(Delete)">删除</el-button>
              </template>
            </el-popconfirm>
              <el-dropdown>
              <el-button class="ml-3 mt-[2px]" link type="primary" :size="size" :icon="useRenderIcon(More)" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item>
                    <el-button :class="buttonClass" link type="primary" :icon="useRenderIcon(Upload)" @click="handleUpload(row)">上传封面</el-button>
                  </el-dropdown-item>
                    <el-dropdown-item>
                      <el-button :class="buttonClass" link type="primary" :icon="useRenderIcon(ChatLineSquare)" @click="$router.push({ name: 'AlbumComments', params: { id: row.albumId } })">评论管理</el-button>
                    </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </pure-table>
      </template>
      </PureTableBar>
    </div>
  </div>
  
</template>

<style scoped>
/* 调整“专辑名”列与“封面”列的视觉对齐：给专辑名增加一点左内边距 */
:deep(.album-title-col .cell) {
  padding-left: 62px;
  text-align: center;
}

/* 表头“专辑名”列微调 */
:deep(.album-title-header) {
  padding-left: 2px;
}
</style>


