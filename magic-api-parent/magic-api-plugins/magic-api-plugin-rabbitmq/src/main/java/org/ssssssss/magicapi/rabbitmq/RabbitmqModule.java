package org.ssssssss.magicapi.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.beans.Transient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.magicapi.core.model.Options;
import org.ssssssss.magicapi.modules.DynamicModule;
import org.ssssssss.magicapi.rabbitmq.datasource.MagicRabbitMqSource;
import org.ssssssss.magicapi.rabbitmq.datasource.MagicRabbitMqSource.RabbitMqSourceNode;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.annotation.Comment;
import org.ssssssss.script.functions.DynamicAttribute;

/**
 * rabbit mq 模块
 *
 * @author jiangys
 */
@MagicModule("rabbitmq")
public class RabbitmqModule implements DynamicAttribute<RabbitmqModule, RabbitmqModule>,
    DynamicModule<RabbitmqModule> {

  private MagicRabbitMqSource magicRabbitMqSource;
  private RabbitMqSourceNode rabbitMqSourceNode;
  private BasicPropertiesDraft basicPropertiesDraft;
  private Channel currentChannel;
  private String currentQueue;

  public RabbitmqModule(MagicRabbitMqSource rabbitMqSource) {
    magicRabbitMqSource = rabbitMqSource;
  }

  public RabbitmqModule(MagicRabbitMqSource rabbitMqSource,
      BasicPropertiesDraft basicPropertiesDraft) {
    this.magicRabbitMqSource = rabbitMqSource;
    this.basicPropertiesDraft = basicPropertiesDraft;
  }

  @Transient
  public void setMagicRabbitMqSource(MagicRabbitMqSource rabbitMqSource) {
    this.magicRabbitMqSource = rabbitMqSource;
  }

  @Transient
  void setRabbitMqSourceNode(RabbitMqSourceNode rabbitMqSourceNode) {
    this.rabbitMqSourceNode = rabbitMqSourceNode;
  }

  @Transient
  @Override
  public RabbitmqModule getDynamicModule(MagicScriptContext context) {
    String key = context.getString(Options.DEFAULT_DATA_SOURCE.getValue());
    if (StringUtils.isEmpty(key)) {
      return this;
    }
    RabbitmqModule rabbitmqModule = new RabbitmqModule(magicRabbitMqSource,
        basicPropertiesDraft);
    try {
      rabbitmqModule.setRabbitMqSourceNode(magicRabbitMqSource.getDataSource(key));
    } catch (IOException | TimeoutException e) {
      throw new RuntimeException(e);
    }
    return rabbitmqModule;
  }

  /**
   * rabbitmq 数据源切换
   *
   * @param key the object node matching to this key
   * @return RabbitmqModule with the corresponding source node.
   */
  @Override
  @Transient
  public RabbitmqModule getDynamicAttribute(String key) {
    RabbitmqModule rabbitmqModule = new RabbitmqModule(magicRabbitMqSource,
        basicPropertiesDraft);
    try {
      if (key == null) {
        rabbitmqModule.setRabbitMqSourceNode(this.magicRabbitMqSource.getDataSource());
      } else {
        rabbitmqModule.setRabbitMqSourceNode(this.magicRabbitMqSource.getDataSource(key));
      }
    } catch (IOException | TimeoutException e) {
      throw new RuntimeException(e);
    }
    return rabbitmqModule;
  }

  // ======================= Sender Functions =====================

  @Comment("指定队列, 其余默认false / null")
  public RabbitmqModule queueDeclare(
      @Comment(name = "queue", value = "the name of the queue") String queueName)
      throws IOException, TimeoutException {
    return queueDeclare(queueName, false, false, false, null);
  }

  @Comment("指定队列，是否持久化，是否独占此链接，是否自动回收此队列，队列参数")
  public RabbitmqModule queueDeclare(
      @Comment(name = "queueName", value = "the name of the queue") String queueName,
      @Comment(name = "durable", value = "If this queue is durable") boolean durable,
      @Comment(name = "domain", value = "If this queue domain the connection.") boolean domain,
      @Comment(name = "auto delete", value = "Whether auto remove this queue when not been used.") boolean autoRemove,
      @Comment(name = "parameters", value = "Other setting parameters for this queue") Map<String, Object> param)
      throws IOException, TimeoutException {
    String threadId = String.valueOf(Thread.currentThread().getId());
    String channelId = threadId + "_" + queueName;
    Channel channel = rabbitMqSourceNode.getChannelConcurrentHashMap().get(channelId);
    if (channel == null) {
      channel = rabbitMqSourceNode.getGeneralRabbitConfiguration().getChannel();
      channel.queueDeclare(queueName, durable, domain, autoRemove, param);
      rabbitMqSourceNode.getChannelConcurrentHashMap().put(channelId, channel);
    }
    basicPropertiesDraft = null;
    currentQueue = queueName;
    currentChannel = channel;
    return this;
  }

  @Comment("向当前队列里发送消息，基础模式；当前channel必须是唤醒状态并且声明了队列属性；发送后重置basicProperties")
  public void basicPublish(
      @Comment(name = "exchange", value = "交换机名称，如果没有指定，则使用Default Exchange") String exchange,
      //@Comment(name = "routingKey", value = "routingKey,消息的路由Key，是用于Exchange（交换机）将消息转发到指定的消息队列") String routingKey,
      @Comment(name = "msg", value = "消息体") String msg) throws IOException {
    // channel 必须是唤醒状态并且声明了队列属性
    currentChannel.basicPublish(exchange, currentQueue, generateBasicProperties(),
        msg.getBytes());
    basicPropertiesDraft = null;
    currentQueue = null;
    currentChannel = null;
  }

  @Comment("添加Header的 key - value 值")
  public RabbitmqModule addHeader(@Comment(name = "key", value = "key value for header") String key,
      @Comment(name = "obj", value = "Corresponding Object.") Object obj) {
    if (basicPropertiesDraft == null) {
      basicPropertiesDraft = new BasicPropertiesDraft();
    }
    if (basicPropertiesDraft.getHeaders() == null) {
      basicPropertiesDraft.setHeaders(new HashMap<>());
    }
    basicPropertiesDraft.getHeaders().put(key, obj);
    return this;
  }

  @Comment("移除Header的 key - value 值")
  public RabbitmqModule removeHeader(
      @Comment(name = "key", value = "key value for header") String key) {
    if (basicPropertiesDraft != null && !basicPropertiesDraft.getHeaders()
        .isEmpty()) {
      basicPropertiesDraft.getHeaders().remove(key);
    }
    return this;
  }

  @Comment("重置Header对象。")
  public RabbitmqModule flushHeaders() {
    basicPropertiesDraft.setHeaders(null);
    return this;
  }

  @Comment("重置本地的BasicProperties对象")
  public RabbitmqModule flushBasicProperties() {
    basicPropertiesDraft = null;
    return this;
  }
  //====================== Receiver Functions =======================

  @Comment("Default consumer 默认消费者，无须单独声明队列")
  public String defaultConsumer(@Comment(name = "queue", value = "name of the queue.") String queue,
      @Comment(name = "function", value = "lambda function for processing handler.") BiFunction<String, Map<String, Object>, Void> function)
      throws IOException, TimeoutException {
    String consumerId = UUID.randomUUID().toString();
    Channel channel = rabbitMqSourceNode.getGeneralRabbitConfiguration().getChannel();
    channel.queueDeclare(queue, false, false, false, null);
    Consumer consumer = new DefaultConsumer(channel) {
      /**
       * 消费者接收消息调用此方法
       * @param consumerTag 消费者的标签，在channel.basicConsume()去指定
       * @param envelope 消息包的内容，可从中获取消息id，消息routingkey，交换机，消息和重传标志
      (收到消息失败后是否需要重新发送)
       * @param properties
       * @param body
       */
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope,
          AMQP.BasicProperties properties, byte[] body) throws IOException {
        //交换机
        String exchange = envelope.getExchange();
        System.out.println("exchange:" + exchange);
        //路由key
        String routingKey = envelope.getRoutingKey();
        System.out.println("routingKey:" + routingKey);
        //消息id
        long deliveryTag = envelope.getDeliveryTag();
        System.out.println("deliveryTag:" + deliveryTag);
        //消息内容
        String msg = new String(body, StandardCharsets.UTF_8);
        System.out.println("收到消息:" + msg);
        Map<String, Object> propertiesMap = new HashMap<>();
        parseProperties(propertiesMap, properties, msg);
        function.apply(consumerId, propertiesMap);
      }
    };
    try {
      channel.basicConsume(queue, true, consumer);
    } catch (IOException e) {
      throw new IOException(e);
    }
    rabbitMqSourceNode.getChannelConcurrentHashMap().put(consumerId, channel);
    return consumerId;
  }

  @Comment("关闭Consumer所在的Channel")
  public void close(@Comment(name = "consumerId", value = "Id of this consumer") String consumerId)
      throws IOException, TimeoutException {
    Channel channel = rabbitMqSourceNode.getChannelConcurrentHashMap().get(consumerId);
    if (channel != null) {
      channel.close();
    }
    rabbitMqSourceNode.getChannelConcurrentHashMap().remove(consumerId);
  }

  //====================== General Functions ========================
  @Comment("查看所有Channel")
  public Map<String, String> checkAllChannels() {
    Map<String, String> channelMap = new HashMap<>();
    for (Map.Entry<String, Channel> entry : rabbitMqSourceNode.getChannelConcurrentHashMap()
        .entrySet()) {
      channelMap.put(entry.getKey(), entry.getValue().toString());
    }
    return channelMap;
  }

  private BasicProperties generateBasicProperties() {
    return this.basicPropertiesDraft == null ? null
        : new BasicProperties(basicPropertiesDraft.contentType,
            basicPropertiesDraft.contentEncoding, basicPropertiesDraft.headers,
            basicPropertiesDraft.deliveryMode, basicPropertiesDraft.priority,
            basicPropertiesDraft.correlationId, basicPropertiesDraft.replyTo,
            basicPropertiesDraft.expiration, basicPropertiesDraft.messageId,
            basicPropertiesDraft.timestamp,
            basicPropertiesDraft.type, basicPropertiesDraft.userId, basicPropertiesDraft.appId,
            basicPropertiesDraft.clusterId);
  }

  private void parseProperties(Map<String, Object> properties, BasicProperties basicProperties,
      String msg) {
    properties.put("appId", basicProperties.getAppId());
    properties.put("contentType", basicProperties.getContentType());
    properties.put("headers", basicProperties.getHeaders());
    properties.put("clusterId", basicProperties.getClusterId());
    properties.put("messageBody", msg);
  }

  @Data
  public static class BasicPropertiesDraft {

    private String contentType;
    private String contentEncoding;
    private Map<String, Object> headers;
    private Integer deliveryMode;
    private Integer priority;
    private String correlationId;
    private String replyTo;
    private String expiration;
    private String messageId;
    private Date timestamp;
    private String type;
    private String userId;
    private String appId;
    private String clusterId;
  }
}
