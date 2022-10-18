package org.ssssssss.example.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.ssssssss.example.interceptor.CustomRequestInterceptor;
import org.ssssssss.example.interceptor.CustomUIAuthorizationInterceptor;
import org.ssssssss.example.provider.*;
import org.ssssssss.example.scripts.CustomFunction;
import org.ssssssss.example.scripts.CustomFunctionExtension;
import org.ssssssss.example.scripts.CustomModule;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.provider.PageProvider;
import redis.clients.jedis.JedisPoolConfig;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * magic-api 配置类
 * 以下只配置了多数据源
 * 其它如果有需要可以自行放开 // @Bean 注释查看效果
 */
@Configuration
public class MagicAPIConfiguration {
	@Value("${spring.redis.host}")
	private String host;
	@Value("${spring.redis.database}")
	private Integer database;
	@Value("${spring.redis.port}")
	private Integer port;
	@Value("${spring.redis.password}")
	private String pwd;
	/**
	 * 配置多数据源
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

	@Bean(name = "jedisPoolConfig")
	@ConfigurationProperties(prefix = "spring.redis.pool")
	@Cacheable
	public JedisPoolConfig jedisPoolConfig() {
		JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
		jedisPoolConfig.setMaxWait(Duration.ofSeconds(150));
		return jedisPoolConfig;
	}

	@Bean
	@Cacheable
	public RedisConnectionFactory redisConnectionFactory(JedisPoolConfig jedisPoolConfig) {
		RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
		redisStandaloneConfiguration.setHostName(host);
		redisStandaloneConfiguration.setDatabase(database);
		redisStandaloneConfiguration.setPassword(pwd);
		redisStandaloneConfiguration.setPort(port);
		JedisClientConfiguration.JedisPoolingClientConfigurationBuilder jpcb =
				(JedisClientConfiguration.JedisPoolingClientConfigurationBuilder) JedisClientConfiguration.builder();
		jpcb.poolConfig(jedisPoolConfig);
		JedisClientConfiguration jedisClientConfiguration = jpcb.build();
		return new JedisConnectionFactory(redisStandaloneConfiguration, jedisClientConfiguration);
	}

	/**
	 * 配置自定义JSON结果
	 */
	// @Bean
	public CustomJsonValueProvider customJsonValueProvider() {
		return new CustomJsonValueProvider();
	}

	/**
	 * 配置分页获取方式
	 */
	// @Bean
	public PageProvider pageProvider() {
		return new CustomPageProvider();
	}

	/**
	 * 自定义UI界面鉴权
	 */
	// @Bean
	public CustomUIAuthorizationInterceptor customUIAuthorizationInterceptor() {
		return new CustomUIAuthorizationInterceptor();
	}

	/**
	 * 自定义请求拦截器（鉴权）
	 */
	// @Bean
	public CustomRequestInterceptor customRequestInterceptor() {
		return new CustomRequestInterceptor();
	}

	/**
	 * 自定义SQL缓存
	 */
	// @Bean
	public CustomSqlCache customSqlCache() {
		return new CustomSqlCache();
	}

	/**
	 * 自定义函数
	 */
	// @Bean
	public CustomFunction customFunction() {
		return new CustomFunction();
	}

	/**
	 * 自定义方法扩展
	 */
	// @Bean
	public CustomFunctionExtension customFunctionExtension() {
		return new CustomFunctionExtension();
	}

	/**
	 * 自定义模块
	 */
	// @Bean
	public CustomModule customModule() {
		return new CustomModule();
	}

	/**
	 * 自定义脚本语言
	 */
	// @Bean
	public CustomLanguageProvider customLanguageProvider() {
		return new CustomLanguageProvider();
	}

	/**
	 * 自定义列名转换
	 */
	// @Bean
	public CustomMapperProvider customMapperProvider() {
		return new CustomMapperProvider();
	}

}
