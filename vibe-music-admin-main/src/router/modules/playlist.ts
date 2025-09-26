export default {
  path: "/playlist",
  redirect: "/playlist/index",
  meta: {
    icon: "ri:album-fill",
    title: "歌单管理",
    rank: 4
  },
  children: [
    {
      path: "/playlist/index",
      name: "playlistManagement",
      component: () => import("@/views/playlist/index.vue"),
      meta: {
        title: "歌单管理"
      }
    },
    {
      path: "/playlist/user",
      name: "UserPlaylistManagement",
      component: () => import("@/views/playlist/user.vue"),
      meta: {
        title: "用户歌单（只读）"
      }
    },
    {
      path: "/playlist/bind/:id",
      name: "PlaylistBindSongs",
      component: () => import("@/views/playlist/bind.vue"),
      meta: { title: "歌单添加歌曲", showLink: false }
    },
    {
      path: "/playlist/:id/comments",
      name: "PlaylistComments",
      component: () => import("@/views/playlist/comments.vue"),
      meta: { title: "歌单评论管理", showLink: false }
    }
  ]
} satisfies RouteConfigsTable;
