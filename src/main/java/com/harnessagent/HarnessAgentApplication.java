package com.harnessagent;

import com.harnessagent.config.HarnessAgentProperties;
import com.harnessagent.production.ProductionRuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({HarnessAgentProperties.class, ProductionRuntimeProperties.class})
public class HarnessAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarnessAgentApplication.class, args);
    }
}
