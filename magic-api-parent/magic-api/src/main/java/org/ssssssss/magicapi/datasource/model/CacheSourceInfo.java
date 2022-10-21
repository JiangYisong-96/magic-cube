package org.ssssssss.magicapi.datasource.model;

import org.ssssssss.magicapi.core.model.MagicEntity;

@Deprecated
public class CacheSourceInfo extends MagicEntity {

  private String host;

  private String username;

  private String password;

  private Integer port;

  private Integer database;

  private String key;

  public void setUsername(String username) {
    this.username = username;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public void setDatabase(Integer database) {
    this.database = database;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getHost() {
    return host;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public Integer getPort() {
    return port;
  }

  public Integer getDatabase() {
    return database;
  }

  public String getKey() {
    return key;
  }

  @Override
  public MagicEntity simple() {
    CacheSourceInfo cacheSourceInfo = new CacheSourceInfo();
    super.simple(cacheSourceInfo);
    cacheSourceInfo.setKey(this.key);
    return cacheSourceInfo;
  }

  @Override
  public MagicEntity copy() {
    CacheSourceInfo cacheSourceInfo = new CacheSourceInfo();
    super.copyTo(cacheSourceInfo);
    cacheSourceInfo.setHost(this.host);
    cacheSourceInfo.setKey(this.key);
    cacheSourceInfo.setPort(this.port);
    cacheSourceInfo.setDatabase(this.database);
    cacheSourceInfo.setUsername(this.username);
    cacheSourceInfo.setPassword(this.password);
    return cacheSourceInfo;
  }
}
