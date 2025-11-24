package com.chainup.custody.component;

import com.github.hicoincom.MpcClient;
import com.github.hicoincom.api.bean.mpc.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 同步提现记录，分3个job，此外还提供通知webhook接口 接收chainup的提现通知状态变更
 * job1，根据lastID分布同步记录
 * job2，定时查询未完成的提现
 * job3，定时将用户发起的提现推送到chainUp平台
 */
@Component
public class WithdrawSyncTask {

    @Autowired
    private MpcClient mpcClient;

    /**
     * 这里是测试用，真实情况下可以将这个字段保存到配置表，通过后台功能可以修改
     */
    @Value("${chainUpSubWalletId:}")
    private Integer chainUpSubWalletId;

    private String getRequestId(int withdrawId) {
        return "requestID-" + withdrawId;
    }

    private Integer syncWithdrawInfo(WithdrawRecordResult result) {
        Integer lastId = 0;
        Integer statusSucc = 2000;
        // 将同步的数据 更新入库，txid，状态，矿工费、custody_id等
        for (WithdrawRecord row : result.getData()) {
            // 判断是否完成
            // select * from user_withdraw where custody_id='${row.getId()}' order by custody_id desc limit 1
            // 模拟从数据库查出来的数据
            Map<String, Object> dbRow = new HashMap<>();

                /*if(dbRow.status == "完成") {
                    continue;
                }*/

                /*if(dbRow.custody_id > 0) {
                    continue;
                }*/

            // 将数据写入数据库,注意 requestId 唯一；requestId在推送提现到ChainUp 时设置
            // update user_withdraw set custody_id=row.getId(), txid=row.getTxid, status=xxx, fee=row.getRealFee() where request_id=row.getRequestId()
            lastId = row.getId();
            System.out.println("成功同步一条提现信息" + row.getId() + ", " + row.getSymbol() + " " + row.getTxid());

            // 判断状态，如果是完成状态，触发队列 给用户扣除余额，注意不要重复上账
            if (statusSucc.equals(row.getStatus())) {
                // 触发提现扣账
            }
        }
        return lastId;
    }

    /**
     * job1 同步全量提现数据
     */
    @Scheduled(cron = "0 0/2 * * * *")
    public void scheduledTask() {
        System.out.println("任务开始 WithdrawSyncTask：" + LocalDateTime.now());
        // 从数据库查询最后一条同步的ID(指托管平台的ID) select custody_id from user_withdraw order by custody_id desc limit 1;
        Map<String, Integer> mockLastRow = new HashMap<>();
        mockLastRow.put("custody_id", 100);

        int lastId = mockLastRow.getOrDefault("custody_id", 100);
        while (true) {
            WithdrawRecordResult result = mpcClient.getWithdrawApi().syncWithdrawRecords(lastId);
            if (result == null || !result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
                // 如果需要打印相应日志
                return;
            }

            // 将同步的数据 更新入库，txid，状态，矿工费、custody_id等
            lastId = syncWithdrawInfo(result);
        }
    }

    /**
     * job2 未完成同步状态
     */
    @Scheduled(cron = "0 0/3 * * * *")
    public void scheduledTask2() {
        System.out.println("任务开始 WithdrawSyncTask2：" + LocalDateTime.now());
        // 分页未完成的提现的ID(指商户平台的ID， 非chainup平台的ID)
        int lastId = 0;
        while (true) {
            // 查询20条未完成的提现
            // select id from user_withdraw where status in (未完成状态1, 未完成状态2...) and id>${lastId} order by id asc limit 20;

            List<Integer> mockUnDoneIdList = Arrays.asList(1, 2); // 模拟查询到未完成的ID
            if (mockUnDoneIdList == null || mockUnDoneIdList.isEmpty()) {
                return;
            }

            List<String> requestIdList = new ArrayList<>();
            for (Integer id : mockUnDoneIdList) {
                requestIdList.add(getRequestId(id));
            }

            WithdrawRecordResult result = mpcClient.getWithdrawApi().getWithdrawRecords(requestIdList);
            if (result == null || !result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
                // 如果需要打印相应日志
                return;
            }

            // 将同步的数据 更新入库，txid，状态，矿工费、custody_id等
            lastId = syncWithdrawInfo(result);
        }
    }

    /**
     * job3 将用户发送的提现推到chainup平台
     */
    @Scheduled(cron = "0 0/3 * * * *")
    public void scheduledTask3() {
        System.out.println("任务开始 WithdrawPullTask3：" + LocalDateTime.now());
        // 分页未完成的提现的ID(指商户平台的ID， 非chainup平台的ID)
        int lastId = 0;
        while (true) {
            // 查询20条未推送的提现
            // select * from user_withdraw where status in (未完成状态1, 未完成状态2...) and id>${lastId} and custody_id=0 order by id asc limit 20;

            List<Map<String, Object>> mockWithdraws = new ArrayList<>(); // 模拟从数据库查询到的 提现数据
            Map<String, Object> mockData = new HashMap<>();
            mockData.put("id", 1);
            mockData.put("symbol", "eth");
            mockData.put("amount", "0.000000001");
            mockData.put("address_to", "");
            mockData.put("memo", "0xdcb0D867403adE76e75a4A6bBcE9D53C9d05B981");

            mockWithdraws.add(mockData);
            if (mockWithdraws == null || mockWithdraws.isEmpty()) {
                return;
            }

            List<String> requestIdList = new ArrayList<>();
            for (Map<String, Object> row : mockWithdraws) {
                int withdrawId = (Integer) row.get("id");
                lastId = withdrawId;
                WithdrawArgs args = new WithdrawArgs();
                args.setAddressTo((String) row.get("address_to"));

                // 注意小数精度
                args.setAmount((String) row.get("amount"));
                args.setSymbol((String) row.get("symbol"));
                // memo可选
                args.setMemo((String) row.get("memo"));

                // 注意requestId 不要变更，变更可能导至多次出金
                args.setRequestId(getRequestId(withdrawId));
                args.setSubWalletId(chainUpSubWalletId);

                WithdrawResult result = mpcClient.getWithdrawApi().withdraw(args);
                if (result == null) {
                    continue;
                }

                if (result.isSuccess()) {
                    // 更新custody_id
                    // update user_withdraw set custody_id='${result.getData().getWithdrawId()}' where id='${withdrawId}'
                    continue;
                }
                // 其它逻辑
            }
        }
    }
}
