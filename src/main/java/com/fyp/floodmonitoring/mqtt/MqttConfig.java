package com.fyp.floodmonitoring.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

/**
 * MQTT inbound configuration — only active when app.mqtt.enabled=true.
 *
 * Subscribes to two topic patterns:
 *   flood/nodes/{nodeId}/data    — QoS 0  (telemetry, fire-and-forget; missed readings
 *                                           are replaced by the next one in ≤1s)
 *   flood/nodes/{nodeId}/status  — QoS 1  (LWT offline events must not be missed)
 *
 * Auto-reconnect is enabled with up to 30s back-off so the listener resumes
 * automatically if Mosquitto restarts.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")
public class MqttConfig {

    @Value("${app.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${app.mqtt.username}")
    private String username;

    @Value("${app.mqtt.password}")
    private String password;

    @Value("${app.mqtt.client-id:flood-service}")
    private String clientId;

    /** Topics the service subscribes to. */
    private static final String TOPIC_TELEMETRY = "flood/nodes/+/data";
    private static final String TOPIC_STATUS    = "flood/nodes/+/status";

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setServerURIs(new String[]{ brokerUrl });
        opts.setUserName(username);
        opts.setPassword(password.toCharArray());
        opts.setAutomaticReconnect(true);          // resume after broker restart
        opts.setMaxReconnectDelay(30_000);         // cap back-off at 30s
        opts.setConnectionTimeout(30);
        opts.setKeepAliveInterval(60);
        opts.setCleanSession(true);
        return opts;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(mqttConnectOptions());
        return factory;
    }

    /** Channel that delivers inbound MQTT messages to the @ServiceActivator. */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId + "-inbound",
                        mqttClientFactory(),
                        TOPIC_TELEMETRY,
                        TOPIC_STATUS);

        adapter.setCompletionTimeout(5_000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        // QoS 0 for telemetry (index 0), QoS 1 for status/LWT (index 1)
        adapter.setQos(new int[]{ 0, 1 });
        adapter.setOutputChannel(mqttInputChannel());

        log.info("[MQTT] Subscribing to broker={} topics=[{}, {}]",
                brokerUrl, TOPIC_TELEMETRY, TOPIC_STATUS);
        return adapter;
    }
}
