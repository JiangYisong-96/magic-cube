package org.ssssssss.magicapi.rabbitmq.datasource;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ssssssss.magicapi.rabbitmq.config.GeneralRabbitConfiguration;
import org.ssssssss.magicapi.utils.Assert;

/**
 * 多数据源对象
 *
 * @author jiangys
 */
public class MagicRabbitMqSource {

  private static final Logger logger = LoggerFactory.getLogger(MagicRabbitMqSource.class);
  private final Map<String, RabbitMqSourceNode> dataSourceMap = new ConcurrentHashMap<>();

  /**
   * Register default rabbitmq source
   *
   * @param generalRabbitConfiguration
   */
  public void put(GeneralRabbitConfiguration generalRabbitConfiguration) {
    put(generalRabbitConfiguration);
  }

  public void put(String key, GeneralRabbitConfiguration generalRabbitConfiguration)
      throws IOException, TimeoutException {
    put(null, key, key, generalRabbitConfiguration);
  }

  /**
   * Register rabbitmq data source
   */
  public void put(String id, String key, String name,
      GeneralRabbitConfiguration generalRabbitConfiguration) throws IOException, TimeoutException {
    if (key == null) {
      key = "";
    }
    logger.info("注册rabbit-mq数据源：{}", StringUtils.isNotBlank(key) ? key : "default");
    this.dataSourceMap.put(key,
        new RabbitMqSourceNode(generalRabbitConfiguration, key, name, id));
    if (id != null) {
      String finalDataSourceKey = key;
      this.dataSourceMap.entrySet().stream().filter(
          it -> id.equals(it.getValue().getId()) && !finalDataSourceKey.equals(
              it.getValue().getKey())).findFirst().ifPresent(it -> {
        logger.info("移除旧rabbit-mq数据源:{}", it.getValue().getKey());
        try {
          this.delete(it.getValue().getKey());
        } catch (IOException | TimeoutException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }

  public boolean isEmpty() {
    return this.dataSourceMap.isEmpty();
  }

  /**
   * 获取全部数据源
   */
  public Collection<RabbitMqSourceNode> datasourceNodes() {
    return this.dataSourceMap.values();
  }

  /**
   * 删除数据源
   *
   * @param datasourceKey 数据源Key
   */
  public void delete(String datasourceKey) throws IOException, TimeoutException {
    boolean result = false;
    // 检查参数是否合法
    if (datasourceKey != null && !datasourceKey.isEmpty()) {
      RabbitMqSourceNode node = this.dataSourceMap.remove(datasourceKey);
      result = node != null;
    }
    logger.info("删除rabbit-mq数据源：{}:{}", datasourceKey, result ? "成功" : "失败");
  }

  /**
   * 获取默认rabbitmq数据源
   */
  public RabbitMqSourceNode getDataSource() throws IOException, TimeoutException {
    return getDataSource(null);
  }

  /**
   * 获取rabbitmq数据源
   *
   * @param datasourceKey 数据源Key
   */

  public RabbitMqSourceNode getDataSource(String datasourceKey)
      throws IOException, TimeoutException {
    if (datasourceKey == null) {
      datasourceKey = "";
    }
    RabbitMqSourceNode dataSourceNode = dataSourceMap.get(datasourceKey);
    Assert.isNotNull(dataSourceNode, String.format("找不到rabbitmq源%s", datasourceKey));
    return dataSourceNode;
  }

  @Getter
  public static class RabbitMqSourceNode {

    private final String id;
    private final String key;
    private final String name;
    private final GeneralRabbitConfiguration generalRabbitConfiguration;
    private final ConcurrentHashMap<String, Channel> channelConcurrentHashMap;

    RabbitMqSourceNode(GeneralRabbitConfiguration generalRabbitConfiguration,
        String key, String name, String id)
        throws IOException, TimeoutException {
      this.key = key;
      this.name = name;
      this.id = id;
      this.generalRabbitConfiguration = generalRabbitConfiguration;
      channelConcurrentHashMap = new ConcurrentHashMap<>();
    }
  }
}
