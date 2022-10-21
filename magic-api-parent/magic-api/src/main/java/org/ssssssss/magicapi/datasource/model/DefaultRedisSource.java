//package org.ssssssss.magicapi.datasource.model;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;

@Getter
@Deprecated
public class DefaultRedisSource {

  @Value("${spring.redis.host}")
  private String host;
  @Value("${spring.redis.database}")
  private Integer database;
  @Value("${spring.redis.port}")
  private Integer port;
  @Value("${spring.redis.password}")
  private String password;
}
