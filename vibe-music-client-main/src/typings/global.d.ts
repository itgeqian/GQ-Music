declare interface Window {
  _loading?: boolean
}

declare var window: Window & typeof globalThis

declare var CustomEvent: {
  new <T>(type: string, eventInitDict?: CustomEventInit<T>): CustomEvent<T>
}

/* Vite */
declare type Recordable<T = any> = Record<string, T>

declare interface ViteEnv {
  VITE_USER_NODE_ENV: 'development' | 'production' | 'test'
  VITE_PUBLIC_PATH: string
  VITE_ROUTER_MODE: 'hash' | 'history'
  VITE_APP_BASE_API: string
  VITE_PROXY: [string, string][]
}

interface ImportMetaEnv extends ViteEnv {
  __: unknown
}
