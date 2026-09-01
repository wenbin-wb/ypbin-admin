/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.mapper;

import cn.ypbin.admin.system.entity.SysNoticeDelivery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 公告投递记录 Mapper。
 *
 * @author wenbin
 * @since 2026-08-09
 */
public interface SysNoticeDeliveryMapper extends BaseMapper<SysNoticeDelivery> {

    @Update("""
        UPDATE sys_notice_delivery
        SET delivery_status = CASE WHEN retry_count + 1 >= #{maxRetries} THEN 'FAILED' ELSE 'RETRY' END,
            retry_count = retry_count + 1,
            next_retry_time = CASE WHEN retry_count + 1 >= #{maxRetries} THEN NULL ELSE #{now} END,
            error_message = '投递处理超时，已恢复', update_time = #{now}
        WHERE delivery_status = 'PROCESSING' AND update_time < #{staleBefore}
        """)
    int recoverStale(@Param("staleBefore") LocalDateTime staleBefore,
                     @Param("now") LocalDateTime now, @Param("maxRetries") int maxRetries);

    @Update("""
        UPDATE sys_notice_delivery
        SET delivery_status = 'PROCESSING', update_time = #{now}
        WHERE id = #{id}
          AND delivery_status = #{expectedStatus}
          AND delivery_status IN ('PENDING', 'RETRY')
          AND (next_retry_time IS NULL OR next_retry_time <= #{now})
        """)
    int claim(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
              @Param("now") LocalDateTime now);

    @Update("""
        UPDATE sys_notice_delivery
        SET delivery_status = 'SUCCESS', error_message = NULL,
            delivered_time = #{now}, update_time = #{now}
        WHERE id = #{id} AND delivery_status = 'PROCESSING'
        """)
    int markSuccess(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("""
        UPDATE sys_notice_delivery
        SET delivery_status = #{status}, retry_count = #{retryCount},
            next_retry_time = #{nextRetryTime}, error_message = #{errorMessage},
            update_time = #{now}
        WHERE id = #{id} AND delivery_status = 'PROCESSING'
        """)
    int markFailure(@Param("id") Long id, @Param("status") String status,
                    @Param("retryCount") int retryCount,
                    @Param("nextRetryTime") LocalDateTime nextRetryTime,
                    @Param("errorMessage") String errorMessage,
                    @Param("now") LocalDateTime now);

    @Update("""
        UPDATE sys_notice_delivery
        SET delivery_status = 'CANCELLED', error_message = #{reason}, update_time = #{now}
        WHERE id = #{id} AND delivery_status = 'PROCESSING'
        """)
    int markCancelled(@Param("id") Long id, @Param("reason") String reason,
                      @Param("now") LocalDateTime now);
}
