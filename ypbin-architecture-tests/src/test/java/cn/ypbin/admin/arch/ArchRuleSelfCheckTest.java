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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 架构规则「有效性自检」：用合成违规反向验证规则真的会命中。
 *
 * <p>架构测试最大的风险是<b>规则写错却永远通过</b>（ArchUnit 匹配不到目标方法、断言恒真），
 * 这种测试给出虚假安全感，比没有规则更危险。本类把 starter 侧「规则有效性自检」的做法搬到 admin，
 * 并把三条最容易写错的口径固化成断言：</p>
 * <ol>
 *   <li>{@code callMethod(Throwable.class, ...)} 会因调用点 owner 是子类而全部漏判 → 用可赋值谓词；</li>
 *   <li>{@code @TransactionalEventListener} 不是 {@code @Transactional} → 不得判违规；</li>
 *   <li>规则必须在「无违规的类」上通过（避免恒真/恒假两端的假绿）。</li>
 * </ol>
 *
 * <p>注意：本类里的合成违规类位于<b>测试源码</b>，而源码级规则只扫 {@code src/main/java}，
 * 因此它们不会反过来把门禁自己搞红。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class ArchRuleSelfCheckTest {

    /** 谓词：调用 printStackTrace()（owner 多为 Throwable 子类，故按可赋值判定） */
    private static final DescribedPredicate<JavaMethodCall> CALLS_PRINT_STACK_TRACE =
        DescribedPredicate.describe("调用 printStackTrace()", call ->
            call.getName().equals("printStackTrace") && call.getTargetOwner().isAssignableTo(Throwable.class));

    /** 谓词：访问 System.out */
    private static final DescribedPredicate<JavaFieldAccess> ACCESSES_SYSTEM_OUT =
        DescribedPredicate.describe("访问 System.out", access ->
            access.getName().equals("out") && access.getTargetOwner().isAssignableTo(System.class));

    private static JavaClasses syntheticClasses() {
        return new ClassFileImporter().importClasses(
            PrintStackViolation.class, SystemOutViolation.class, FieldInjectionViolation.class,
            TxMissingRollbackFor.class, TxWithRollbackFor.class, TxEventListenerOnly.class,
            TxClassLevelMissingRollbackFor.class, TxClassLevelWithRollbackFor.class,
            TxClassLevelOkButMethodOverrides.class);
    }

    @Test
    @DisplayName("printStackTrace 规则应能捕获违规（owner 为子类情形）")
    void printStackTraceRuleShouldCatchViolation() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .should().callMethodWhere(CALLS_PRINT_STACK_TRACE);
        assertThatThrownBy(() -> rule.check(syntheticClasses()))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("System.out 规则应能捕获违规")
    void systemOutRuleShouldCatchViolation() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .should().accessFieldWhere(ACCESSES_SYSTEM_OUT);
        assertThatThrownBy(() -> rule.check(syntheticClasses()))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("规则在无违规的类上应通过（避免恒真/恒假）")
    void ruleShouldPassOnCleanClasses() {
        ArchRule rule = ArchRuleDefinition.noClasses()
            .should().callMethodWhere(CALLS_PRINT_STACK_TRACE);
        assertThat(rule.evaluate(syntheticClasses()).hasViolation()).isTrue();
        JavaClasses clean = new ClassFileImporter().importClasses(String.class);
        assertThat(rule.evaluate(clean).hasViolation()).isFalse();
    }

    @Test
    @DisplayName("@Transactional 缺 rollbackFor 应命中（方法级/类级）；合规写法与 @TransactionalEventListener 不得命中")
    void transactionalRollbackForDetectionShouldBeAccurate() {
        // 命中：方法级 @Transactional 未声明 rollbackFor（本仓 2026-09-16 主源码实为 0 处）
        assertThat(violationsOf(TxMissingRollbackFor.class)).hasSize(1);
        // 放过：方法级显式声明 rollbackFor
        assertThat(violationsOf(TxWithRollbackFor.class)).isEmpty();
        // 放过：@TransactionalEventListener 是另一个注解（本仓恰有 2 处，不能误判）
        assertThat(violationsOf(TxEventListenerOnly.class)).isEmpty();
        // 命中：**类级** @Transactional 未声明 rollbackFor（会作用于该类全部方法）
        assertThat(violationsOf(TxClassLevelMissingRollbackFor.class)).hasSize(1);
        // 放过：类级显式声明 rollbackFor
        assertThat(violationsOf(TxClassLevelWithRollbackFor.class)).isEmpty();
        // 命中：类级合规，但方法级注解覆盖且自身未声明 rollbackFor（方法级会覆盖类级语义）
        assertThat(violationsOf(TxClassLevelOkButMethodOverrides.class)).hasSize(1);
    }

    private static List<String> violationsOf(Class<?> syntheticClass) {
        return CodingRulesTest.transactionalMembersWithoutRollbackFor(
            new ClassFileImporter().importClasses(syntheticClass));
    }

    @Test
    @DisplayName("字段注入自检：合成违规类必须被字节码层看到")
    void fieldInjectionShouldBeDetectable() {
        JavaClasses imported = new ClassFileImporter().importClasses(FieldInjectionViolation.class);
        long injected = imported.stream()
            .flatMap(clazz -> clazz.getFields().stream())
            .filter(field -> field.isAnnotatedWith(Autowired.class))
            .count();
        assertThat(injected).as("若为 0，说明字段注入规则在字节码层根本匹配不到（规则恒真）").isEqualTo(1);
    }

    /** 合成违规：printStackTrace() */
    static class PrintStackViolation {

        void report(Exception e) {
            e.printStackTrace();
        }
    }

    /** 合成违规：System.out */
    static class SystemOutViolation {

        void report(String message) {
            System.out.println(message);
        }
    }

    /** 合成违规：字段注入 */
    static class FieldInjectionViolation {

        @Autowired
        private String injected;
    }

    /** 合成违规：@Transactional 未声明 rollbackFor */
    static class TxMissingRollbackFor {

        @Transactional
        public void save() {
        }
    }

    /** 合成合规：@Transactional 显式声明 rollbackFor */
    static class TxWithRollbackFor {

        @Transactional(rollbackFor = Exception.class)
        public void save() {
        }
    }

    /** 合成合规：@TransactionalEventListener 不是 @Transactional */
    static class TxEventListenerOnly {

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void onCommitted() {
        }
    }

    /** 合成违规：类级 @Transactional 未声明 rollbackFor */
    @Transactional
    static class TxClassLevelMissingRollbackFor {

        public void save() {
        }
    }

    /** 合成合规：类级 @Transactional 显式声明 rollbackFor */
    @Transactional(rollbackFor = Exception.class)
    static class TxClassLevelWithRollbackFor {

        public void save() {
        }
    }

    /** 合成违规：类级合规，但方法级注解覆盖且未声明 rollbackFor */
    @Transactional(rollbackFor = Exception.class)
    static class TxClassLevelOkButMethodOverrides {

        @Transactional
        public void save() {
        }
    }
}
