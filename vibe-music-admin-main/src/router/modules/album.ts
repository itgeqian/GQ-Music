export default {
  path: "/album",
  redirect: "/album/index",
  meta: {
    icon: "ep:headset",
    title: "专辑管理",
    rank: 3.5
  },
  children: [
    {
      path: "/album/index",
      name: "AlbumManagement",
      component: () => import("@/views/album/index.vue"),
      meta: {
        title: "专辑管理"
      }
    },
    {
      path: "/album/:id/comments",
      name: "AlbumComments",
      component: () => import("@/views/album/comments.vue"),
      meta: {
        title: "专辑评论管理",
        showLink: false
      }
    }
  ]
} satisfies RouteConfigsTable;


