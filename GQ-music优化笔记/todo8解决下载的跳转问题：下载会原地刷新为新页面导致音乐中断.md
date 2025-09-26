## todo8解决下载的跳转问题：下载会原地刷新为新页面导致音乐中断

定位到“下载”按钮逻辑在 vibe-music-client-main/src/components/Table.vue 的 downLoadMusic 方法，并把它改为使用 fetch 获取 Blob 再触发浏览器下载，避免在当前页直接打开从而打断播放；并加了失败回退到新标签打开。

现在会得到两点体验优化：

- 点击“下载”不会跳转当前页面，播放器不中断；
- 若服务端不支持跨域下载或链接异常，会在新标签中打开，尽量保证可获取到文件。

关键改动
```startLine:endLine:vibe-music-client-main/src/components/Table.vue
126:134:const downLoadMusic = async (row: Song, e: Event) => {
  e.stopPropagation() // 阻止事件冒泡
  const fileName = `${row.songName} - ${row.artistName}.mp3`
  try {
    const response = await fetch(row.audioUrl, { mode: 'cors' })
    if (!response.ok) throw new Error('下载链接不可用')
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = fileName
    link.rel = 'noopener'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(blobUrl)
  } catch (err) {
    const newWin = window.open(row.audioUrl, '_blank', 'noopener,noreferrer')
    if (!newWin) {
      ElMessage.error('下载失败，请检查网络或稍后再试')
    }
  }
}
```

