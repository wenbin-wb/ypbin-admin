/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.modules.system.mapper;

import cn.ypbin.admin.modules.system.entity.SysNotice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 公告 Mapper。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 发布 CAS：仅当库内状态与版本仍等于期望值时，把公告置为已发布并递增版本。
     *
     * <p><b>NULL 必须按草稿参与比较</b>：{@code publish_status} 列允许 NULL，历史行可能没有状态，
     * 服务层 {@code requireNotice} 在内存里把它们当草稿（0）；SQL 若直接
     * {@code publish_status = #{expectedStatus}}，NULL 永远不匹配，这类行就再也发不出去，
     * 报的却是「状态已变化」。</p>
     */
    @Update("""
        UPDATE sys_notice
        SET publish_status = 2, publish_time = #{publishTime},
            publish_version = #{nextVersion}, update_time = #{publishTime}
        WHERE id = #{id}
          AND COALESCE(publish_status, 0) = #{expectedStatus}
          AND publish_version = #{expectedVersion}
          AND is_deleted = 0
        """)
    int publishCas(@Param("id") Long id, @Param("expectedStatus") int expectedStatus,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("nextVersion") long nextVersion,
                   @Param("publishTime") LocalDateTime publishTime);

    @Update("""
        UPDATE sys_notice
        SET publish_status = 3, update_time = #{now}
        WHERE id = #{id} AND publish_status = 2
          AND publish_version = #{expectedVersion} AND is_deleted = 0
        """)
    int revokeCas(@Param("id") Long id, @Param("expectedVersion") long expectedVersion,
                  @Param("now") LocalDateTime now);
}
