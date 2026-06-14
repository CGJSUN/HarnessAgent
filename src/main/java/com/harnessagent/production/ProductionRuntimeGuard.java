package com.harnessagent.production;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ProductionRuntimeGuard implements ApplicationRunner {

    private final ProductionRuntimeValidator validator;

    public ProductionRuntimeGuard(ProductionRuntimeValidator validator) {
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments args) {
        validator.validate();
    }
}
