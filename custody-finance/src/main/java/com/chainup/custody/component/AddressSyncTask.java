package com.chainup.custody.component;

import com.github.hicoincom.MpcClient;
import com.github.hicoincom.api.bean.mpc.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                // insert into sync_deposit_addr (symbol, address, memo,bind_uid,custody_id) values('${mergeAddrSymbol}','${ addr.getData().getAddress()}','${addr.getData().getMemo()}',0,'${addr.getData().getId()}')
            }
        }
    }

    /**
     * 同步全量地址数据，正常情况下用不到这个任务（注意memo类型地址都相同，所以只返回一条数据，memo是最后一个memo）
     */
    @Scheduled(cron = "0 0 0/1 * * *")
    public void scheduledTask2() {
        System.out.println("任务开始 addressSyncTask2：" + LocalDateTime.now());

        SupportMainChainResult chainResult = mpcClient.getWorkSpaceApi().getSupportMainChain();
        if (chainResult == null || !chainResult.isSuccess() || chainResult.getData() == null ||
                chainResult.getData().getOpenMainChain() == null ||
                chainResult.getData().getOpenMainChain().isEmpty()) {
            System.out.println("钱包未开通相应主链");
            return;
        }

        List<String> syncChain = new ArrayList<>();
        for (SupportCoin chain : chainResult.getData().getOpenMainChain()) {
            // 判断合并地址（EVM系列主链充值地址相同，比如ETH跟BSC链地址）
            //(从同步币种job缓存的币种信息表查询) select MergeAddressSymbol from custody_coin where symbol='${row.getSymbol()}' order by custody_id desc limit 1
            // 这里是示例所以直接用的symbol，生产环境请使用 MergeAddressSymbol
            Map<String, Object> mockCoin = new HashMap<>();
            mockCoin.put("mergeAddrSymbol", chain.getSymbol());
            mockCoin.put("supportMemo", false);

            String mergeAddrSymbol = (String) mockCoin.get("mergeAddrSymbol");
            if (syncChain.contains(mergeAddrSymbol)) {
                //evm系列主链地址相同，只需要同步一次
                continue;
            }

            if ((Boolean) mockCoin.get("supportMemo")) {
                // memo 类型不需要同步
                continue;
            }

            syncChain.add(mergeAddrSymbol);
            this.syncAllAddress(mergeAddrSymbol);
        }
    }

    private void syncAllAddress(String mergeAddrSymbol) {
        // 模拟从数据库查询最后的一个custody address id
        Map<String, Integer> mockLastRow = new HashMap<>();
        mockLastRow.put("custody_id", 100);
        // userAddrType, 类型为1的地址才能分配地址用户，其它类型的地址为出金地址
        Integer userAddrType = 1;

        int lastId = mockLastRow.getOrDefault("custody_id", 0);
        while (true) {
            WalletAddressListResult result = mpcClient.getWalletApi().queryWalletAddress(chainUpSubWalletId, mergeAddrSymbol, lastId);
            if (result == null || !result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
                // 如果需要打印相应日志
                return;
            }

            // 将同步的币种入库（先判断数据库是否存在）注意：symbol 字段唯一
            for (WalletAddress row : result.getData()) {
                if (row.getId() > lastId) {
                    lastId = row.getId();
                }

                if (!userAddrType.equals(row.getAddrType())) {
                    continue;
                }

                // 判断地址是否存在
                // select custody_id from sync_deposit_addr where address='${row.getAddress()}' order by custody_id desc limit 1
                /*if(存在){
                    continue;
                }*/

                System.out.println(mergeAddrSymbol + "成功同步一条地址 addr:" + row.getAddress() + " memo:" + row.getMemo());
                // 将地址写入表 sync_deposit_addr 并指定未使用
                // // insert into sync_deposit_addr (symbol, address, memo,bind_uid,custody_id) values('${mergeAddrSymbol}','${ addr.getData().getAddress()}','${addr.getData().getMemo()}',0,'${addr.getData().getId()}')
            }
        }
    }
}
