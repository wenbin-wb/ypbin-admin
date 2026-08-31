/*
 * Copyright (c) 2026-present ypbin-admin authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package cn.ypbin.admin.system.model.req;

import cn.ypbin.starter.data.core.EntityStatus;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/**
 * 请求契约校验测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class ContractValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void statusAcceptsOnlyZeroOrOne() {
        StatusReq valid = new StatusReq();
        valid.setStatus(EntityStatus.ENABLED.getCode());
        StatusReq invalid = new StatusReq();
        invalid.setStatus(2);

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(invalid)).isNotEmpty();
    }

    @Test
    void jobRequiresExactlyOneTrigger() {
        JobSaveReq missing = request();
        JobSaveReq cron = request();
        cron.setCron("0 * * * * *");
        JobSaveReq fixedRate = request();
        fixedRate.setFixedRateSeconds(30L);
        JobSaveReq multiple = request();
        multiple.setCron("0 * * * * *");
        multiple.setFixedRateSeconds(30L);

        assertThat(validator.validate(missing)).isNotEmpty();
        assertThat(validator.validate(cron)).isEmpty();
        assertThat(validator.validate(fixedRate)).isEmpty();
        assertThat(validator.validate(multiple)).isNotEmpty();
    }

    private JobSaveReq request() {
        JobSaveReq req = new JobSaveReq();
        req.setName("job");
        req.setExecutor("demo");
        req.setConcurrentGuard(1);
        return req;
    }
}
