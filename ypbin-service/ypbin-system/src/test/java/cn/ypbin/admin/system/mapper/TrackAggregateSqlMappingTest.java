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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.admin.system.entity.SysRoleDept;
import cn.ypbin.admin.system.entity.SysRoleMenu;
import cn.ypbin.admin.system.entity.SysTemplateMenu;
import cn.ypbin.admin.system.entity.SysTrackEventDaily;
import cn.ypbin.admin.system.entity.SysUser;
import cn.ypbin.admin.system.entity.SysUserPost;
import cn.ypbin.admin.system.entity.SysUserRole;
import cn.ypbin.admin.system.entity.SysTrackSession;
import cn.ypbin.admin.system.entity.SysTrackUserDaily;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

/**
 * 聚合表 SQL 与实体/DTO 字段的<strong>静态核对</strong>（不依赖数据库）。
 *
 * <p>本仓测试环境没有数据库，聚合 SQL 无法真跑。能做的最强检查是：把 SQL 里出现的每一个列名/别名
 * 取出来，逐个核对目标类型上确实存在同名（下划线转驼峰）的字段——列名写错、字段改名、
 * DTO 与 SQL 漂移都会在构建期直接失败，而不是等上线后静默返回一片 null。</p>
 *
 * <p>另外顺带验证 MyBatis-Plus 能接受<strong>没有代理主键</strong>的实体
 * （两张按天聚合表用复合主键，没有 {@code @TableId}）：若框架在这里就不认，服务启动即失败。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class TrackAggregateSqlMappingTest {

    /** SELECT 列表里的别名：{@code expr AS alias} */
    private static final Pattern ALIAS = Pattern.compile("\\bAS\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?");

    /** INSERT 的表名与列清单 */
    private static final Pattern INSERT_COLUMNS =
        Pattern.compile("INSERT\\s+INTO\\s+`?(\\w+)`?\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    /** 复合主键表：列名必须由框架的列映射核对，而不是直接猜驼峰 */
    private static final List<Class<?>> COMPOSITE_KEY_ENTITIES = List.of(SysTrackEventDaily.class,
        SysTrackUserDaily.class);

    private static final MybatisConfiguration CONFIGURATION = new MybatisConfiguration();

    @Test
    void shouldInitializeTableInfoWithoutSurrogatePrimaryKey() {
        TableInfo eventDaily = tableInfo(SysTrackEventDaily.class);
        assertThat(eventDaily.getTableName()).isEqualTo("sys_track_event_daily");
        // 复合主键表没有代理主键：MyBatis-Plus 必须能接受（否则服务启动就失败）
        assertThat(eventDaily.getKeyProperty()).isNull();
        assertThat(eventDaily.getKeyColumn()).isNull();
        assertThat(columns(eventDaily)).containsExactlyInAnyOrder("stat_date", "event_code", "app_id",
            "event_count", "fail_count", "duration_sum_ms", "duration_cnt", "create_time");

        TableInfo userDaily = tableInfo(SysTrackUserDaily.class);
        assertThat(userDaily.getTableName()).isEqualTo("sys_track_user_daily");
        assertThat(userDaily.getKeyProperty()).isNull();
        assertThat(userDaily.getKeyColumn()).isNull();
        assertThat(columns(userDaily)).containsExactlyInAnyOrder("stat_date", "user_id", "app_id",
            "event_count", "first_time", "last_time", "create_time");

        TableInfo session = tableInfo(SysTrackSession.class);
        assertThat(session.getTableName()).isEqualTo("sys_track_session");
        assertThat(session.getKeyProperty()).isEqualTo("sessionId");
        assertThat(session.getKeyColumn()).isEqualTo("session_id");
        assertThat(columns(session)).containsExactlyInAnyOrder("session_id", "user_id", "tenant_id", "app_id",
            "start_time", "end_time", "duration_ms", "event_count", "event_sequence", "truncated",
            "create_time");
    }

    @Test
    void shouldMapEveryInsertColumnToAnEntityField() {
        assertInsertColumns(SysTrackEventDailyMapper.class, SysTrackEventDaily.class);
        assertInsertColumns(SysTrackUserDailyMapper.class, SysTrackUserDaily.class);
        assertInsertColumns(SysTrackSessionMapper.class, SysTrackSession.class);
        // 由「禁循环内 DB」门禁暴露出的逐行写入改为批量后新增的 insertBatch：列清单必须与实体字段对得上
        assertInsertColumns(SysUserMapper.class, SysUser.class);
        assertInsertColumns(SysUserRoleMapper.class, SysUserRole.class);
        assertInsertColumns(SysUserPostMapper.class, SysUserPost.class);
        assertInsertColumns(SysRoleMenuMapper.class, SysRoleMenu.class);
        assertInsertColumns(SysRoleDeptMapper.class, SysRoleDept.class);
        assertInsertColumns(SysTemplateMenuMapper.class, SysTemplateMenu.class);
    }

    @Test
    void shouldMapEverySelectColumnToATargetTypeField() {
        assertSelectColumns(SysTrackEventMapper.class);
        assertSelectColumns(SysTrackUserDailyMapper.class);
        assertSelectColumns(SysTrackSessionMapper.class);
    }

    private static void assertInsertColumns(Class<?> mapperType, Class<?> entityType) {
        List<String> checked = new ArrayList<>();
        for (Method method : mapperType.getDeclaredMethods()) {
            Insert insert = method.getAnnotation(Insert.class);
            if (insert == null) {
                continue;
            }
            Matcher matcher = INSERT_COLUMNS.matcher(String.join(" ", insert.value()));
            while (matcher.find()) {
                assertThat(matcher.group(1)).as("INSERT 目标表").isEqualTo(tableInfo(entityType).getTableName());
                for (String column : matcher.group(2).split(",")) {
                    String name = column.trim();
                    assertThat(columns(tableInfo(entityType)))
                        .as("%s.%s 的列「%s」不在 %s 的字段映射里",
                            mapperType.getSimpleName(), method.getName(), name, entityType.getSimpleName())
                        .contains(name);
                    checked.add(name);
                }
            }
        }
        assertThat(checked).as("必须真的解析到列清单，否则本检查是空转").isNotEmpty();
    }

    private static void assertSelectColumns(Class<?> mapperType) {
        List<String> checked = new ArrayList<>();
        for (Method method : mapperType.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            Class<?> targetType = select == null ? null : selectTargetType(method);
            if (targetType == null || isScalar(targetType)) {
                continue;
            }
            for (String item : selectListItems(String.join(" ", select.value()))) {
                String name = aliasOf(item);
                assertThat(hasField(targetType, name))
                    .as("%s.%s 的查询列「%s」在 %s 上没有对应字段（下划线转驼峰）",
                        mapperType.getSimpleName(), method.getName(), name, targetType.getSimpleName())
                    .isTrue();
                checked.add(name);
            }
        }
        assertThat(checked).as("必须真的解析到查询列，否则本检查是空转").isNotEmpty();
    }

    /** 取 SELECT 与第一个顶层 FROM 之间的列清单，并按顶层逗号切分。 */
    private static List<String> selectListItems(String sql) {
        int selectIndex = sql.toUpperCase(Locale.ROOT).indexOf("SELECT");
        if (selectIndex < 0) {
            return List.of();
        }
        int depth = 0;
        int fromIndex = -1;
        for (int index = selectIndex + "SELECT".length(); index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')') {
                depth--;
            } else if (depth == 0 && sql.regionMatches(true, index, "FROM", 0, 4)) {
                fromIndex = index;
                break;
            }
        }
        assertThat(fromIndex).as("SQL 里没有找到顶层 FROM：%s", sql).isGreaterThan(0);
        String list = sql.substring(selectIndex + "SELECT".length(), fromIndex).trim();
        if (list.toUpperCase(Locale.ROOT).startsWith("DISTINCT")) {
            list = list.substring("DISTINCT".length()).trim();
        }
        List<String> items = new ArrayList<>();
        depth = 0;
        StringBuilder current = new StringBuilder();
        for (char character : list.toCharArray()) {
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            }
            if (character == ',' && depth == 0) {
                items.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        items.add(current.toString().trim());
        return items;
    }

    /** 取列名：有 AS 就用别名，否则只有简单标识符才当成列名（表达式跳过）。 */
    private static String aliasOf(String item) {
        Matcher matcher = ALIAS.matcher(item);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String trimmed = item.trim();
        return trimmed.matches("[a-zA-Z_][a-zA-Z0-9_]*") ? trimmed : "";
    }

    /** 解析 {@code List<T>} 的 T；无法解析时返回 null。 */
    private static Class<?> selectTargetType(Method method) {
        if (!(method.getGenericReturnType() instanceof ParameterizedType parameterized)) {
            return null;
        }
        if (!(parameterized.getActualTypeArguments()[0] instanceof Class<?> target)) {
            return null;
        }
        return target;
    }

    private static boolean isScalar(Class<?> type) {
        return type.isPrimitive() || type.getName().startsWith("java.");
    }

    private static TableInfo tableInfo(Class<?> entityType) {
        return TableInfoHelper.initTableInfo(new MapperBuilderAssistant(CONFIGURATION, "track-aggregate"),
            entityType);
    }

    private static List<String> columns(TableInfo tableInfo) {
        List<String> columns = new ArrayList<>();
        columns.add(tableInfo.getKeyColumn());
        for (TableFieldInfo field : tableInfo.getFieldList()) {
            columns.add(field.getColumn());
        }
        columns.removeIf(Objects::isNull);
        return columns;
    }

    /**
     * 目标类型上是否存在该列对应的字段。
     *
     * <p>复合主键实体走框架的列映射（顺带证明 {@code @TableName} 与列名对齐）；
     * DTO 与其它实体按「下划线转驼峰」核对字段名。</p>
     */
    private static boolean hasField(Class<?> type, String column) {
        if (column.isEmpty()) {
            return true;
        }
        if (COMPOSITE_KEY_ENTITIES.contains(type) || type.equals(SysTrackSession.class)) {
            return columns(tableInfo(type)).contains(column);
        }
        return Arrays.stream(type.getDeclaredFields())
            .map(Field::getName)
            .anyMatch(toCamelCase(column)::equals);
    }

    private static String toCamelCase(String column) {
        StringBuilder builder = new StringBuilder(column.length());
        boolean upperNext = false;
        for (char character : column.toCharArray()) {
            if (character == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(character) : character);
            upperNext = false;
        }
        return builder.toString();
    }
}
