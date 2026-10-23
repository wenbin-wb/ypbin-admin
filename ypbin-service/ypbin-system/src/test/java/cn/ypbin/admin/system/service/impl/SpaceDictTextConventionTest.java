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
package cn.ypbin.admin.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.admin.system.model.resp.SpaceRelationResp;
import cn.ypbin.admin.system.model.resp.SpaceResp;
import cn.ypbin.admin.system.model.dto.SysSpaceDto;
import cn.ypbin.admin.system.model.dto.SysSpaceRelationDto;
import cn.ypbin.starter.json.dict.DictText;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 门禁：空间域出参的「字典值 → 展示文本」必须走 starter 的 {@link DictText} 注解。
 *
 * <p><b>为什么需要这条门禁（2026-10-23 审核发现）</b>：本仓既有的字典翻译口径是
 * {@code @DictText}（见 {@code UserResp.gender/status}）——后端在序列化时<b>额外输出</b>
 * {@code 字段名+Text}，前端直接渲染。我在空间实现里一开始写的是「服务层手工查字典 →
 * set 一个 {@code spaceTypeName}」，这与 starter 形成了<b>两份翻译实现</b>：</p>
 * <ul>
 *   <li>字典项改名后，{@code @DictText} 自动跟随，手工翻译的那份需要服务重启或重新查库；</li>
 *   <li>手工翻译要自己处理「字典缺失/缓存未就绪」，而 starter 已处理；</li>
 *   <li>字段命名分叉：{@code spaceTypeName} vs 惯例的 {@code spaceTypeText}，
 *       前端要在两套字段名之间做兼容判断。</li>
 * </ul>
 *
 * <p>故本门禁钉住三件事：① {@code spaceType} 字段带 {@code @DictText("space_type")}；
 * ② 不再有手工翻译的 {@code spaceTypeName} 字段；③ 注解的字典类型值必须是 {@code space_type}
 * （写错字典编码不会报错，只会静默输出原值）。</p>
 *
 * @author wenbin
 * @since 2026-10-23
 */
class SpaceDictTextConventionTest {

    /** 空间域全部出参类型（页面响应 + 跨服务 DTO）。 */
    private static final Class<?>[] SPACE_OUTPUT_TYPES = {
        SpaceResp.class,
        SpaceRelationResp.class,
        SysSpaceDto.class,
        SysSpaceRelationDto.class,
    };

    @Test
    @DisplayName("★ 空间域出参的 spaceType 必须用 @DictText(\"space_type\") 翻译")
    void spaceTypeMustUseDictText() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : SPACE_OUTPUT_TYPES) {
            Field field = findField(type, "spaceType");
            if (field == null) {
                violations.add(type.getSimpleName() + " → 缺少 spaceType 字段（类型契约已变，请同步本门禁）");
                continue;
            }
            DictText dictText = field.getAnnotation(DictText.class);
            if (dictText == null) {
                violations.add(type.getSimpleName() + "#spaceType → 缺少 @DictText");
                continue;
            }
            if (!"space_type".equals(dictText.value())) {
                violations.add(type.getSimpleName() + "#spaceType → @DictText 的字典编码是 "
                    + dictText.value() + "，应为 space_type");
            }
        }
        assertThat(violations)
            .as("字典翻译必须走 starter 的 @DictText（与 UserResp.gender/status 同一套口径）；"
                + "手工在服务层查字典翻译会形成第二份实现，字典项改名后一处生效一处不生效")
            .isEmpty();
    }

    @Test
    @DisplayName("★ 不得保留手工翻译的 spaceTypeName 字段（字段名分叉会让前端写两套兼容逻辑）")
    void mustNotHaveManualTranslatedField() {
        List<String> violations = new ArrayList<>();
        for (Class<?> type : SPACE_OUTPUT_TYPES) {
            if (findField(type, "spaceTypeName") != null) {
                violations.add(type.getSimpleName() + "#spaceTypeName");
            }
        }
        assertThat(violations)
            .as("这些类仍有手工翻译字段 spaceTypeName：应按 @DictText 惯例输出 spaceTypeText")
            .isEmpty();
    }

    @Test
    @DisplayName("反向有效性自检：构造缺少注解的合成字段，判定逻辑必须能命中（防规则恒真）")
    void ruleMustDetectViolation() throws NoSuchFieldException {
        // 合成一个没有 @DictText 的字段，验证「注解缺失」判据不是恒真
        Field plain = SyntheticWithoutDictText.class.getDeclaredField("spaceType");
        assertThat(plain.getAnnotation(DictText.class))
            .as("判据失效：无注解字段被判成有注解").isNull();
        // 合成一个有注解的字段，验证正常路径能识别
        Field annotated = SyntheticWithDictText.class.getDeclaredField("spaceType");
        assertThat(annotated.getAnnotation(DictText.class)).isNotNull();
        assertThat(annotated.getAnnotation(DictText.class).value()).isEqualTo("space_type");
    }

    @Test
    @DisplayName("扫描面自检：被测类型确实存在于类路径上（否则本门禁在守空集合）")
    void monitoredTypesMustBeResolvable() {
        assertThat(SPACE_OUTPUT_TYPES).hasSize(4);
        for (Class<?> type : SPACE_OUTPUT_TYPES) {
            assertThat(findField(type, "spaceType"))
                .as("%s 解析不到 spaceType 字段——门禁空转", type.getSimpleName())
                .isNotNull();
        }
    }

    // ---------- 辅助 ----------

    /**
     * 沿父类查找字段（出参可能继承基类）。
     *
     * @param type      类型
     * @param fieldName 字段名
     * @return 字段；不存在返回 {@code null}
     */
    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /** 探针：无注解（用于验证判据能识别缺失）。 */
    private static class SyntheticWithoutDictText {
        @SuppressWarnings("unused")
        private String spaceType;
    }

    /** 探针：有注解（用于验证判据能识别存在）。 */
    private static class SyntheticWithDictText {
        @DictText("space_type")
        @SuppressWarnings("unused")
        private String spaceType;
    }
}
