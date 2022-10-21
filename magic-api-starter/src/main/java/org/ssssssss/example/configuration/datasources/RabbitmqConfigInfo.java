package org.ssssssss.example.configuration.datasources;

import java.io.Serializable;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConfigurationProperties(prefix = "spring.rabbitmq")
@Data
@Primary
public class RabbitmqConfigInfo {

  @NestedConfigurationProperty
  private List<RabbitServer> rabbitServer;

  @Data
  public static class RabbitServer implements Serializable {

    private String id;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String virtualHost;
  }
}
