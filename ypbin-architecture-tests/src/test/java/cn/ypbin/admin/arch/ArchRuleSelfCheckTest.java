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

import cn.ypbin.admin.arch.synthetic.provider.ServiceImplDependentAdapter;
import cn.ypbin.admin.arch.synthetic.service.impl.ProviderDependencyViolation;
import cn.ypbin.admin.arch.synthetic.service.impl.SupportOnlyDependency;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
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
 * <p><strong>夹具必须自包含</strong>（2026-09-18 真实故障的教训）：合成夹具只允许依赖本模块测试源码里
 * 自造的玩具类（{@code synthetic/provider} 与 {@code synthetic/service/impl}），<b>不得 import 任何
 * 跨模块真实类</b>。曾经直接 import {@code cn.ypbin.admin.system.provider.AdminDataScopeHandler}，
 * 而 {@code ypbin-system} 的主构件是 {@code BOOT-INF/classes/...} 布局的 fat jar（裸 {@code -pl} 时还会命中
 * {@code ~/.m2} 旧 jar），于是夹具的 import 会让 {@code testCompile} 报 {@code package ... does not exist}，
 * <b>整仓构建失败、部署中断</b>。夹具的职责只是触发规则，不该成为门禁自己的脆弱点——
 * {@link #syntheticFixturesMustBeSelfContained()} 把这条固定成构建失败。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class ArchRuleSelfCheckTest {

    /** 合成夹具唯一允许 import 的 {@code cn.ypbin.admin} 前缀（跨模块真实类一律禁止，见自包含自检） */
    private static final String CROSS_MODULE_IMPORT_ALLOWED = "import cn.ypbin.admin.arch.synthetic.";

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

    @Test
    @DisplayName("service/impl → provider 规则自检：合成违规必须转红，无关依赖与反向依赖必须放过")
    void serviceImplToProviderRuleShouldBeAccurate() {
        ArchRule rule = CodingRulesTest.serviceImplShouldNotDependOnProvider();

        // ① 命中：service/impl 持有 provider 玩具类 SomeProvider（复刻 2026-09-17 那次的真实回归形态）
        JavaClasses violators = new ClassFileImporter().importClasses(ProviderDependencyViolation.class);
        assertThatThrownBy(() -> rule.check(violators))
            .as("规则必须能抓住「实现层直连宿主端口适配实现」——抓不住说明包谓词写错，规则是恒真的假门禁")
            .isInstanceOf(AssertionError.class);

        // ② 放过：service/impl 只依赖 JDK 类型（规则不能把正常依赖判违规，否则是恒假的假门禁）
        JavaClasses jdkOnly = new ClassFileImporter().importClasses(SupportOnlyDependency.class);
        assertThat(rule.evaluate(jdkOnly).hasViolation())
            .as("只依赖 java.util.List 的 service/impl 不得被判违规")
            .isFalse();

        // ③ 放过：反向依赖 provider → service/impl 不在本条约束内（把两侧都导入，规则仍须放过）
        JavaClasses reverseOnly = new ClassFileImporter()
            .importClasses(ServiceImplDependentAdapter.class, SupportOnlyDependency.class);
        assertThat(rule.evaluate(reverseOnly).hasViolation())
            .as("规则方向性必须正确：provider 里的适配器依赖 service/impl 是反向依赖，不得被本条拦截")
            .isFalse();
    }

    @Test
    @DisplayName("合成夹具必须自包含：不得 import 本模块之外的 cn.ypbin.admin 真实类")
    void syntheticFixturesMustBeSelfContained() throws IOException {
        Path synthetic = SourceScan.repoRoot()
            .resolve("ypbin-architecture-tests/src/test/java/cn/ypbin/admin/arch/synthetic");
        assertThat(Files.isDirectory(synthetic)).as("夹具目录不存在，本自检会空转").isTrue();

        List<String> fixtureSources = new ArrayList<>();
        List<String> crossModuleImports = new ArrayList<>();
        try (Stream<Path> files = Files.walk(synthetic)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                fixtureSources.add(SourceScan.relative(file));
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.startsWith("import cn.ypbin.admin.") && !line.startsWith(CROSS_MODULE_IMPORT_ALLOWED)) {
                        crossModuleImports.add(SourceScan.relative(file) + " → " + line.trim());
                    }
                }
            }
        }
        assertThat(fixtureSources).as("夹具源码一个都没扫到，本自检是空转").isNotEmpty();
        assertThat(crossModuleImports)
            .as("夹具一旦 import 跨模块真实类，构建路径差异（反应堆里 ypbin-system 被 repackage 成 BOOT-INF 布局的"
                + " fat jar、裸 -pl 时命中 ~/.m2 旧 jar）就会让 testCompile 直接失败——2026-09-18 部署即因此中断。"
                + "夹具只应依赖本模块测试源码里自造的玩具类")
            .isEmpty();
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
