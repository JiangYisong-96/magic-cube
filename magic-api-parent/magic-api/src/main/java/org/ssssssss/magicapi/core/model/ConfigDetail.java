package org.ssssssss.magicapi.core.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Remote value passer for registering configurations
 * eg. redisConfig, kafkaConfig
 */

@Data
@AllArgsConstructor (access = AccessLevel.PRIVATE)
@NoArgsConstructor
public class ConfigDetail {
    /* ip address or host value */
    private String host;

    /* port */
    private Integer port;

    /* db index */
    private Integer dbIndex;

    /* username */
    private String username;

    /* password */
    private String password;

}
