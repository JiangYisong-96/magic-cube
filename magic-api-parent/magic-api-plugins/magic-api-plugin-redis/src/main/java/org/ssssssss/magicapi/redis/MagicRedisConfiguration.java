package org.ssssssss.magicapi.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.config.MagicPluginConfiguration;
import org.ssssssss.magicapi.core.config.Resource;
import org.ssssssss.magicapi.core.model.Plugin;

@Configuration
public class MagicRedisConfiguration implements MagicPluginConfiguration {

	private final MagicAPIProperties properties;

	public MagicRedisConfiguration(MagicAPIProperties properties) {
		this.properties = properties;
	}

	/**
	 * 使用Redis存储
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = "magic-api", name = "resource.type", havingValue = "redis")
	public org.ssssssss.magicapi.core.resource.Resource magicRedisResource(RedisConnectionFactory redisConnectionFactory) {
		Resource resource = properties.getResource();
		return new RedisResource(new StringRedisTemplate(redisConnectionFactory), resource.getPrefix(), resource.isReadonly());
	}

	/**
	 * 注入redis模块
	 */
	@Bean
	public RedisModule redisFunctions(RedisTemplate<String, Object> mgRedisTemplate) {
		return new RedisModule(mgRedisTemplate);
	}

	/**
	 * 配置redisTemplate针对不同key和value场景下不同序列化的方式
	 *
	 * @param redisConnectionFactory Redis连接工厂
	 * @return
	 */
	@Bean(name = "mgRedisTemplate")
	@Cacheable
	public RedisTemplate<String, Object> mgRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(redisConnectionFactory);
		StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
		template.setKeySerializer(stringRedisSerializer);
		template.setHashKeySerializer(stringRedisSerializer);
		Jackson2JsonRedisSerializer<Object> redisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
		template.setValueSerializer(redisSerializer);
		template.setHashValueSerializer(redisSerializer);
		template.afterPropertiesSet();
		return template;
	}

	@Override
	public Plugin plugin() {
		return new Plugin("Redis");
	}
}
