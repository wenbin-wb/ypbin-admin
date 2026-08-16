/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 知识库文档。
 *
 * <p>不继承 BaseEntity：无审计字段；逻辑删除列 is_deleted 通过 {@link TableLogic} 接管，
 * status 列区分向量化处理状态。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@Getter
@Setter
@TableName("ai_document")
public class AiDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 所属知识库 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    /** 文件名 */
    private String filename;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 切片数量 */
    private Integer chunkCount;

    /** 状态：0 处理中 1 就绪 2 失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 上传人 */
    @TableField(value = "create_user", fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUser;

    /** 上传时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0 正常 1 已删除 */
    @TableLogic
    private Integer isDeleted;
}
