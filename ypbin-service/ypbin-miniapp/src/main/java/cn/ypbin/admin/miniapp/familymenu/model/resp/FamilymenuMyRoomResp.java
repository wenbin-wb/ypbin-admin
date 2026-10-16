/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.miniapp.familymenu.model.resp;

import lombok.Getter;
import lombok.Setter;

/**
 * 「我的餐厅」出参：餐厅信息 + 我在该餐厅的成员记录。
 *
 * <p><b>为什么是包装结构而不是扁平字段</b>：端侧进入首页需要一次拿全「哪个餐厅」与「我是什么角色」，
 * 二者是两类实体（餐厅 / 成员）。摊平成同一层会出现 {@code id}/{@code nickname} 归属歧义
 * （到底是餐厅的 id 还是成员的 id），且成员视角的字段（role/memberStatus）混进餐厅契约。
 * 故按实体边界包装，字段名与各自实体一致（铁律：字段全程同名，不做改名映射）。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@Getter
@Setter
public class FamilymenuMyRoomResp {

    /** 餐厅（无餐厅时为 null） */
    private FamilymenuRoomDetailResp room;

    /** 我在该餐厅的成员记录（非成员时为 null） */
    private FamilymenuMemberResp member;
}
