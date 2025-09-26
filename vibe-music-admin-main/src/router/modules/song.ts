export default {
  path: "/song",
  redirect: "/song/index",
  meta: {
    icon: "ri:music-2-fill",
    title: "歌曲管理",
    rank: 3
  },
  children: [
    {
      path: "/song/index",
      name: "SongManagement",
      component: () => import("@/views/song/index.vue"),
      meta: {
        title: "歌曲管理"
      }
    },
    {
      path: "/song/:id/comments",
      name: "SongComments",
      component: () => import("@/views/song/comments.vue"),
      meta: { title: "歌曲评论管理", showLink: false }
    }
  ]
} satisfies RouteConfigsTable;
