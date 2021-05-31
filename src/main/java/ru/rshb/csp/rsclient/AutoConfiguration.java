package ru.rshb.csp.rsclient;

import com.google.gson.Gson;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;

import java.util.HashMap;

@Configuration
@ComponentScan
@ConditionalOnBean({CspRSClientManager.class})
@AutoConfigureBefore({RSocketStrategiesAutoConfiguration.class})
@AutoConfigureAfter({RSocketRequester.Builder.class, CspRSClientManager.class})
public class AutoConfiguration {

    @Bean
    HashMap<RSServices, CspRSClientManager.RSAddress> rSocketServices(
            CspRSClientManager rsClientManager,
            RSocketRequester.Builder builder,
            Gson gson
    ) {
        // TODO: Чтение перечная сервисов и сервиса конфигурации
        HashMap<RSServices, CspRSClientManager.RSAddress> services = new HashMap<>();
        services.put(RSServices.TASKS, new CspRSClientManager.RSAddress("localhost", 7000));
        services.put(RSServices.USERS, new CspRSClientManager.RSAddress("localhost", 7001));

        // Конфигурируется клиент
        rsClientManager.setManager(builder);
        rsClientManager.setServices(services);
        rsClientManager.setGson(gson);

        return services;
    }

    @Bean
    public RSocketStrategies rSocketStrategies() {
        return RSocketStrategies.builder()
                .encoders(encoders -> encoders.add(new Jackson2JsonEncoder()))
                .decoders(decoders -> decoders.add(new Jackson2JsonDecoder()))
                .build();
    }

}