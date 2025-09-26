import axios, {
  AxiosInstance,
  AxiosRequestConfig,
  InternalAxiosRequestConfig,
  AxiosRequestHeaders,
} from 'axios'
import NProgress from '@/config/nprogress'
import 'nprogress/nprogress.css'
import { UserStore } from '@/stores/modules/user'
import { ElMessage } from 'element-plus'

const instance: AxiosInstance = axios.create({
  baseURL: 'http://localhost:8080', // 设置为后端服务地址
  timeout: 20000, // 设置超时时间 20秒
  headers: {
    Accept: 'application/json, text/plain, */*',
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
  },
  withCredentials: false,
})

// 请求拦截器
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 开启进度条
    NProgress.start()

    // 登录、验证码、公开接口不加 token
    if (
      config.url?.includes('/user/login') ||
      config.url?.includes('/captcha/generate') ||
      config.url?.includes('/banner/getBannerList') ||
      config.url?.includes('/search/getHotKeywords') ||
      config.url?.includes('/search/reportKeyword')
    ) {
      return config
    }

    // 从 pinia 中获取token
    const userStore = UserStore()
    const token = userStore.userInfo?.token

    if (token) {
      // 确保headers对象存在并且是正确的类型
      if (!config.headers) {
        config.headers = {} as AxiosRequestHeaders
      }
      // 添加Bearer前缀（若已带此前缀则不重复添加）
      config.headers.Authorization = token.startsWith('Bearer ')
        ? token
        : `Bearer ${token}`
    }

    // console.log('请求URL:', config.url)
    // console.log('请求头:', config.headers)
    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
instance.interceptors.response.use(
  (response) => {
    // 关闭进度条
    NProgress.done()
    const { data } = response
    return data
  },
  (error) => {
    // 关闭进度条
    NProgress.done()

    if (error.response) {
      switch (error.response.status) {
        case 401: {
          const userStore = UserStore()
          const isLoggedIn = userStore.isLoggedIn
          const method = String(error.config?.method || '').toLowerCase()

          // 登录接口401 → 账号或密码错误
          if (error.config.url?.includes('/user/login')) {
            ElMessage.error('邮箱或密码错误')
            break
          }

          if (isLoggedIn) {
            // 已登录 → token 失效，清理并提示
            userStore.clearUserInfo()
            ElMessage.error('登录已过期，请重新登录')
          } else {
            // 未登录：GET 请求静默；非 GET 给轻提示
            if (method !== 'get') {
              ElMessage.warning('请先登录')
            }
          }
          break
        }
        case 403:
          ElMessage.error('没有权限')
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误')
          break
        default:
          ElMessage.error('网络错误')
      }
    } else {
      ElMessage.error('网络连接失败')
    }

    return Promise.reject(error)
  }
)

// 封装request方法
export const http = <T>(
  method: 'get' | 'post' | 'put' | 'delete' | 'patch',
  url: string,
  config?: Omit<AxiosRequestConfig, 'method' | 'url'>
): Promise<T> => {
  return instance({ method, url, ...config })
}

// 封装get方法
export const httpGet = <T>(url: string, params?: object): Promise<T> =>
  instance.get(url, { params })

// 封装post方法
export const httpPost = <T>(
  url: string,
  data?: object,
  header?: object
): Promise<T> => instance.post(url, data, { headers: header })

// 封装upload方法
export const httpUpload = <T>(
  url: string,
  formData: FormData,
  header?: object
): Promise<T> => {
  return instance.post(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      ...header,
    },
  })
}
