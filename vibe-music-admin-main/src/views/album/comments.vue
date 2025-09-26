<script setup lang="ts">
import { ref, onMounted, h } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "@/utils/message";
import { PureTableBar } from "@/components/RePureTableBar";
import { useRenderIcon } from "@/components/ReIcon/src/hooks";
import Refresh from "@iconify-icons/ep/refresh";
import ArrowLeft from "@iconify-icons/ep/arrow-left";
import type { PaginationProps, TableColumns } from "@pureadmin/table";
import { getAlbumComments, deleteComment } from "@/api/system";

defineOptions({ name: "AlbumComments" });

const route = useRoute();
const router = useRouter();
const albumId = ref<number>(Number(route.params.id));
const loading = ref(false);
const dataList = ref<any[]>([]);
const pagination = ref<PaginationProps>({ total: 0, pageSize: 10, currentPage: 1, background: true });

const columns: TableColumns[] = [
  { label: "评论ID", prop: "commentId", width: 90 },
  { label: "用户", prop: "username", minWidth: 120 },
  { label: "内容", prop: "content", slot: "content", minWidth: 320 },
  { label: "时间", prop: "createTime", width: 140 },
  { label: "点赞", prop: "likeCount", width: 90 },
  { label: "操作", slot: "operation", width: 120, fixed: "right" }
];

const showImageDialog = ref(false);
const previewUrl = ref("");
function openImage(row: any) {
  if (!row?.imgPath) return;
  previewUrl.value = row.imgPath;
  showImageDialog.value = true;
}

function buildTree(list: any[]) {
  if (!Array.isArray(list)) return [];
  const byId: Record<number, any> = {};
  const roots: any[] = [];
  list.forEach(it => { it.pCommentId = it.pCommentId ?? it.pcommentId ?? 0; byId[it.commentId] = { ...it, children: Array.isArray(it.children) ? it.children : [] }; });
  Object.values(byId).forEach((it:any)=>{ const pid = Number((it as any).pCommentId||0); if (pid>0 && byId[pid]) byId[pid].children.push(it); else if (pid===0) roots.push(it as any); });
  return roots;
}

function flatten(list:any[], depth=0, out:any[]=[]){
  list.forEach(it=>{ out.push({ ...it, _depth: depth, _displayContent: (it.replyNickName?`回复 @${it.replyNickName} `:"") + (it.content||"") }); if (Array.isArray(it.children) && it.children.length) flatten(it.children, depth+1, out); });
  return out;
}

async function fetchData() {
  loading.value = true;
  try {
    const res: any = await getAlbumComments(albumId.value);
    if (res?.code === 0) {
      const raw = (res.data as any[]) || [];
      const hasChildren = raw.some((it:any)=>Array.isArray(it.children) && it.children.length);
      const tree = hasChildren ? raw : buildTree(raw);
      dataList.value = flatten(tree);
      pagination.value.total = dataList.value.length;
    }
  } finally {
    loading.value = false;
  }
}

function onDelete(row: any) {
  deleteComment(row.commentId).then((res: any) => {
    if (res.code === 0) {
      message("删除成功", { type: "success" });
      fetchData();
    } else {
      message("删除失败," + res.message, { type: "error" });
    }
  });
}

onMounted(() => {
  if (!Number.isFinite(albumId.value)) {
    message("专辑ID无效", { type: "warning" });
    router.back();
    return;
  }
  fetchData();
});
</script>

<template>
  <div class="w-full">
    <div class="mb-2">
      <el-button link type="primary" :icon="useRenderIcon(ArrowLeft)" @click="$router.back()">返回</el-button>
    </div>
    <PureTableBar title="专辑评论管理" :columns="columns" @refresh="fetchData">
      <template #buttons>
        <el-button :icon="useRenderIcon(Refresh)" @click="fetchData">刷新</el-button>
      </template>
      <template #default="{ size, dynamicColumns }">
        <pure-table :data="dataList" :columns="dynamicColumns" :size="size" :loading="loading" :pagination="pagination">
          <template #content="{ row }">
            <span :style="{ marginLeft: (row._depth||0)*16 + 'px' }">{{ row._displayContent || row.content }}</span>
            <el-tag v-if="row.imgPath" type="warning" size="small" effect="plain" class="ml-2 cursor-pointer" @click="openImage(row)">图片</el-tag>
          </template>
          <template #operation="{ row }">
            <el-popconfirm title="确认删除该评论？" @confirm="onDelete(row)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </pure-table>
        <el-dialog v-model="showImageDialog" title="图片预览" width="600px" append-to-body>
          <div class="w-full flex justify-center items-center">
            <img v-if="showImageDialog" :src="previewUrl" style="max-width:100%;max-height:70vh;object-fit:contain;" />
          </div>
        </el-dialog>
      </template>
    </PureTableBar>
  </div>
</template>

<style scoped></style>


