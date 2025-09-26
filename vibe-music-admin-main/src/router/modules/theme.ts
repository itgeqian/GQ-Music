export default {
  path: "/theme",
  redirect: "/theme/index",
  meta: { icon: "ri:shirt-fill", title: "主题管理", rank: 8 },
  children: [
    {
      path: "/theme/index",
      name: "ThemeManagement",
      component: () => import("@/views/theme/index.vue"),
      meta: { title: "主题管理" }
    }
  ]
} satisfies RouteConfigsTable;


