type ObjToKeyValArray<T> = {
    [K in keyof T]: [K, T[K]];
}[keyof T];

declare interface Window {
  _loading?: boolean
}

declare var window: Window & typeof globalThis