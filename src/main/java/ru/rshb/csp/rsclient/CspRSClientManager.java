package ru.rshb.csp.rsclient;

import com.google.gson.Gson;
import io.rsocket.transport.netty.client.TcpClientTransport;
import org.springframework.messaging.rsocket.RSocketRequester;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
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
//@Component
public class CspRSClientManager {
    private Gson gson;
    private RSocketRequester.Builder requesterbuilder;
    private Map<RSServices, CspRSClientManager.RSAddress> rSocketServices;

    private Map<RSServices, Sinks.Many<OperationResultMessage>> responsesSinks
            = new HashMap<>();
    private Map<RSServices, Sinks.Many<OperationRequestMessage>> requestsSinks
            = new HashMap<>();
    private Map<RSServices, RSocketRequester.RequestSpec> requesters
            = new HashMap<>();

    public Flux<OperationResultMessage> post(RSServices service, String operation, Object params) {

        UUID operationId = UUID.randomUUID();

        String jsonRequest = gson.toJson(params);
        OperationRequestMessage operationMessage = new OperationRequestMessage(jsonRequest);
        operationMessage.setOperation(operation);
        operationMessage.setOperationId(operationId);
        //operationMessage.setChannelId(usersDomainChannel);

        getRequestSink(service).tryEmitNext(operationMessage);
        return getResponsSink(service).asFlux()
                .filter((result) -> result.getOperationId().equals(operationId));
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

    public Sinks.Many<OperationRequestMessage> getRequestSink(RSServices service) {
        if (!requestsSinks.containsKey(service)) {
            Sinks.Many<OperationRequestMessage> sink = Sinks.many().replay().all();
            // Подцепляем рекьюстер на этот поток
            sink.asFlux()
                    .flatMap((request) -> {
                        // Здась наверно надо будет что-то запиливать
                        return getRequester(service)
                                .data(request)
                                .retrieveFlux(OperationResultMessage.class)
                                .flatMap((resultMessage) -> { // Для перехвата сообщений с ошибками
                                    if (resultMessage.isFail() || resultMessage.getThrowable() != null) {
                                        return Mono.error(resultMessage.getThrowable() != null
                                                ? resultMessage.getThrowable()
                                                : new Throwable("UNKNOWN_ERROR"));
                                    }
                                    return Mono.just(resultMessage);
                                });
                    })
                    .subscribe((response) -> {
                        getResponsSink(service).tryEmitNext(response);
                    });
            requestsSinks.put(service, sink);
        }
        return requestsSinks.get(service);
    }

    public Sinks.Many<OperationResultMessage> getResponsSink(RSServices service) {
        if (!responsesSinks.containsKey(service)) {
            Sinks.Many<OperationResultMessage> sink = Sinks.many().replay().all();
            responsesSinks.put(service, sink);
        }
        return responsesSinks.get(service);
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

    Gson getGson() {
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