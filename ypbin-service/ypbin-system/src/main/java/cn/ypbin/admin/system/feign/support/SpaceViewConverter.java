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
package cn.ypbin.admin.system.feign.support;

import cn.ypbin.admin.system.entity.SysSpace;
import cn.ypbin.admin.system.model.dto.SysSpaceDto;
import cn.ypbin.admin.system.model.dto.SysSpaceRelationDto;
import cn.ypbin.admin.system.model.resp.SpaceRelationResp;
import cn.ypbin.admin.system.model.resp.SpaceResp;
/**
 * 空间实体/响应 → 跨服务 DTO 的投影。
 *
 * <p><b>为什么要有这一层（而不是让 {@code SystemClientImpl} 直接 new DTO）</b>：
 * 跨服务契约只暴露 {@link SysSpaceDto} 这类只读视图，而实体与页面响应的字段会随业务演进
 * （如 {@code extJson} 是业务自定义字段、{@code childCount} 是列表页统计）。
 * 若让 Feign 实现直接拼 DTO，每加一个字段都要在「契约」与「实现」两处改；
 * 收在一处投影里，契约的稳定性由本类负责。</p>
 *
 * <p><b>为什么没收进 {@code system-api} 模块</b>：本类要引用 {@link SysSpace}（持久化实体），
 * 而契约模块禁止引用实体包（架构门禁 {@code SourceConventionTest} 强制）。故必须留在
 * service 模块——与既有 {@code UserViewConverter} 同一处置。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
public final class SpaceViewConverter {

    private SpaceViewConverter() {
    }

    /**
     * 空间实体/响应 → 跨服务 DTO。
     *
     * @param source 空间响应（含后端算好的路径与类型名）
     * @return 只读视图
     */
    public static SysSpaceDto toDto(SpaceResp source) {
        if (source == null) {
            return null;
        }
        SysSpaceDto dto = new SysSpaceDto();
        dto.setId(source.getId());
        dto.setPid(source.getPid());
        dto.setName(source.getName());
        dto.setCode(source.getCode());
        dto.setSpaceType(source.getSpaceType());
        dto.setLevel(source.getLevel());
        dto.setSpacePath(source.getSpacePath());
        dto.setStatus(source.getStatus());
        return dto;
    }

    /**
     * 空间实体 → 跨服务 DTO（不经过响应组装，供「按 ID 查单个」这类轻路径用）。
     *
     * <p>{@code spacePath} 留空：调用方若需要路径，应走返回 {@link SpaceResp} 的服务方法
     * （那里已算好），而不是让本方法再去查一次父链——那会把一次查询变成 N 次。</p>
     *
     * @param source 空间实体
     * @return 只读视图
     */
    public static SysSpaceDto toDto(SysSpace source) {
        if (source == null) {
            return null;
        }
        SysSpaceDto dto = new SysSpaceDto();
        dto.setId(source.getId());
        dto.setPid(source.getPid());
        dto.setName(source.getName());
        dto.setCode(source.getCode());
        dto.setSpaceType(source.getSpaceType());
        dto.setLevel(source.getLevel());
        dto.setStatus(source.getStatus());
        return dto;
    }

    /**
     * 空间关联响应 → 跨服务 DTO。
     *
     * @param source 关联响应
     * @return 只读视图
     */
    public static SysSpaceRelationDto toDto(SpaceRelationResp source) {
        if (source == null) {
            return null;
        }
        SysSpaceRelationDto dto = new SysSpaceRelationDto();
        dto.setId(source.getId());
        dto.setSpaceId(source.getSpaceId());
        dto.setSpaceName(source.getSpaceName());
        dto.setSpaceCode(source.getSpaceCode());
        dto.setSpaceType(source.getSpaceType());
        dto.setSpacePath(source.getSpacePath());
        dto.setTargetType(source.getTargetType());
        dto.setTargetId(source.getTargetId());
        dto.setIsPrimary(source.getIsPrimary());
        return dto;
    }
}
