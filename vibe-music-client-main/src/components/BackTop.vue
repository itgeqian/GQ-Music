<script setup lang="ts">
const show = ref(false)
const rootRef = ref<HTMLElement | null>(null)
const DEBUG = true
let scrollTarget: Window | HTMLElement = window

const isScrollable = (el: HTMLElement) => {
    const style = getComputedStyle(el)
    const overflowY = style.overflowY
    return (overflowY === 'auto' || overflowY === 'scroll') && el.scrollHeight > el.clientHeight
}

const findScrollParent = (el: HTMLElement | null): Window | HTMLElement => {
    let node: HTMLElement | null = el
    while (node && node !== document.body) {
        if (isScrollable(node)) return node
        node = node.parentElement
    }
    return window
}

const getScrollTop = (): number => {
    if (scrollTarget === window) {
        return window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0
    }
    return (scrollTarget as HTMLElement).scrollTop
}

let lastShow = false
const onScroll = () => {
    const top = getScrollTop()
    const nextShow = top > 300
    if (nextShow !== lastShow) {
        if (DEBUG) console.log('[BackTop] scrollTop:', top, 'show:', nextShow, 'target:', scrollTarget === window ? 'window' : (scrollTarget as HTMLElement).className || (scrollTarget as HTMLElement).tagName)
        lastShow = nextShow
    }
    show.value = nextShow
}

const backToTop = () => {
    if (DEBUG) console.log('[BackTop] backToTop clicked, target is', scrollTarget === window ? 'window' : (scrollTarget as HTMLElement).tagName)
    if (scrollTarget === window) {
        window.scrollTo({ top: 0, behavior: 'smooth' })
        document.documentElement.scrollTo({ top: 0, behavior: 'smooth' })
        document.body.scrollTo({ top: 0, behavior: 'smooth' })
    } else {
        ;(scrollTarget as HTMLElement).scrollTo({ top: 0, behavior: 'smooth' })
    }
}

onMounted(() => {
    // rootRef 一定要渲染出来，才能正确向上寻找滚动容器
    const baseEl = rootRef.value as HTMLElement
    scrollTarget = findScrollParent(baseEl)
    if (DEBUG) console.log('[BackTop] mounted. detected scroll target =', scrollTarget === window ? 'window' : (scrollTarget as HTMLElement).tagName)
    const target: any = scrollTarget
    target.addEventListener('scroll', onScroll, { passive: true })
    // 同时监听窗口，防止容器切换
    window.addEventListener('scroll', onScroll, { passive: true })
    onScroll()
})

onBeforeUnmount(() => {
    const target: any = scrollTarget
    target?.removeEventListener?.('scroll', onScroll)
    window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <div ref="rootRef">
  <transition name="fade">
    <button v-if="show" @click="backToTop"
      class="fixed right-6 bottom-24 z-50 inline-flex items-center justify-center w-10 h-10 rounded-full bg-primary text-white shadow-lg hover:bg-primary/90 transition-colors"
      aria-label="回到顶部">
      <icon-akar-icons:arrow-up />
    </button>
  </transition>
  </div>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>


