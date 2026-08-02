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

import cn.ypbin.admin.system.entity.SysTemplateMenu;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 权限模板-菜单关联 Mapper。
 *
 * @author wenbin
 * @since 2026-08-02
 */
public interface SysTemplateMenuMapper extends BaseMapper<SysTemplateMenu> {

    /**
     * 查询模板授权的菜单 ID 集合。
     *
     * @param templateId 模板 ID
     * @return 菜单 ID 列表
     */
    @Select("SELECT menu_id FROM sys_template_menu WHERE template_id = #{templateId}")
    List<Long> selectMenuIdsByTemplateId(@Param("templateId") Long templateId);
}
