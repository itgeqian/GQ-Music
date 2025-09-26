<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { message } from "@/utils/message";
import { getAllArtists, getSongList, addSongsToPlaylist, getSongsOfPlaylist, removeSongsFromPlaylist } from "@/api/system";
// 拖拽已移除
import songCover from "@/assets/song.jpg";

defineOptions({ name: "PlaylistBindSongs" });

const route = useRoute();
const router = useRouter();
const dialogVisible = ref(true);
const submitting = ref(false);

const playlistId = ref<number>(Number(route.params.id));

const artists = ref<Array<any>>([]);
const tableLoading = ref(false);
const tableData = ref<Array<any>>([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const selectedIds = ref<number[]>([]);

const form = reactive({
  artistId: null as number | null,
  songName: ""
});

// 已绑定页签
const activeTab = ref<string>("add");
const boundLoading = ref(false);
const boundList = ref<any[]>([]);
const boundSelected = ref<any[]>([]); // 选中的已绑定行
const boundPage = ref(1);
const boundSize = ref(10);
const boundTotal = ref(0);
const boundTableRef = ref();

const fetchBound = async () => {
  boundLoading.value = true;
  try {
    const res: any = await getSongsOfPlaylist({
      playlistId: playlistId.value,
      pageNum: boundPage.value,
      pageSize: boundSize.value
    });
    if (res?.code === 0 && res?.data) {
      boundTotal.value = res.data.total || 0;
      boundList.value = (res.data.items || []).map((s: any) => ({
        id: s.id,
        songId: s.songId,
        songName: s.songName,
        artistName: s.artistName,
        album: s.album,
        coverUrl: s.coverUrl || songCover,
        sortNo: s.sortNo || 0
      }));
    } else {
      boundTotal.value = 0;
      boundList.value = [];
    }
  } finally {
    boundLoading.value = false;
  }
};

const handleRemoveBound = async () => {
  if (!boundSelected.value.length) {
    message("请先选择要移除的歌曲", { type: "warning" });
    return;
  }
  const res: any = await removeSongsFromPlaylist({ playlistId: playlistId.value, songIds: boundSelected.value.map((r:any)=> r.songId) });
  if (res?.code === 0) {
    message("已移除", { type: "success" });
    fetchBound();
  }
};

// 拖拽已移除，无需初始化

// 排序已移除

const fetchArtists = async () => {
  const res: any = await getAllArtists();
  if (res?.code === 0) artists.value = (res.data as any[]) || [];
};

const fetchSongs = async () => {
  tableLoading.value = true;
  try {
    const res: any = await getSongList({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      artistId: form.artistId,
      songName: form.songName,
      album: ""
    });
    if (res?.code === 0 && res?.data) {
      total.value = res.data.total || 0;
      tableData.value = (res.data.items || []).map((s: any) => ({
        songId: s.songId,
        songName: s.songName,
        artistName: s.artistName,
        album: s.album,
        coverUrl: s.coverUrl || songCover
      }));
    } else {
      total.value = 0;
      tableData.value = [];
    }
  } finally {
    tableLoading.value = false;
  }
};

const handleConfirm = async () => {
  if (!playlistId.value) return;
  if (!selectedIds.value.length) {
    message("请先勾选要添加的歌曲", { type: "warning" });
    return;
  }
  submitting.value = true;
  try {
    const res: any = await addSongsToPlaylist({
      playlistId: playlistId.value,
      songIds: selectedIds.value
    });
    if (res?.code === 0) {
      message(res?.message || "添加成功", { type: "success" });
      // 保持在弹窗，自动切换到“已绑定”并刷新列表
      activeTab.value = "bound";
      await fetchBound();
      // 清空“选择添加”勾选
      selectedIds.value = [];
      // 重新加载可选歌曲列表，避免状态不同步
      await fetchSongs();
    } else {
      message(res?.message || "添加失败", { type: "error" });
    }
  } finally {
    submitting.value = false;
  }
};

const handleClose = () => {
  // 兜底：先路由到列表，再强制整页刷新，避免白屏
  router.replace({ name: "playlistManagement" }).finally(() => {
    setTimeout(() => window.location.reload(), 0);
  });
};

onMounted(async () => {
  await fetchArtists();
  await fetchSongs();
  await fetchBound();
});

watch([currentPage, pageSize], fetchSongs);
watch([boundPage, boundSize], fetchBound);
// 拖拽已移除
</script>

<template>
  <el-dialog v-model="dialogVisible" title="添加歌曲到歌单" width="980px" :close-on-click-modal="false" @close="handleClose">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="选择添加" name="add">
    <div class="mb-3 flex items-center gap-3">
      <el-select v-model="form.artistId" placeholder="按歌手筛选" clearable class="!w-[200px]">
        <el-option v-for="a in artists" :key="a.artistId" :label="a.artistName" :value="a.artistId" />
      </el-select>
      <el-input v-model="form.songName" placeholder="输入歌曲名搜索" clearable class="!w-[220px]" />
      <el-button type="primary" @click="() => { currentPage = 1 as any; fetchSongs(); }">搜索</el-button>
      <el-button @click="() => { form.artistId = null; form.songName = ''; currentPage = 1 as any; fetchSongs(); }">重置</el-button>
    </div>

     <el-table
      :data="tableData"
      v-loading="tableLoading"
      @selection-change="(rows:any[]) => (selectedIds = rows.map(r => r.songId))"
       :row-key="row => row.songId"
      height="420px"
    >
      <el-table-column type="selection" width="50" />
      <el-table-column label="封面" width="90">
        <template #default="{ row }">
          <el-image :src="row.coverUrl || songCover" fit="cover" class="w-[60px] h-[60px] rounded-md" />
        </template>
      </el-table-column>
      <el-table-column prop="songName" label="歌曲名" min-width="180" />
      <el-table-column prop="artistName" label="歌手" width="160" />
      <el-table-column prop="album" label="专辑" min-width="180" />
    </el-table>

    <div class="mt-3 flex justify-between items-center">
      <span>已选 {{ selectedIds.length }} 首</span>
      <el-pagination
        background
        layout="prev, pager, next, sizes, total"
        :page-sizes="[10, 20, 40]"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
      />
    </div>
      </el-tab-pane>
      <el-tab-pane label="已绑定" name="bound">
        <div class="mb-2 flex justify-between">
          <div>
            <el-button type="danger" @click="handleRemoveBound">移除选中</el-button>
            
          </div>
          <el-pagination
            small
            background
            layout="prev, pager, next, sizes, total"
            :page-sizes="[10,20,40]"
            v-model:current-page="boundPage"
            v-model:page-size="boundSize"
            :total="boundTotal"
          />
        </div>
        <el-table
          ref="boundTableRef"
          :data="boundList"
          v-loading="boundLoading"
          height="420px"
          @selection-change="(rows:any[]) => (boundSelected = rows)"
        >
          <el-table-column type="selection" width="50" />
          <el-table-column label="封面" width="90">
            <template #default="{ row }">
              <el-image :src="row.coverUrl || songCover" fit="cover" class="w-[60px] h-[60px] rounded-md" />
            </template>
          </el-table-column>
          <el-table-column prop="songName" label="歌曲名" min-width="180" />
          <el-table-column prop="artistName" label="歌手" width="160" />
          <el-table-column prop="album" label="专辑" min-width="180" />
          
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button v-if="activeTab==='add'" type="primary" :loading="submitting" @click="handleConfirm">确认添加</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
</style>


