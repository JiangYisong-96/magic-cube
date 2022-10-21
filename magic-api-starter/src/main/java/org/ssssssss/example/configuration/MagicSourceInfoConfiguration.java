package org.ssssssss.example.configuration;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.ssssssss.example.configuration.datasources.RabbitmqConfigInfo;
import org.ssssssss.example.configuration.datasources.RabbitmqConfigInfo.RabbitServer;
import org.ssssssss.example.configuration.datasources.RedisConfigInfo;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.rabbitmq.config.GeneralRabbitConfiguration;
import org.ssssssss.magicapi.rabbitmq.datasource.MagicRabbitMqSource;
import redis.clients.jedis.JedisPoolConfig;

/**
 * 自动配置后台数据源信息
 */
@Configuration
public class MagicSourceInfoConfiguration {

  @Autowired
  RedisConfigInfo redisConfigInfo;
  @Autowired
  RabbitmqConfigInfo rabbitmqConfigInfo;

  /**
   * 配置多database数据源
   *
   * @see org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource
   */
  @Bean
  public MagicDynamicDataSource magicDynamicDataSource(DataSource dataSource) {
    MagicDynamicDataSource dynamicDataSource = new MagicDynamicDataSource();
    dynamicDataSource.setDefault(dataSource); // 设置默认数据源
    dynamicDataSource.add("slave", dataSource);
    return dynamicDataSource;
  }

  /**
   * 自动配置rabbitmq 数据源
   *
   * @return MagicRabbitMqSource
   * @throws IOException      io issue
   * @throws TimeoutException connection timeout issue
   */
  @Bean
  public MagicRabbitMqSource magicRabbitMqSource() throws IOException, TimeoutException {
    MagicRabbitMqSource magicRabbitMqSource = new MagicRabbitMqSource();
    for (RabbitServer rabbitServer : rabbitmqConfigInfo.getRabbitServer()) {
      magicRabbitMqSource.put(rabbitServer.getId(),
          new GeneralRabbitConfiguration(rabbitServer.getHost(), rabbitServer.getPort(),
              rabbitServer.getUsername(), rabbitServer.getPassword(),
              rabbitServer.getVirtualHost()));
    }
    return magicRabbitMqSource;
  }

  /**
   * jedis pool configuration
   *
   * @return JedisPoolConfig
   */
  @Bean(name = "jedisPoolConfig")
  @ConfigurationProperties(prefix = "spring.redis.pool")
  @Cacheable
  public JedisPoolConfig jedisPoolConfig() {
    JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
    jedisPoolConfig.setMaxWait(Duration.ofMillis(redisConfigInfo.getTimeout()));
    return jedisPoolConfig;
  }

  /**
   * RedisConnectionFactory configuration
   *
   * @param jedisPoolConfig
   * @return RedisConnectionFactory
   */
  @Bean
  @Cacheable
  public RedisConnectionFactory redisConnectionFactory(JedisPoolConfig jedisPoolConfig) {
    RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
    redisStandaloneConfiguration.setHostName(redisConfigInfo.getHost());
    redisStandaloneConfiguration.setDatabase(redisConfigInfo.getDatabase());
    redisStandaloneConfiguration.setPassword(redisConfigInfo.getPassword());
    redisStandaloneConfiguration.setPort(redisConfigInfo.getPort());
    JedisClientConfiguration.JedisPoolingClientConfigurationBuilder jedisPoolingClientConfigBuilder =
        (JedisClientConfiguration.JedisPoolingClientConfigurationBuilder) JedisClientConfiguration.builder();
    jedisPoolingClientConfigBuilder.poolConfig(jedisPoolConfig);
    JedisClientConfiguration jedisClientConfiguration = jedisPoolingClientConfigBuilder.build();
    return new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration);
  }

}
