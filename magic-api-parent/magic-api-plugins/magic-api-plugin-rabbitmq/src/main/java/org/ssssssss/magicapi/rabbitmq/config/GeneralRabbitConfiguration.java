package org.ssssssss.magicapi.rabbitmq.config;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.concurrent.TimeoutException;


public class GeneralRabbitConfiguration extends AbstractRabbitConfiguration {

  public GeneralRabbitConfiguration(String host, int port, String username, String password,
      String virtualHost) {
    super(host, port, username, password, virtualHost);
  }

  public Channel getChannel() throws IOException, TimeoutException {
    ConnectionFactory connectionFactory = super.connectionFactory();
    Connection connection = connectionFactory.newConnection();

    return connection.createChannel();
  }
}
