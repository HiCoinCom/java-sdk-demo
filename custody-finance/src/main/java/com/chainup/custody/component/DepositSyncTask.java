package com.chainup.custody.component;

import com.github.hicoincom.MpcClient;
import com.github.hicoincom.api.bean.mpc.DepositRecord;
import com.github.hicoincom.api.bean.mpc.DepositRecordResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步充值记录，分两个job，此外还提供充值通知webhook接收chainup的充值通知
 * job1，根据lastID分布同步记录
 * job2，定时查询未完成的充值
 */
@Component
public class DepositSyncTask {

    @Autowired
    private MpcClient mpcClient;

    /**
     * job1 同步全量充值数据
     */
    @Scheduled(cron = "0 0/2 * * * *")
    public void scheduledTask() {
        System.out.println("任务开始 DepositSyncTask：" + LocalDateTime.now());
        // 从数据库查询最后一条同步的ID(指托管平台的ID) select custody_id from user_deposit order by custody_id desc limit 1;
        Map<String, Integer> mockLastRow = new HashMap<>();
        mockLastRow.put("custody_id", 100);

        int lastId = mockLastRow.getOrDefault("custody_id", 100);
        Integer depositSucc = 2000;
        while (true) {
            DepositRecordResult result = mpcClient.getDepositApi().syncDepositRecords(lastId);
            if (result == null || !result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
                // 如果需要打印相应日志
                return;
            }

            // 将同步的数据入库（先判断数据库是否存在）注意：custody_id字段唯一
            for (DepositRecord row : result.getData()) {
                // 判断是否存在
                // select custody_id from user_deposit where custody_id='${row.getId()}' order by custody_id desc limit 1
                /*if(数据已存在，跳过) {
                    continue;
                }*/

                // 将数据写入数据库
                // insert into user_deposit values(row.getId(), row.getSymbol() ,row.xxx)
                lastId = row.getId();
                System.out.println("成功同步一条充值信息" + row.getId() + ", " + row.getSymbol());

                // 判断充值状态，如果是完成状态，触发队列 给用户增加余额，注意不要重复上账
                if (depositSucc.equals(row.getStatus())) {
                    // 触发充值上账
                }
            }
        }
    }

    /**
     * job2 未完成充值同步状态
     */
    @Scheduled(cron = "0 0/3 * * * *")
    public void scheduledTask2() {
        System.out.println("任务开始 DepositSyncTask2：" + LocalDateTime.now());
        // 分页未完成的充值的ID(指托管平台的ID)
        int lastId = 0;
        Integer depositSucc = 2000;
        while (true) {
            // 查询20条未完成的充值
            // select custody_id from user_deposit where status in (未完成状态1, 未完成状态2...) and custody_id>${lastId} order by custody_id asc limit 20;
            List<Integer> mockUnDoneCustodyIdList = Arrays.asList(1, 2);
            if (mockUnDoneCustodyIdList == null || mockUnDoneCustodyIdList.isEmpty()) {
                return;
            }

            DepositRecordResult result = mpcClient.getDepositApi().getDepositRecords(mockUnDoneCustodyIdList);
            if (result == null || !result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
                // 如果需要打印相应日志
                return;
            }

            // 将同步的数据入库（先判断数据库是否存在）注意：custody_id字段唯一
            for (DepositRecord row : result.getData()) {
                if (row.getId() > lastId) {
                    lastId = row.getId();
                }

                // 更新充值状态, 注意加锁
                // update user_deposit set status='完成' where custody_id=${row.getId()} and status='原来的status'
                System.out.println("成功同步一条充值信息" + row.getId() + ", " + row.getSymbol());

                // 判断充值状态，如果是完成状态，触发队列 给用户增加余额，注意不要重复上账
                if (depositSucc.equals(row.getStatus())) {
                    // 触发充值上账
                }
            }
        }
    }
}
