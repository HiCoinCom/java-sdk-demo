package com.chainup.custody.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

@Configuration
@EnableScheduling
public class ScheduleConfig implements SchedulingConfigurer {

    /**
     * 自定义线程池
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("finance-schedule-%d")
                .setDaemon(Boolean.FALSE)
                .build();

        // 固定20个线程
        ExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(20, threadFactory);
        taskRegistrar.setScheduler(scheduledExecutorService);
    }
}