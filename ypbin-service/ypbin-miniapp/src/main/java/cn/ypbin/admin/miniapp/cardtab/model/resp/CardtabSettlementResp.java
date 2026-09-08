/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.cardtab.model.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 房间结算出参。
 *
 * @author wenbin
 * @since 2026-09-08
 */
@Getter
@Setter
public class CardtabSettlementResp {

    /** 房间 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roomId;

    /** 房间状态 */
    private String status;

    /** 最终排名 */
    private List<RankingItem> rankings;

    /** 结算转账方案（谁给谁多少） */
    private List<TransferItem> transfers;

    /** 分享文案 */
    private String shareText;

    @Getter
    @Setter
    public static class RankingItem {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long memberId;
        private String nickname;
        private BigDecimal totalScore;
        private Integer rankNo;
    }

    @Getter
    @Setter
    public static class TransferItem {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long fromMemberId;
        private String fromName;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long toMemberId;
        private String toName;
        private BigDecimal amount;
    }
}
