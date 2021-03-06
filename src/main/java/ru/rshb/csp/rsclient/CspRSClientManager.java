package ru.rshb.csp.rsclient;

import com.google.gson.Gson;
import io.rsocket.transport.netty.client.TcpClientTransport;
import org.springframework.messaging.rsocket.RSocketRequester;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;
import ru.rshb.taskmanagement.api.dto.OperationRequestMessage;
import ru.rshb.taskmanagement.api.dto.OperationResultMessage;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/*
 * Инициализирует каналы для rsocket
 */
public class CspRSClientManager {
    private Gson gson;
    private RSocketRequester.Builder requesterbuilder;
    private Map<RSServices, CspRSClientManager.RSAddress> rSocketServices;

    private Map<RSServices, RSocketRequester.RequestSpec> requesters = new HashMap<>();

    public Flux<OperationResultMessage> send(RSServices service, String operation, Object params) {

        UUID correlationId = UUID.randomUUID();

        String jsonRequest = gson.toJson(params);
        OperationRequestMessage operationMessage = new OperationRequestMessage(jsonRequest);
        operationMessage.setOperation(operation);
        operationMessage.setCorrelationId(correlationId);

        return getRequester(service)
                .data(operationMessage)
                .retrieveFlux(OperationResultMessage.class)
                .concatMap((message) -> { // Для перехвата сообщений с ошибками
                    if (message.getError() != null) {
                        return Flux.error(message.getError());
                    }
                    return Flux.just(message);
                });
    }

    private RSocketRequester.RequestSpec getRequester(RSServices service) {
        if (!requesters.containsKey(service)) {
            RSAddress address = getServices().get(service);
            RSocketRequester.RequestSpec requester = getBuilder()
                    .rsocketConnector((connector) ->
                            connector.reconnect(Retry.backoff(10, Duration.ofMillis(500)))
                    )
                    .transport(TcpClientTransport.create(address.host, address.port))
                    .route("/operationsChannel");
            requesters.put(service, requester);
        }
        return requesters.get(service);
    }

    public void setServices(Map<RSServices, RSAddress> services) {
        this.rSocketServices = services;
    }

    public Map<RSServices, RSAddress> getServices() {
        return rSocketServices;
    }

    RSocketRequester.Builder getBuilder() {
        return requesterbuilder;
    }

    void setManager(RSocketRequester.Builder builder) {
        this.requesterbuilder = builder;
    }

    private Gson getGson() {
        return gson;
    }

    void setGson(Gson gson) {
        this.gson = gson;
    }

    public static class RSAddress {
        private String host;
        private Integer port;

        RSAddress(String host, Integer port) {
            this.host = host;
            this.port = port;
        }
    }
}