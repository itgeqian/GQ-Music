package cn.edu.seig.vibemusic.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "yungou")
public class YunGouProperties {

    /** 商户号或 YunGouOS 商户ID */
    private String mchId;

    /** 商户密钥 */
    private String mchKey;

    /** 授权/登录回调地址 */
    private String callbackUrl;
}


