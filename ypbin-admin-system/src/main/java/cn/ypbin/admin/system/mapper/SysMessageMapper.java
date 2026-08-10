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

import cn.ypbin.admin.system.entity.SysMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;

/**
 * 用户消息 Mapper。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface SysMessageMapper extends BaseMapper<SysMessage> {

    @Insert("""
        INSERT INTO sys_message
            (id, tenant_id, notice_id, publish_version, receiver_user_id,
             title, content, message_type, read_status,
             create_time, update_time, status, is_deleted)
        VALUES
            (#{id}, #{tenantId}, #{noticeId}, #{publishVersion}, #{receiverUserId},
             #{title}, #{content}, #{messageType}, #{readStatus},
             #{createTime}, #{updateTime}, #{status}, #{isDeleted})
        """)
    int insertNoticeMessage(SysMessage message);
}
