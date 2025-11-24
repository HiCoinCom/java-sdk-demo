package com.chainup.custody.config;

import com.github.hicoincom.MpcConfig;
import com.github.hicoincom.WaasClientFactory;
import com.github.hicoincom.crypto.rsa.RSAHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.hicoincom.MpcClient;

import java.security.PrivateKey;

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
        //System.out.println(RSAHelper.genRSAKeys().getPrivateKey());
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(customRsaPriv) || StringUtils.isBlank(chainUpRsaPub)) {
            throw new Exception("mpc client cfg error, pls check appid、 rsa private key and rsa pub key");
        }

        MpcConfig cfg = new MpcConfig();
        cfg.setAppId(appId);
        cfg.setEnableLog(false);
        cfg.setUserPrivateKey(customRsaPriv);
        cfg.setWaasPublickKey(chainUpRsaPub);

        System.out.println("");
        String pub = RSAHelper.getPublicKeyStringByPrivateKey(cfg.getUserPrivateKey());
        System.out.println("请将以下Rsa公钥配置到ChainUp平台 Api管理菜单的 Client system RSA Public Key：");
        System.out.println(pub);
        System.out.println("");

        return WaasClientFactory.CreateMpcClient(cfg);
    }
}
