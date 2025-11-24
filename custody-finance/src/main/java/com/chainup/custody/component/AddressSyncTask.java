package com.chainup.custody.component;

import com.github.hicoincom.MpcClient;
import com.github.hicoincom.api.bean.mpc.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 同步用户新地址，缓存到中间表，当有新用户需要地址时，从缓存表获取一条分配给新用户
 * 比如用户充值地址表名为 user_deposit_address; 临时缓存表名为 sync_deposit_addr，这个job按策略从chainup获取新地址写入 sync_deposit_addr
 * 当用户获取充值地址时，从user_deposit_address 获取，如果没有获取到；则从sync_deposit_addr表获取一条未使用的 地址写入 表 user_deposit_address（注意这个过程加锁，防止一个地址分配给多个用户）
 */
@Component
public class AddressSyncTask {

    @Autowired
    private MpcClient mpcClient;

    /**
     * 这里是测试用，真实情况下可以将这个字段保存到配置表，通过后台功能可以修改
     */
    @Value("${chainUpSubWalletId:}")
    private Integer chainUpSubWalletId;

    @Scheduled(cron = "0 0/2 * * * *")
    public void scheduledTask() {
        System.out.println("任务开始 addressSyncTask：" + LocalDateTime.now());
        SupportMainChainResult result = mpcClient.getWorkSpaceApi().getSupportMainChain();
        if (result == null || !result.isSuccess() || result.getData() == null ||
                result.getData().getOpenMainChain() == null ||
                result.getData().getOpenMainChain().isEmpty()) {
            System.out.println("钱包未开通相应主链");
            return;
        }

        // 未使用地址阀值(<100则获取500个新地址)
        int minCount = 10;
        // 获取新地址数量(注意不要浪费地址，超一定地址数量会产生费用)
        int createCount = 3;
        // userAddrType, 类型为1的地址才能分配地址用户，其它类型的地址为出金地址
        Integer userAddrType = 1;

        // 按主链分别获取
        for (SupportCoin row : result.getData().getOpenMainChain()) {
            // 判断合并地址（EVM系列主链充值地址相同，比如ETH跟BSC链地址）
            //(从同步币种job缓存的币种信息表查询) select MergeAddressSymbol from custody_coin where symbol='${row.getSymbol()}' order by custody_id desc limit 1
            // 这里是示例所以直接用的symbol，生产环境请使用 MergeAddressSymbol
            String mergeAddrSymbol = row.getSymbol();

            // 查询地址缓存表 未使用的地址数量
            // select count(1) from sync_deposit_addr where symbol='${mergeAddrSymbol}' and bind_uid=0;
            int mockUnusedCount = 2;
            if (mockUnusedCount > minCount) {
                continue;
            }

            // 调用接口获取新地址
            for (int i = 0; i < createCount; i++) {
                WalletAddressResult addr = mpcClient.getWalletApi()
                        .createWalletAddress(chainUpSubWalletId, mergeAddrSymbol);
                if (addr == null || !addr.isSuccess() || StringUtils.isBlank(addr.getData().getAddress())) {
                    System.out.println("获取地址失败 " + i);
                    continue;
                }

                if (!userAddrType.equals(addr.getData().getAddrType())) {
                    continue;
                }

                System.out.println(mergeAddrSymbol + "获取地址成功 " + i + " addr:" + addr.getData().getAddress() + " memo:" + addr.getData().getMemo());
                // 将地址写入表 sync_deposit_addr 并指定未使用
                // insert into sync_deposit_addr (symbol, address, memo,bind_uid) values('${mergeAddrSymbol}','${ addr.getData().getAddress()}','${addr.getData().getMemo()}',0)
            }
        }
    }
}
