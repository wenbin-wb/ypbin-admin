/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.admin.system.entity.SysMessage;
import cn.ypbin.admin.system.mapper.SysMessageMapper;
import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link SysMessageServiceImpl} 影响行数测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class SysMessageServiceImplTest {

    private SysMessageMapper messageMapper;
    private SysMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysMessage.class);
        messageMapper = mock(SysMessageMapper.class);
        service = new SysMessageServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", messageMapper);
    }

    @Test
    void markReadRejectsMissingOrForeignMessage() {
        when(messageMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.markRead(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("消息不存在或无权操作");
    }

    @Test
    void recentRejectsOutOfRangeLimit() {
        assertThatThrownBy(() -> service.recent(1L, 0))
            .isInstanceOf(BusinessException.class)
            .hasMessage("最近消息数量必须在 1 到 100 之间");
    }

    @Test
    void deleteRejectsMissingOrForeignMessage() {
        when(messageMapper.delete(any())).thenReturn(0);

        assertThatThrownBy(() -> service.delete(1L, 2L))
            .isInstanceOf(BusinessException.class)
            .hasMessage("消息不存在或无权操作");
    }
}
