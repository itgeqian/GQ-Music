import {
  createRouter,
  createWebHistory,
  createWebHashHistory,
} from 'vue-router'

const mode = import.meta.env.VITE_ROUTER_MODE

const routerMode = {
  hash: () => createWebHashHistory(),
  history: () => createWebHistory(),
}

const router = createRouter({
  history: routerMode[mode](),
  strict: false,
  scrollBehavior: () => ({ left: 0, top: 0 }),
  routes: [
    {
      path: '/',
      component: () => import('@/pages/index.vue'),
    },
    {
      path: '/album/:id',
      component: () => import('@/pages/album/[id].vue'),
    },
    {
      path: '/album',
      redirect: '/library',
    },
    {
      path: '/library',
      component: () => import('@/pages/library/index.vue'),
    },
    {
      path: '/artist',
      component: () => import('@/pages/artist/index.vue'),
    },
    {
      path: '/artist/:id',
      component: () => import('@/pages/artist/[id].vue'),
    },
    {
      path: '/playlist',
      component: () => import('@/pages/playlist/index.vue'),
    },
    {
      path: '/playlist/:id',
      component: () => import('@/pages/playlist/[id].vue'),
    },
    {
      path: '/like',
      component: () => import('@/pages/like/index.vue'),
    },
    {
      path: '/my-playlists',
      component: () => import('@/pages/my-playlists/index.vue'),
    },
    {
      path: '/recent',
      component: () => import('@/pages/recent/index.vue'),
    },
    {
      path: '/profile/:id',
      component: () => import('@/pages/profile/[id].vue'),
    },
    {
      path: '/profile',
      component: () => import('@/pages/profile/[id].vue'),
    },
    {
      path: '/user',
      component: () => import('@/pages/user/index.vue'),
    },
    {
      path: '/user/search',
      component: () => import('@/pages/user/search.vue'),
    },
  ],
})

export default router
