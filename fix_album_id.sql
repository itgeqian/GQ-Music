-- 修复批量导入后缺失的album_id字段
-- 这个脚本会为所有album_id为NULL的歌曲设置正确的album_id

-- 首先查看需要修复的数据
SELECT 
    s.id,
    s.artist_id,
    s.album,
    s.album_id,
    a.album_id as correct_album_id
FROM tb_song s
LEFT JOIN tb_album a ON s.artist_id = a.artist_id AND s.album = a.title
WHERE s.album_id IS NULL
ORDER BY s.artist_id, s.album, s.id;

-- 更新缺失album_id的歌曲
UPDATE tb_song s
INNER JOIN tb_album a ON s.artist_id = a.artist_id AND s.album = a.title
SET s.album_id = a.album_id
WHERE s.album_id IS NULL;

-- 验证修复结果
SELECT 
    COUNT(*) as total_songs,
    COUNT(album_id) as songs_with_album_id,
    COUNT(*) - COUNT(album_id) as songs_without_album_id
FROM tb_song;

-- 查看修复后的数据
SELECT 
    s.id,
    s.artist_id,
    s.album,
    s.album_id,
    a.title as album_title
FROM tb_song s
LEFT JOIN tb_album a ON s.album_id = a.album_id
WHERE s.album = '三体广播剧'
ORDER BY s.id;

