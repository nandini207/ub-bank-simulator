package com.billdesk.simulator.config;

import com.billdesk.simulator.model.SimulatorSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulatorConfig {

    @Value("${simulator.encryption.key}")
    private String encryptionKey;

    @Value("${simulator.checksum.key}")
    private String checksumKey;

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public String getChecksumKey() {
        return checksumKey;
    }

    @Bean
    public SimulatorSettings simulatorSettings() {
        return new SimulatorSettings();
    }
}
