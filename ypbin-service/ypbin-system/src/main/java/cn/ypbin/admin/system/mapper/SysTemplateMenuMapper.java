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
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
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

    /**
     * 批量插入权限模板-菜单关联行（单条多值 INSERT）。
     *
     * <p>复合主键表没有代理主键、也没有审计列，故不涉及主键回填；权限模板-菜单关联的规模由一次请求的入参决定，
     * 逐条 insert 会产生同等数量的单行往返（N+1 写）。</p>
     *
     * @param rows 待插入行（调用方保证非空）
     * @return 影响行数
     */
    @Insert("<script>"
        + "INSERT INTO sys_template_menu (template_id, menu_id) VALUES "
        + "<foreach collection='rows' item='r' separator=','>(#{r.templateId}, #{r.menuId})</foreach>"
        + "</script>")
    int insertBatch(@Param("rows") Collection<SysTemplateMenu> rows);

}
