package cn.edu.seig.vibemusic.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LyricLine {
    private long timeMs; // 行开始时间
    private String text; // 文本
}


