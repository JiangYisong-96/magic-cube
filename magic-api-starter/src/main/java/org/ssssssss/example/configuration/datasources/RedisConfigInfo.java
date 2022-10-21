package org.ssssssss.example.configuration.datasources;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedisConfigInfo {

  private String host;
  private Integer database;
  private Integer port;
  private String password;
  private Long timeout;
}
