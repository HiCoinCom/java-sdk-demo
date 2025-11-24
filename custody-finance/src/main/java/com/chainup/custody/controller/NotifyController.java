package com.chainup.custody.controller;

import com.alibaba.fastjson.JSONObject;
import com.github.hicoincom.MpcClient;
import com.github.hicoincom.api.bean.mpc.NotifyArgs;
import com.github.hicoincom.api.bean.mpc.WalletAddressInfoResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;

@RestController
public class NotifyController {

    @Autowired
    MpcClient mpcClient;

    /**
     * 模拟接收chainup的充提通知
     */
    @ResponseBody
    @GetMapping(value = "/mpc/notify")
    public String notifyHook(String data) {
        NotifyArgs args = mpcClient.getNotifyApi().notifyRequest(data);
        if (args == null) {
            return "FAIL";
        }

        // 充提上账逻辑，参考同步充提记录的job
        System.out.println(JSONObject.toJSONString(args));
        return "SUCCESS";
    }

    /**
     * 模拟用户获取充值地址
     *
     * @return
     */
    @ResponseBody
    @GetMapping(value = "/mpc/deposit/address")
    public String getUserDepositAddress(int uid, String symbol) {
        // 1、获取币种信息，主币，合并地址主链
        // select * from custody_coin where symbol='${symbol}'
        Map<String, String> mockCoin = new HashMap<>();
        mockCoin.put("symbol", "usdtbep20");
        mockCoin.put("base_symbol", "bsc");
        mockCoin.put("merge_addr_symbol", "eth");


        // 查询充值地址表 user_deposit_address
        // select * from user_deposit_address where uid=${uid} and symbol='${mockCoin.merge_addr_symbol}'
        Map<String, String> mockDepositAddr = new HashMap<>();
        mockDepositAddr.put("address", "0xdcb0D867403adE76e75a4A6bBcE9D53C9d05B981");
        if (mockDepositAddr == null) {
            // 使用分布式锁防止给不同用户分配的相同地址
            // redis.setnx()

            // 分配新地址, 从sync_deposit_addr 获取一条未使用的地址
            // GOTOFLAG:
            // select * from sync_deposit_addr where  bind_uid=0 and symbol='${mockCoin.merge_addr_symbol}'
            String mockUnusedAddr = "123456";

            // 将地址更新为已使用
            //  update sync_deposit_addr set bind_uid='${uid}' where address='${mockUnusedAddr}' and bind_uid =0;
            // if(update false) {
            //  go to GOTOFLAG;
            // } else {
            //   // 写入地址表
            //  insert into user_deposit_address values(uid, mockUnusedAddr, merge_addr_symbol)
            //}
        } else {
            // 调用chainup接口验证地址
            WalletAddressInfoResult result = mpcClient.getWalletApi()
                    .walletAddressInfo(mockDepositAddr.get("address"), mockDepositAddr.get("memo"));
            if (result == null || !result.isSuccess() || result.getData() == null
                    || !Integer.valueOf("1").equals(result.getData().getAddrType())) {
                // 获取失败或非用户地址 , 重试机制或者返回失败
                return "error";
            }
        }
        return mockDepositAddr.get("address");
    }
}
