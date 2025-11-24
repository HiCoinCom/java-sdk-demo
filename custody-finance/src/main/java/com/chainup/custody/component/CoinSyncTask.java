package com.chainup.custody.component;

import com.github.hicoincom.MpcClient;
import com.github.hicoincom.api.bean.mpc.CoinDetails;
import com.github.hicoincom.api.bean.mpc.CoinDetailsResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 同步币种, 币种数据基本无更新，同步频率可以降低
 */
@Component
public class CoinSyncTask {

    @Autowired
    private MpcClient mpcClient;

    @Scheduled(cron = "0 0/20 * * * *")
    public void scheduledTask() {
        System.out.println("任务开始 CoinSyncTask：" + LocalDateTime.now());
        // 从数据库查询最后一条同步的ID(指托管平台的ID) select custody_id from custody_coin order by custody_id desc limit 1;
        Map<String, Integer> mockLastCoinRow = new HashMap<>();
        mockLastCoinRow.put("custody_id", 100);

        int lastId = mockLastCoinRow.getOrDefault("custody_id", 0);
        while (true) {
            CoinDetailsResult result = mpcClient.getWorkSpaceApi().getCoinDetails(
                    null, null, null, lastId, 1000);

            if (result == null || !result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
                // 如果需要打印相应日志
                return;
            }

            // 将同步的币种入库（先判断数据库是否存在）注意：symbol 字段唯一
            for (CoinDetails row : result.getData()) {
                // 判断币种是否存在
                // select custody_id from custody_coin where symbol='${row.getSymbol()}' order by custody_id desc limit 1

                // 将币种数据写入数据库
                // insert into custody_coin values(row.getId(), row.getMergeAddressSymbol() ,row.xxx)
                lastId = row.getId();
                System.out.println("成功同步一条币种信息" + row.getId() + ", " + row.getSymbol());
            }
        }
    }
}
