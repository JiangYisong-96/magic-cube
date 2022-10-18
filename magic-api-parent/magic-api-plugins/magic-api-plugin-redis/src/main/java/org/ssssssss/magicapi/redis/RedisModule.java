package org.ssssssss.magicapi.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.magicapi.redis.service.RedisService;
import org.ssssssss.magicapi.redis.service.impl.RedisServiceImpl;
import org.ssssssss.script.annotation.Comment;
import org.ssssssss.script.functions.DynamicMethod;

import java.util.List;

/**
 * redis模块
 *
 * @author mxd
 */
@Component
@MagicModule("redis")
public class RedisModule implements DynamicMethod {
	private final RedisService redisService;

	public RedisModule(RedisTemplate<String, Object> redisTemplate) {
		this.redisService = new RedisServiceImpl(redisTemplate);
	}

	/**
	 * 执行命令
	 *
	 * @param methodName 命令名称
	 * @param parameters 命令参数
	 */
	@Override
	public Object execute(String methodName, List<Object> parameters) {
		return redisService.redisExecute(methodName, parameters);
	}
	/*
	======================= General ============
	 */
	@Comment("判斷可以是否存在")
	public boolean hasKey(@Comment(name = "key", value = "key value") String key) {
		return redisService.hasKey(key);
	}

	@Comment("指定緩存失效時間")
	public boolean expire(@Comment(name = "key", value = "key value") String key,
						  @Comment(name = "time", value = "expire time in ms") long time) {
		return redisService.expire(key, time);
	}
	/*
	======================= KV =================
	 */

	@Comment("插入 key value 到redis 缓存。")
	public Boolean set(@Comment(name = "key", value = "key value") String key,
					  @Comment(name = "value", value = "matched object") Object value) {
		return redisService.set(key, value);
	}

	@Comment("插入 key value 以及 有效时间到redis缓存。")
	public Boolean set(@Comment(name = "key", value = "key value") String key,
					   @Comment(name = "value", value = "matched object") Object value,
					   @Comment(name = "time", value = "expire time in ms") long time){
		return redisService.set(key, value, time);
	}

	@Comment("从redis缓存中通过key 读取 value")
	public Object get(@Comment(name = "key", value = "key value") String key) {
		return redisService.get(key);
	}

	@Comment("删除key键所对应的对象")
	public void del(@Comment(name = "key", value = "key value") String key) throws Exception {
		try {
			redisService.del(key);
		} catch (Exception e) {
			throw new Exception(e);
		}
	}

}
