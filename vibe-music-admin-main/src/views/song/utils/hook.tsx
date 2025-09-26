import "./reset.css";
import dayjs from "dayjs";
import editForm from "../form/index.vue";
import { message } from "@/utils/message";
import songCover from "@/assets/song.jpg";
import { addDialog } from "@/components/ReDialog";
import type { PaginationProps } from "@pureadmin/table";
import ReCropperPreview from "@/components/ReCropperPreview";
import type { FormItemProps } from "../utils/types";
import { getKeyList, deviceDetection } from "@pureadmin/utils";
import {
  getAllArtists,
  getSongList,
  addSong,
  updateSong,
  updateSongCover,
  deleteSong,
  deleteSongs
} from "@/api/system";
import {
  type Ref,
  h,
  ref,
  toRaw,
  watch,
  computed,
  reactive,
  onMounted
} from "vue";

export function useSong(tableRef: Ref, treeRef: Ref) {
  const form = reactive({
    pageNum: 1,
    pageSize: 10,
    // 左侧歌手树的id
    artistId: null,
    songName: null,
    album: null
  });
  const formRef = ref();
  const dataList = ref([]);
  const loading = ref(true);
  // 上传封面信息
  const coverInfo = ref();
  const treeData = ref([]);
  const treeLoading = ref(true);
  const selectedNum = ref(0);
  const pagination = reactive<PaginationProps>({
    total: 0,
    pageSize: 10,
    currentPage: 1,
    background: true
  });
  const columns: TableColumnList = [
    {
      label: "勾选列", // 如果需要表格多选，此处label必须设置
      type: "selection",
      fixed: "left",
      reserveSelection: true // 数据刷新后保留选项
    },
    {
      label: "歌曲编号",
      prop: "songId",
      width: 90
    },
    {
      label: "封面",
      prop: "cover",
      cellRenderer: ({ row }) => (
        (() => {
          // 规范化封面地址：空/空格/"null"/"undefined" 一律回退到默认图
          const normalizeCover = (u: any) => {
            const s = (u ?? "").toString().trim();
            if (!s || s === "null" || s === "undefined") return songCover as any;
            return s as any;
          };
          const safe = normalizeCover(row.cover);
          return (
            <el-image
              fit="cover"
              preview-teleported={true}
              src={safe}
              // 预览列表也使用安全地址
              preview-src-list={Array.of(safe)}
              // Element Plus 提供 fallback，当加载失败时回退
              fallback={songCover as any}
              class="w-[72px] h-[72px] rounded-lg align-middle"
            />
          );
        })()
      ),
      width: 90
    },
    {
      label: "歌名",
      prop: "songName",
      minWidth: 130
    },
    {
      label: "歌手",
      prop: "artistName",
      minWidth: 160
    },
    {
      label: "专辑",
      prop: "album",
      minWidth: 160,
      width: 200
    },
    {
      label: "曲风",
      prop: "style",
      minWidth: 130,
      cellRenderer: ({ row }) =>
        row.style && Array.isArray(row.style)
          ? row.style.map((tag, index) => (
              <el-tag key={index} class="mr-1">
                {tag}
              </el-tag>
            ))
          : "-"
    },
    {
      label: "发行日期",
      minWidth: 130,
      width: 130,
      prop: "releaseTime",
      formatter: ({ releaseTime }) => dayjs(releaseTime).format("YYYY-MM-DD")
    },
    {
      label: "时长",
      prop: "duration",
      minWidth: 100,
      width: 100,
      formatter: ({ duration }) => {
        if (!duration) return "-";
        // 如果duration是数字（秒），转换为时分秒格式
        const seconds = parseInt(duration);
        if (!isNaN(seconds)) {
          const hours = Math.floor(seconds / 3600);
          const minutes = Math.floor((seconds % 3600) / 60);
          const remainingSeconds = seconds % 60;
          
          if (hours > 0) {
            return `${hours}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`;
          } else {
            return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
          }
        }
        // 如果已经是字符串格式，直接返回
        return duration;
      }
    },
    {
      label: "操作",
      fixed: "right",
      width: 180,
      slot: "operation"
    }
  ];
  const buttonClass = computed(() => {
    return [
      "!h-[20px]",
      "reset-margin",
      "!text-gray-500",
      "dark:!text-white",
      "dark:hover:!text-primary"
    ];
  });

  function handleUpdate(row) {
    console.log(row);
  }

  function handleDelete(row) {
    deleteSong(row.songId).then(res => {
      if (res.code === 0) {
        message(`您删除了歌曲编号为 ${row.songId} 的这条数据`, {
          type: "success"
        });
        onSearch();
      } else {
        message("删除歌曲失败", { type: "error" });
      }
    });
  }

  function handleSizeChange(val: number) {
    // console.log(`${val} items per page`);
    pagination.pageSize = val; // 更新每页条目数
    form.pageSize = val; // 更新页码参数
    onSearch(); // 重新获取数据
  }

  function handleCurrentChange(val: number) {
    // console.log(`current page: ${val}`);
    pagination.currentPage = val; // 更新当前页码
    form.pageNum = val; // 更新页码参数
    onSearch(); // 重新获取数据
  }

  /** 当CheckBox选择项发生变化时会触发该事件 */
  function handleSelectionChange(val) {
    selectedNum.value = val.length;
    // 重置表格高度
    tableRef.value.setAdaptive();
  }

  /** 取消选择 */
  function onSelectionCancel() {
    selectedNum.value = 0;
    // 用于多选表格，清空歌曲的选择
    tableRef.value.getTableRef().clearSelection();
  }

  /** 批量删除 */
  function onbatchDel() {
    // 返回当前选中的行
    const curSelected = tableRef.value.getTableRef().getSelectionRows();
    const ids = getKeyList(curSelected, "songId");

    deleteSongs(ids)
      .then(res => {
        if (res.code === 0) {
          message(`已删除歌曲编号为 ${ids} 的数据`, {
            type: "success"
          });
          tableRef.value.getTableRef().clearSelection();
          onSearch();
        } else {
          message("删除歌曲失败", { type: "error" });
        }
      })
      .catch(err => {
        console.error("删除歌曲失败", err);
        message("删除歌曲失败", { type: "error" });
      });
  }

  /** 获取所有歌手 */
  async function fetchArtists() {
    try {
      const result = await getAllArtists();

      if (result.code === 0 && Array.isArray(result.data)) {
        // 转换成树形结构
        treeData.value = result.data.map(item => ({
          label: item.artistName, // 歌手名称
          value: item.artistId, // 唯一标识符
          children: [] // 默认没有子节点，后续可以根据需要添加
        }));

        // 默认选中第一个歌手
        // if (result.data.length > 0) {
        //   form.artistId = result.data[0].artistId;
        // }

        // 默认返回所有数据
        onSearch();
      } else {
        console.error("API 返回异常:", result);
        message("获取歌手列表失败", { type: "error" });
      }
    } catch (error) {
      console.error("请求失败：", error);
      message("会话过期，请重新登录", { type: "error" });
    }
  }

  async function onSearch() {
    try {
      const result = await getSongList(toRaw(form)); // 获取完整的返回数据

      if (result.code === 0 && result.data && result.data.items) {
        // 如果返回状态码为 0（成功），且 data 和 items 存在
        pagination.total = result.data.total; // 设置总条目数
        dataList.value = result.data.items.map(item => ({
          songId: item.songId,
          songName: item.songName,
          artistId: item.artistId,
          artistName: item.artistName,
          album: item.album,
          cover: item.coverUrl,
          audio: item.audioUrl,
          style: item.style ? item.style.split(",") : [],
          releaseTime: item.releaseTime,
          duration: item.duration
        }));
      } else {
        // 如果返回状态码不为 0 或 data.items 不存在
        dataList.value = [];
        pagination.total = 0;
        message("未找到匹配的歌手信息", { type: "warning" });
      }
    } catch (error) {
      console.error("请求失败：", error);
      message("请求失败，请稍后重试", { type: "error" });
    }

    setTimeout(() => {
      loading.value = false;
    }, 500);
  }

  /** 监听歌手变化，重新加载歌曲列表 */
  watch(
    () => form.artistId,
    newId => {
      if (newId) {
        onSearch();
      }
    }
  );

  const resetForm = formEl => {
    if (!formEl) return;
    formEl.resetFields();
    form.artistId = "";
    treeRef.value.onTreeReset();
    onSearch();
  };

  function onTreeSelect({ artistId, selected }) {
    form.artistId = selected ? artistId : "";
    pagination.currentPage = 1; // 重置页码
    form.pageNum = 1; // 重置页码参数
    onSearch();
  }

  function openDialog(title = "新增", row?: FormItemProps) {
    // 未选择左侧歌手时禁止新增，提示先选择歌手
    if (title === "新增" && (!form.artistId || (form.artistId as any) === "")) {
      message("请先在左侧选择歌手", { type: "warning" });
      return;
    }
    // 解析编辑态的歌手ID：优先 row.artistId，其次根据 row.artistName 从树中匹配
    let resolvedArtistId: any = null;
    if (title === "修改") {
      resolvedArtistId = (row as any)?.artistId;
      if (!resolvedArtistId && (row as any)?.artistName && Array.isArray(treeData.value)) {
        const found = (treeData.value as any[]).find(a => a.label === (row as any).artistName);
        resolvedArtistId = found ? found.value : null;
      }
    }
    const dialogProps = {
      formInline: {
        title,
        // 编辑时优先取当前行解析出的 artistId；未选歌手树也可正常编辑
        artistId: title === "新增" ? (form.artistId as any) : (resolvedArtistId ?? (form.artistId as any)),
        artistName: row?.artistName ?? "",
        songId: row?.songId ?? 0,
        songName: row?.songName ?? "",
        album: row?.album ?? "",
        style: row?.style ?? [],
        releaseTime: row?.releaseTime ?? ""
      }
    };
    addDialog({
      title: `${title}歌曲`,
      props: dialogProps,
      width: "46%",
      draggable: true,
      fullscreen: deviceDetection(),
      fullscreenIcon: true,
      closeOnClickModal: false,
      // 透传我们构造的表单数据到子组件
      contentRenderer: () => h(editForm as any, { ref: formRef, ...dialogProps }),
      beforeSure: (done, { options }) => {
        const FormRef = formRef.value.getRef();
        const curData = options.props.formInline as FormItemProps;
        function chores() {
          message(`您${title}了歌名为 ${curData.songName} 的这条数据`, {
            type: "success"
          });
          done(); // 关闭弹框
          onSearch(); // 刷新表格数据
        }
        FormRef.validate(valid => {
          if (valid) {
            console.log("curData", curData);
            // 表单规则校验通过
            if (title === "新增") {
              const payload: any = {
                ...curData,
                artistId: Number((curData as any).artistId) || 0,
                style: Array.isArray(curData.style)
                  ? curData.style.join(",")
                  : ""
              };
              addSong(payload).then(res => {
                if (res.code === 0) {
                  chores();
                } else {
                  message("新增歌曲失败，" + res.message, { type: "error" });
                }
              });
            } else {
              const payload: any = {
                ...curData,
                artistId: Number((curData as any).artistId) || 0,
                style: Array.isArray(curData.style)
                  ? curData.style.join(",")
                  : ""
              };
              updateSong(payload).then(res => {
                if (res.code === 0) {
                  chores();
                } else {
                  message("修改歌曲失败，" + res.message, { type: "error" });
                }
              });
            }
          }
        });
      }
    });
  }

  const cropRef = ref();
  /** 上传封面 */
  async function handleUpload(row) {
    addDialog({
      title: "裁剪、上传封面",
      width: "40%",
      closeOnClickModal: false,
      fullscreen: deviceDetection(),
      contentRenderer: () =>
        h(ReCropperPreview, {
          ref: cropRef,
          imgSrc: row.cover || songCover,
          onCropper: info => (coverInfo.value = info)
        }),
      beforeSure: async done => {
        if (!coverInfo.value?.blob) {
          message("图片裁剪失败，请重试", { type: "error" });
          return;
        }

        // 创建 FormData（为 Blob 指定文件名与扩展名，避免 MinIO 生成 -blob 路径）
        const formData = new FormData();
        const blob = coverInfo.value.blob as Blob;
        const mime = blob?.type || "image/png";
        const ext = mime.includes("png") ? "png" : mime.includes("jpeg") || mime.includes("jpg") ? "jpg" : "png";
        const filename = `${row.songId}-${Date.now()}.${ext}`;
        const file = new File([blob], filename, { type: mime });
        formData.append("cover", file, filename);
        formData.append("songId", row.songId); // 传递歌曲 ID

        try {
          // 调用上传接口
          const res = await updateSongCover(row.songId, formData);

          if (res.code === 0) {
            message("上传封面成功", { type: "success" });
            done(); // 关闭弹框
            onSearch(); // 刷新表格数据
          } else {
            message("上传封面失败", { type: "error" });
          }
        } catch (error) {
          console.error("上传失败:", error);
          message("上传失败，请重试", { type: "error" });
        }
      },
      closeCallBack: () => cropRef.value.hidePopover()
    });
  }

  onMounted(async () => {
    treeLoading.value = true;
    await fetchArtists(); // 先获取歌手列表
    if (form.artistId) {
      await onSearch(); // 获取歌曲列表
    }
    treeLoading.value = false;
  });

  return {
    form,
    loading,
    columns,
    dataList,
    treeData,
    treeLoading,
    selectedNum,
    pagination,
    buttonClass,
    deviceDetection,
    onSearch,
    resetForm,
    onbatchDel,
    openDialog,
    onTreeSelect,
    handleUpdate,
    handleDelete,
    handleUpload,
    handleSizeChange,
    onSelectionCancel,
    handleCurrentChange,
    handleSelectionChange
  };
}
