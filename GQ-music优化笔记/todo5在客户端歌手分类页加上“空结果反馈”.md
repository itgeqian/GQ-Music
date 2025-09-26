## 在客户端歌手分类页加上“空结果反馈”

```
  getAllArtists(params).then((res) => {
    if (res.code === 0 && res.data && res.data.items && res.data.items.length > 0) {
      artistList.value = res.data.items.map(item => ({
        artistId: item.artistId,
        name: item.artistName,
        picUrl: item.avatar,
        alias: []
      }))
      total.value = res.data.total
      state.total = res.data.total
    } else {
      artistList.value = []
      total.value = 0
      state.total = 0
    }
  })
}
```

- 当搜索或筛选返回空列表时，不再弹错误通知，也不再保留旧画面，而是清空列表并显示占位文案“没有找到匹配的歌手”。

- 同时分页总数置 0，分页器同步归零。

- 改动文件：vibe-music-client-main/src/pages/artist/index.vue

- 成功分支需同时满足 res.data.items && items.length > 0 才渲染列表；

- 否则进入空状态，渲染占位提示。

现在当你在歌手分类中搜索不到歌手时，页面会直接给出清晰的“空结果”反馈。