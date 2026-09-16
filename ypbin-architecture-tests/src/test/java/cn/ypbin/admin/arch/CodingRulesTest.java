/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * admin 仓「字节码级」架构约束：这些规则在字节码上判定比源码正则更可靠。
 *
 * <p>与 {@link SourceConventionTest} 的分工原则：能看字节码就别扫源码（源码正则会漏判「注解与签名同行」
 * 「通配导入」「泛型/raw 类型」等写法）。反过来，Lombok {@code @Data}（SOURCE 保留）、
 * {@code switch(enum)} 编译出的 {@code ordinal()}、循环边界这三类在字节码上<b>不可表达或必然误报</b>，
 * 才落到源码扫描——这个划分沿用 starter 侧已踩过坑的结论。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class CodingRulesTest {

    /** Spring 事务注解全限定名（精确匹配，避免把 @TransactionalEventListener 误当作 @Transactional） */
    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    /** 字段注入注解全限定名 */
    private static final List<String> FIELD_INJECTION_ANNOTATIONS = List.of(
        "org.springframework.beans.factory.annotation.Autowired",
        "jakarta.annotation.Resource");

    /** 谓词：调用 printStackTrace()（owner 多为 Throwable 子类，故按可赋值判定） */
    private static final DescribedPredicate<JavaMethodCall> CALLS_PRINT_STACK_TRACE =
        DescribedPredicate.describe("调用 printStackTrace()", call ->
            call.getName().equals("printStackTrace") && call.getTargetOwner().isAssignableTo(Throwable.class));

    /** 谓词：访问 System.out */
    private static final DescribedPredicate<JavaFieldAccess> ACCESSES_SYSTEM_OUT =
        DescribedPredicate.describe("访问 System.out", access ->
            access.getName().equals("out") && access.getTargetOwner().isAssignableTo(System.class));

    /** 谓词：访问 System.err */
    private static final DescribedPredicate<JavaFieldAccess> ACCESSES_SYSTEM_ERR =
        DescribedPredicate.describe("访问 System.err", access ->
            access.getName().equals("err") && access.getTargetOwner().isAssignableTo(System.class));

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cn.ypbin.admin");
    }

    @Test
    @DisplayName("禁止 printStackTrace 与直接使用 System.out/System.err")
    void shouldNotPrintStackTraceOrUseSystemStreams() {
        // 用 callMethodWhere 而非 callMethod(Throwable.class, ...)：调用点 owner 通常是子类
        // （如 Exception/IllegalStateException），按精确 owner 匹配会静默漏掉全部违规
        noClasses()
            .should().callMethodWhere(CALLS_PRINT_STACK_TRACE)
            .because("必须走日志框架并传完整堆栈（log.error(\"...\", ex)），printStackTrace 丢失日志上下文")
            .check(classes);

        noClasses()
            .should().accessFieldWhere(ACCESSES_SYSTEM_OUT)
            .orShould().accessFieldWhere(ACCESSES_SYSTEM_ERR)
            .because("标准输出绕过日志框架与统一格式，生产环境不可追踪")
            .check(classes);
    }

    @Test
    @DisplayName("@Transactional 必须显式声明 rollbackFor（受检异常否则漏回滚）")
    void transactionalShouldAlwaysDeclareRollbackFor() {
        List<String> violations = transactionalMethodsWithoutRollbackFor(classes);
        assertThat(violations)
            .as("写操作的 @Transactional 必须带 rollbackFor = Exception.class；"
                + "注意 @TransactionalEventListener 不是 @Transactional，不该被判违规")
            .isEmpty();
    }

    @Test
    @DisplayName("禁止字段注入（@Autowired/@Resource 标注字段），统一构造器注入")
    void shouldNotUseFieldInjection() {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : classes) {
            for (JavaField field : clazz.getFields()) {
                boolean injected = field.getAnnotations().stream()
                    .anyMatch(annotation -> FIELD_INJECTION_ANNOTATIONS.contains(annotation.getRawType().getName()));
                if (injected) {
                    violations.add(clazz.getName() + "#" + field.getName());
                }
            }
        }
        assertThat(violations)
            .as("字段注入让依赖不可变性与可测性变差（本仓统一 @RequiredArgsConstructor + final 字段）")
            .isEmpty();
    }

    /**
     * 判定注解是否**显式**声明了 rollbackFor。
     *
     * <p>⚠️ 这里不能用 {@code getProperties().containsKey("rollbackFor")}：ArchUnit 的
     * {@code getProperties()} <b>会把注解默认值一并填进来</b>——实测未写任何属性的
     * {@code @Transactional} 其 props 里就已有 {@code rollbackFor=[]}（空数组），
     * 于是「containsKey」判定恒为真，规则<b>永远通过（恒真）</b>，是典型的假绿。
     * 正确做法是按<b>取值</b>判：{@code rollbackFor} 的默认值是空数组，显式声明后非空。</p>
     *
     * <p>本方法只认 {@code rollbackFor}（铁律的字面要求）。仅写 {@code rollbackForClassName}
     * 的写法会被判为违规——本仓 2026-09-16 实测 0 处该写法。</p>
     *
     * @param annotation 注解实例
     * @return 显式声明了非空 rollbackFor 时返回 true
     */
    static boolean declaresRollbackFor(JavaAnnotation<?> annotation) {
        Object value = annotation.get("rollbackFor").orElse(null);
        if (value instanceof Object[] declared) {
            // JavaClass[] 也是 Object[]，长度即「显式声明了几个回滚异常类型」
            return declared.length > 0;
        }
        return value != null;
    }

    static List<String> transactionalMethodsWithoutRollbackFor(JavaClasses classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClass clazz : classes) {
            for (JavaMethod method : clazz.getMethods()) {
                List<JavaAnnotation<JavaMethod>> annotations = method.getAnnotations().stream()
                    .filter(annotation -> annotation.getRawType().getName().equals(TRANSACTIONAL))
                    .toList();
                if (annotations.isEmpty()) {
                    continue;
                }
                boolean declared = annotations.stream().anyMatch(CodingRulesTest::declaresRollbackFor);
                if (!declared) {
                    violations.add(clazz.getName() + "#" + method.getName());
                }
            }
        }
        return violations;
    }
}
