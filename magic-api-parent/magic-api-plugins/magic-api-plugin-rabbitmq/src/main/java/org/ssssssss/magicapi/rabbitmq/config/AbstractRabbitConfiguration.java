package org.ssssssss.magicapi.rabbitmq.config;

import com.rabbitmq.client.ConnectionFactory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;

/**
 * @author hong
 * @date 2022/3/30 17:22
 * @description MQ配置抽象类
 */
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractRabbitConfiguration {

  protected String host;
  protected int port;
  protected String username;
  protected String password;
  protected String virtualHost;

  protected ConnectionFactory connectionFactory() {
    CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
    connectionFactory.setHost(host);
    connectionFactory.setPort(port);
    connectionFactory.setUsername(username);
    connectionFactory.setPassword(password);
    connectionFactory.setVirtualHost(virtualHost);
    return connectionFactory.getRabbitConnectionFactory();
  }
}
