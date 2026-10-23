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
package cn.ypbin.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.json.dict.DictItem;
import cn.ypbin.starter.json.dict.DictUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供<b>租户用户</b>读取字典选项的只读端点（下拉渲染用）。
 *
 * <p><b>为什么需要它（实测依据，2026-10-23）</b>：既有 {@code SysDictItemController} 带<b>类级</b>
 * {@code @PlatformAccess}，非平台用户一律 403。而字典维护页（{@code sys_menu.platform_only=1}，
 * 见 {@code 002-data.sql:293}）本就只给平台用户——所以那个控制器整体限平台是<b>与本仓惯例一致的</b>，
 * 不是缺陷。问题在于：<b>租户可见</b>的页面（如空间管理，{@code platform_only=0}）也需要字典选项
 * 来渲染下拉，而目前<b>没有任何租户可用的字典读取端点</b>。</p>
 *
 * <p><b>为什么不改既有控制器把类级注解下移</b>：那是放开一个既有平台接口的访问面
 * （属权限类改动、影响面超出「空间管理」这一功能），且会与 {@code platform_only=1} 的菜单定位
 * 产生口径分歧。按本仓「新功能放新文件」的纪律，新增一个<b>权限最小</b>的只读端点更合适：
 * 仅要求登录、只能读、不能维护。</p>
 *
 * <p><b>安全边界</b>：字典项是<b>全局共享配置</b>（不按租户隔离，见 {@code ypbin.tenant.ignore-tables}
 * 的 {@code sys_dict}/{@code sys_dict_item}），内容只有 label/value/color/sort，<b>不含密钥与个人数据</b>。
 * 因此「已登录即可读」等价于前端渲染下拉所需的公共元数据，不构成越权。</p>
 *
 * <p><b>与 {@code @DictText} 的分工</b>：出参里的「值 → 展示文本」翻译走 starter 的
 * {@code @DictText}（后端直接输出 {@code xxxText}，见 {@code SpaceResp.spaceType}），
 * <b>不需要</b>本端点；本端点只用于「渲染可选值列表」（下拉）。两者不可互相替代：
 * 前者不知道有哪些可选值，后者不知道当前记录的值。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
@RestController
@RequestMapping("/dict-item-options")
@RequiredArgsConstructor
public class DictOptionController {

    /**
     * 按字典编码查询字典项（下拉渲染用，走 starter 的字典缓存）。
     *
     * @param dictCode 字典编码（如 space_type）
     * @return 字典项列表
     */
    @GetMapping("/{dictCode}")
    @SaCheckLogin
    public R<List<DictItem>> options(@PathVariable String dictCode) {
        return R.ok(DictUtils.getItems(dictCode));
    }
}
