/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.entity;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Getter;
import lombok.Setter;

/**
 * 牌账清-意见反馈实体。
 *
 * <p>端侧匿名可提交（网关白名单未放行，但登录用户均可提），因此除内容外记录提交人
 * 便于运营回访。处理状态用 {@code feedbackStatus} 表达，供后台侧推进处理进度。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Getter
@Setter
@TableName("cardtab_feedback")
public class CardtabFeedback extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 提交用户 ID */
    private Long userId;

    /** 反馈内容 */
    private String content;

    /** 联系方式（微信号等，便于回复） */
    private String contact;

    /** 处理状态：PENDING 待处理 PROCESSED 已处理 */
    private String feedbackStatus;
}
