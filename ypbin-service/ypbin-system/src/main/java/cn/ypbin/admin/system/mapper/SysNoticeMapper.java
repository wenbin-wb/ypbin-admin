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

import cn.ypbin.admin.system.entity.SysNotice;
import cn.ypbin.admin.system.enums.NoticePublishStatusEnum;
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
     * 发布 CAS：仅当库内状态与版本仍等于期望值时，把公告置为目标状态并递增版本。
     *
     * <p>状态不写死在 SQL 里：库值口径由 {@link NoticePublishStatusEnum} 统一，调用方传 code。</p>
     *
     * <p><b>NULL 必须按草稿参与比较</b>：{@code publish_status} 是 NULL 列，历史行可能没有状态，
     * {@code SysNoticeServiceImpl#requireNotice} 在内存里把它们当草稿（0）；SQL 若直接
     * {@code publish_status = #{expectedStatus}}，NULL 永远不匹配，这类行就再也发不出去/删不掉，
     * 报的却是「状态已变化」。</p>
     *
     * @param id              公告 ID
     * @param expectedStatus  期望的当前状态
     * @param targetStatus    写入的目标状态
     * @param expectedVersion 期望的当前版本
     * @param nextVersion     写入的新版本
     * @param publishTime     发布时间
     * @return 影响行数（1=成功，0=状态或版本已变化）
     */
    @Update("""
        UPDATE sys_notice
        SET publish_status = #{targetStatus}, publish_time = #{publishTime},
            publish_version = #{nextVersion}, update_time = #{publishTime}
        WHERE id = #{id}
          AND COALESCE(publish_status, 0) = #{expectedStatus}
          AND publish_version = #{expectedVersion}
          AND is_deleted = 0
        """)
    int publishCas(@Param("id") Long id, @Param("expectedStatus") int expectedStatus,
                   @Param("targetStatus") int targetStatus,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("nextVersion") long nextVersion,
                   @Param("publishTime") LocalDateTime publishTime);

    /**
     * 撤回 CAS：仅当库内仍处于期望状态且版本未变化时，把公告置为目标状态。
     *
     * <p>NULL 口径同 {@link #publishCas}（按草稿 0 比较）。</p>
     *
     * @param id              公告 ID
     * @param expectedStatus  期望的当前状态
     * @param targetStatus    写入的目标状态
     * @param expectedVersion 期望的当前版本
     * @param now             更新时间
     * @return 影响行数（1=成功，0=状态或版本已变化）
     */
    @Update("""
        UPDATE sys_notice
        SET publish_status = #{targetStatus}, update_time = #{now}
        WHERE id = #{id} AND COALESCE(publish_status, 0) = #{expectedStatus}
          AND publish_version = #{expectedVersion} AND is_deleted = 0
        """)
    int revokeCas(@Param("id") Long id, @Param("expectedStatus") int expectedStatus,
                  @Param("targetStatus") int targetStatus,
                  @Param("expectedVersion") long expectedVersion,
                  @Param("now") LocalDateTime now);
}
