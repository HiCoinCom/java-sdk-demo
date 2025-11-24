package com.chainup.custody.config;

import com.github.hicoincom.MpcConfig;
import com.github.hicoincom.WaasClientFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.hicoincom.MpcClient;

@Configuration
public class MpcClientConfig {

    /**
     *  这里用于开发测试，正式环境需要将私钥存储在安全的地方，建议加密存储
     */
    @Value("${customRsaPrivate:}")
    private String customRsaPriv;

    /**
     *  这里用于开发测试，正式环境需要将私钥存储在安全的地方，建议加密存储
     */
    @Value("${chainUpRsaPublic:}")
    private String chainUpRsaPub;

    @Value("${chainUpAppId:}")
    private String appId;

    @Bean
    public MpcClient getClient() throws Exception {
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(customRsaPriv) || StringUtils.isBlank(chainUpRsaPub)) {
            throw new Exception("mpc client cfg error, pls check appid、 rsa private key and rsa pub key");
        }

        MpcConfig cfg = new MpcConfig();
        cfg.setAppId(appId);
        cfg.setEnableLog(false);
        cfg.setUserPrivateKey(customRsaPriv);
        cfg.setWaasPublickKey(chainUpRsaPub);
        return WaasClientFactory.CreateMpcClient(cfg);
    }
}
