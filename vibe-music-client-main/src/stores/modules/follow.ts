import { defineStore } from 'pinia'

interface FollowedArtist {
  artistId: number
  artistName: string
}

export const useFollowStore = defineStore('follow', {
  state: () => ({
    followedArtists: [] as FollowedArtist[],
  }),
  getters: {
    isFollowing: (state) => (artistId: number) =>
      state.followedArtists.some((a) => a.artistId === artistId),
  },
  actions: {
    follow(artist: FollowedArtist) {
      if (!this.followedArtists.some((a) => a.artistId === artist.artistId)) {
        this.followedArtists.push({ artistId: artist.artistId, artistName: artist.artistName })
      }
    },
    unfollow(artistId: number) {
      this.followedArtists = this.followedArtists.filter((a) => a.artistId !== artistId)
    },
    toggle(artist: FollowedArtist) {
      if (this.isFollowing(artist.artistId)) {
        this.unfollow(artist.artistId)
      } else {
        this.follow(artist)
      }
    },
  },
  persist: {
    key: 'vibe-followed-artists',
  },
})


