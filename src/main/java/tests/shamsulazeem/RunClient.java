package tests.shamsulazeem;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class RunClient {

    private static int clients = 150;
    private static int totalRequests = 1000000;

    public static void main(String[] args) throws InterruptedException {
        int numberOfRequests = totalRequests / clients;
        AtomicInteger requestsDone = new AtomicInteger(0);
        AtomicInteger aggregation = new AtomicInteger(0);
        AtomicInteger lastRequestsDone = new AtomicInteger(0);
        CountDownLatch countDownLatch = new CountDownLatch(numberOfRequests * clients);
        Vertx mainVertx = Vertx.vertx();

        int delay = 1000;
        long periodicTimerId = mainVertx.setPeriodic(delay, handler -> {
            int currentDone = requestsDone.get();
            int difference = currentDone - lastRequestsDone.get();
            System.out.printf("Requests done are: %s. Aggregation: %s. Difference per %sms: %s%n", currentDone, aggregation.get(), delay, difference);
            lastRequestsDone.set(currentDone);
        });

        IntStream.range(0, clients).forEach(
                _1 -> new Thread(() -> {
                    Vertx vertx = Vertx.vertx();
                    WebClient client = WebClient.create(vertx);

                    IntStream.range(0, numberOfRequests).forEach(
                            _2 ->
                                    //client.postAbs("http://localhost:8080/api/v1/PkFRupFiD7xKlPJHLAvr/rpc")
                                    client.postAbs("http://202.165.25.196/api/v1/T2_TEST_TOKEN/rpc")
                                    //client.postAbs("http://localhost:8080/api/v1/PkFRupFiD7xKlPJHLAvr/telemetry")
                                        .sendJsonObject(new JsonObject().put("method", "some_method").put("params", new JsonObject().put("temperature", 2)), requestHandler -> {
                                        //.sendJsonObject(new JsonObject().put("temperature", 1), requestHandler -> {
                                        if (requestHandler.succeeded()) {
                                            requestsDone.incrementAndGet();

                                            try {
                                                aggregation.addAndGet(requestHandler.result().bodyAsJsonObject().getJsonObject("params").getInteger("temperature"));
                                            } catch (Exception exception) {
                                                aggregation.set(-1);
                                            }

                                            countDownLatch.countDown();
                                        } else {
                                            vertx.cancelTimer(periodicTimerId);
                                            System.out.println(requestHandler.cause().getMessage());
                                            System.exit(-1);
                                        }
                                    })
                    );
                }
        ).start());

        countDownLatch.await();

        System.exit(0);
    }
}
