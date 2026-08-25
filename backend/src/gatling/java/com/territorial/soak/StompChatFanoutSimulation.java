package com.territorial.soak;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/** Simple Broker의 월드 채팅 fan-out 지연을 측정한다. */
public class StompChatFanoutSimulation extends Simulation {

    private final int subscribers = Integer.getInteger("subscribers", 100);
    private final int publisherDelaySeconds = Integer.getInteger("publisherDelaySeconds", 10);
    private final AtomicLong publishStartedAtNanos = new AtomicLong();
    private final List<Long> fanoutLatenciesMillis = new ArrayList<>();

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(System.getProperty("baseUrl", "http://localhost:8081"))
                    .wsBaseUrl(System.getProperty("wsBaseUrl", "ws://localhost:8081"))
                    .disableWarmUp();

    private String sockJsUrl() {
        return "/ws/000/"
                + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36)
                + "/websocket";
    }

    private String stompFrame(String command, String headers, String body) {
        return "[\"" + command + "\\n" + headers + "\\n\\n" + body + "\\u0000\"]";
    }

    private final ScenarioBuilder subscriberScenario =
            scenario("stomp-chat-subscriber")
                    .feed(csv("tokens.csv").queue())
                    .exec(session -> session.set("sockJsUrl", sockJsUrl()))
                    .exec(
                            ws("sockjs-connect")
                                    .connect("#{sockJsUrl}")
                                    .onConnected(
                                            exec(ws("stomp-connect")
                                                            .sendText(
                                                                    session ->
                                                                            stompFrame(
                                                                                    "CONNECT",
                                                                                    "accept-version:1.2\\nAuthorization:Bearer "
                                                                                            + session
                                                                                                    .getString(
                                                                                                            "token"),
                                                                                    ""))
                                                            .await(10)
                                                            .on(
                                                                    ws.checkTextMessage(
                                                                                    "stomp-connected")
                                                                            .matching(
                                                                                    regex(
                                                                                            ".*CONNECTED.*"))))
                                                    .exec(
                                                            ws("stomp-subscribe")
                                                                    .sendText(
                                                                            stompFrame(
                                                                                    "SUBSCRIBE",
                                                                                    "id:fanout\\ndestination:/sub/chat/room_world\\nack:auto",
                                                                                    ""))
                                                                    .await(30)
                                                                    .on(
                                                                            ws.checkTextMessage(
                                                                                            "chat-fanout")
                                                                                    .matching(
                                                                                            regex(
                                                                                                    ".*stomp-fanout-probe.*"))))
                                                    .exec(
                                                            session -> {
                                                                long startedAt =
                                                                        publishStartedAtNanos.get();
                                                                if (startedAt > 0) {
                                                                    synchronized (
                                                                            fanoutLatenciesMillis) {
                                                                        fanoutLatenciesMillis.add(
                                                                                Duration.ofNanos(
                                                                                                System
                                                                                                                .nanoTime()
                                                                                                        - startedAt)
                                                                                        .toMillis());
                                                                    }
                                                                }
                                                                return session;
                                                            })
                                                    .exec(ws("sockjs-close").close())));

    private final ScenarioBuilder publisherScenario =
            scenario("stomp-chat-publisher")
                    .feed(csv("tokens.csv").circular())
                    .exec(session -> session.set("sockJsUrl", sockJsUrl()))
                    .exec(
                            ws("publisher-sockjs-connect")
                                    .connect("#{sockJsUrl}")
                                    .onConnected(
                                            exec(ws("publisher-stomp-connect")
                                                            .sendText(
                                                                    session ->
                                                                            stompFrame(
                                                                                    "CONNECT",
                                                                                    "accept-version:1.2\\nAuthorization:Bearer "
                                                                                            + session
                                                                                                    .getString(
                                                                                                            "token"),
                                                                                    ""))
                                                            .await(10)
                                                            .on(
                                                                    ws.checkTextMessage(
                                                                                    "publisher-connected")
                                                                            .matching(
                                                                                    regex(
                                                                                            ".*CONNECTED.*"))))
                                                    .exec(
                                                            session -> {
                                                                publishStartedAtNanos.set(
                                                                        System.nanoTime());
                                                                return session;
                                                            })
                                                    .exec(
                                                            ws("publish-chat-message")
                                                                    .sendText(
                                                                            stompFrame(
                                                                                    "SEND",
                                                                                    "destination:/pub/chat/room_world\\ncontent-type:application/json",
                                                                                    "{\\\"content\\\":\\\"stomp-fanout-probe\\\"}")))
                                                    .exec(ws("publisher-sockjs-close").close())));

    public StompChatFanoutSimulation() {
        setUp(
                        subscriberScenario.injectOpen(atOnceUsers(subscribers)),
                        publisherScenario.injectOpen(
                                nothingFor(Duration.ofSeconds(publisherDelaySeconds)),
                                atOnceUsers(1)))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().percent().lt(1.0));
    }

    @Override
    public void after() {
        synchronized (fanoutLatenciesMillis) {
            List<Long> latencies = new ArrayList<>(fanoutLatenciesMillis);
            latencies.sort(Long::compareTo);
            if (latencies.isEmpty()) {
                System.out.println(
                        "STOMP fan-out latency: no subscriber received the probe message");
                return;
            }
            int p95Index = (int) Math.ceil(latencies.size() * 0.95) - 1;
            int p99Index = (int) Math.ceil(latencies.size() * 0.99) - 1;
            System.out.printf(
                    "STOMP fan-out latency from publisher send: receivers=%d, min=%dms, p95=%dms, p99=%dms, max=%dms%n",
                    latencies.size(),
                    latencies.get(0),
                    latencies.get(p95Index),
                    latencies.get(p99Index),
                    latencies.get(latencies.size() - 1));
        }
    }
}
