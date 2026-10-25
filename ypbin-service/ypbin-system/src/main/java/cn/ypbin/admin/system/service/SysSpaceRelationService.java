/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.admin.system.service;

import cn.ypbin.admin.system.entity.SysSpaceRelation;
import cn.ypbin.admin.system.model.query.SpaceRelationQuery;
import cn.ypbin.admin.system.model.req.SpaceBindReq;
import cn.ypbin.admin.system.model.req.SpaceSyncReq;
import cn.ypbin.admin.system.model.resp.SpaceRelationResp;
import cn.ypbin.starter.crud.service.BaseService;
import java.util.List;
import java.util.Map;

/**
 * 空间关联服务（空间 ↔ 任意业务对象）。
 *
 * <p>部门挂空间、人员挂空间、设备挂空间，全部经本服务。业务侧（IoT 等）只依赖本接口契约，
 * 不感知 {@code sys_space_relation} 的表结构。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
public interface SysSpaceRelationService extends BaseService<SysSpaceRelation> {

    /**
     * 把一批业务对象绑定到指定空间。
     *
     * @param spaceId 空间 ID
     * @param req     绑定请求
     * @return 实际新增/更新的关联条数
     */
    int bind(Long spaceId, SpaceBindReq req);

    /**
     * 把一批业务对象从指定空间解绑。
     *
     * @param spaceId   空间 ID
     * @param targetType 关联对象类型
     * @param targetIds  关联对象 ID 列表
     * @return 实际删除的关联条数
     */
    int unbind(Long spaceId, String targetType, List<Long> targetIds);

    /**
     * 按业务对象反查其所在空间。
     *
     * @param query 反查条件
     * @return 关联列表（空间信息已联出）
     */
    List<SpaceRelationResp> listByTarget(SpaceRelationQuery query);

    /**
     * 按业务对象**整体同步**其空间集合（一个事务内完成增删）。
     *
     * <p>与 {@link #bind} 的区别：bind 是「往某个空间加几个对象」，本方法是
     * 「这个对象现在就在这几个空间」——后者是表单保存的自然语义，且避免调用方
     * 自己算差集、发 N 次请求、中途失败留下半改状态。</p>
     *
     * @param req 同步请求（targetType + targetId + 目标空间集合）
     * @return 实际变更的关联条数（新增 + 删除）
     */
    int syncTargetSpaces(SpaceSyncReq req);

    /**
     * 按空间反查其关联的业务对象（运维视角：「这个机房挂了哪些设备」）。
     *
     * @param spaceId    空间 ID
     * @param targetType 关联对象类型（null=不限类型）
     * @return 关联列表
     */
    List<SpaceRelationResp> listBySpace(Long spaceId, String targetType);

    /**
     * 批量反查：一次查多个业务对象各自的空间（列表页展示「所在空间」列用）。
     *
     * <p><b>为什么必须有</b>：列表页一页 10~50 条记录，若逐条调 {@link #listByTarget}，
     * 就是 N 次 HTTP + N 次 SQL（N+1）。本方法一次 `IN` 查询取回全部关联，
     * 再按对象分组返回，查询次数恒为 2（关联 1 次 + 空间信息 1 次）。</p>
     *
     * <p><b>返回 Map 而不是平铺列表</b>：调用方拿到的是「每条记录对应哪些空间」，
     * 分组结构让它可以直接按 targetId 取值，不必自己 groupBy。</p>
     *
     * @param targetType 关联对象类型
     * @param targetIds  关联对象 ID 列表（空集合返回空 Map，不查库）
     * @return 对象 ID → 该对象的空间关联列表（无关联的对象不出现在结果里）
     */
    Map<Long, List<SpaceRelationResp>> listByTargets(String targetType, List<Long> targetIds);

    /**
     * 删除某业务对象的<b>全部</b>空间关联（业务侧删除对象时调用）。
     *
     * <p><b>为什么必须有这个方法</b>：多态关联没有数据库外键，目标对象被删时不会级联。
     * 若业务侧删部门/删设备时不调它，关联行会指向已不存在的对象（脏数据）。
     * 这是多态关联的固有代价，用「业务侧必须显式调用」把它变成可见的约定，
     * 而不是让脏数据静默累积。</p>
     *
     * @param targetType 关联对象类型
     * @param targetId   关联对象 ID
     * @return 实际删除条数
     */
    int removeByTarget(String targetType, Long targetId);
}
