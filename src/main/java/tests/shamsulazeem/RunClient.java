package tests.shamsulazeem;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class RunClient {

    private static final int clients = 20;
    private static final int totalRequests = 1000;

    public static void main(String[] args) throws InterruptedException, URISyntaxException, IOException {
        String imageString = Files.readString(
                Paths.get(RunClient.class.getClassLoader().getResource("image-base64").toURI()),
                StandardCharsets.UTF_8
        );

        JsonObject dataJson = new JsonObject(Files.readString(
                Paths.get(RunClient.class.getClassLoader().getResource("data.json").toURI()),
                StandardCharsets.UTF_8
        ));

        int numberOfRequests = totalRequests / clients;
        AtomicBoolean finalPrinted = new AtomicBoolean(false);
        AtomicInteger requestsDone = new AtomicInteger(0);
        AtomicInteger successful = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger lastRequestsDone = new AtomicInteger(0);
        CountDownLatch countDownLatch = new CountDownLatch(numberOfRequests * clients);
        Vertx mainVertx = Vertx.vertx();

        AtomicInteger probeIndex = new AtomicInteger(0);
        int delay = 1000;
        long periodicTimerId = mainVertx.setPeriodic(delay, handler -> {
            int currentDone = requestsDone.get();
            int difference = currentDone - lastRequestsDone.get();
            System.out.printf("%05d: Requests done are: %05d/%05d | Successful: %05d | Failed: %05d | Difference per %sms: %s%n",
                    probeIndex.incrementAndGet(), currentDone, totalRequests, successful.get(), failed.get(), delay, difference);
            lastRequestsDone.set(currentDone);
        });

        long startTime = System.currentTimeMillis();

        IntStream.range(0, clients).forEach(
                _1 -> new Thread(() -> {
                    Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(100));
                    WebClient client = WebClient.create(vertx,
                            new WebClientOptions()
                                    .setKeepAliveTimeout(0)
                                    .setKeepAlive(true)
                                    .setHttp2MaxPoolSize(100)
                                    .setHttp2MaxPoolSize(100));

                    IntStream.range(0, numberOfRequests).forEach(
                            _2 ->
                                    /* local - kafka - test */ client.postAbs("http://nb-172-104-37-209.singapore.nodebalancer.linode.com/predict")
                                    /* face detector */ // .sendJsonObject(new JsonObject().put("method", "predict").put("params", new JsonObject().put("image", new JsonObject().put("@ImageFormat", "PNG").put("@ImageData", imageString))), requestHandler -> {
                                        /* dl4j */ //.sendJsonObject(new JsonObject().put("method", "predict").put("params", new JsonObject().put("input", "[[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17]]")), requestHandler -> {
                                        /* temperature */ // .sendJsonObject(new JsonObject().put("method", "some_method").put("params", new JsonObject().put("temperature", 1)), requestHandler -> {
                                        /* kafka - test */ .sendJsonObject(dataJson, requestHandler -> {
                                        if (requestHandler.succeeded()) {
                                            //System.out.println(requestHandler.result().bodyAsString());
                                            requestsDone.incrementAndGet();

                                            try {
                                                JsonObject result = requestHandler.result().bodyAsJsonObject();

                                                boolean condition = result.containsKey("boxes") && result.containsKey("scores");

                                                //successful.addAndGet(result.containsKey("test") ? 1 : 0);
                                                //successful.addAndGet(result.containsKey("out") ? 1 : 0);
                                                //successful.addAndGet((result.containsKey("params") && result.getJsonObject("params").containsKey("image")) ? 1 : 0);
                                                //successful.addAndGet((result.containsKey("params") && result.getJsonObject("params").containsKey("temperature")) ? 1 : 0);
                                                //successful.addAndGet(result.containsKey("image") ? 1 : 0);
                                                //successful.addAndGet(result.containsKey("out") ? 1 : 0);
                                                //successful.addAndGet(result.containsKey("output") ? 1 : 0);

                                                if(condition) {
                                                    successful.addAndGet(1);
                                                } else {
                                                    System.out.println("Failed due to: " + result);
                                                    failed.addAndGet(1);
                                                }
                                            } catch (Exception exception) {
                                                failed.addAndGet(1);
                                            }

                                            countDownLatch.countDown();
                                        } else {
                                            vertx.cancelTimer(periodicTimerId);

                                            if(!finalPrinted.get()) {
                                                finalPrinted.set(true);
                                                System.out.printf("---%n%05d: %05d/%05d requests done in : %sms. (%05d Successful | %05d Failed)%n---",
                                                        probeIndex.incrementAndGet(), requestsDone.get(),
                                                        totalRequests, System.currentTimeMillis() - startTime,
                                                        successful.get(), failed.get());
                                            }

                                            System.out.println(requestHandler.cause().getMessage());
                                            System.exit(-1);
                                        }
                                    })
                    );
                }
        ).start());

        countDownLatch.await();

        if(!finalPrinted.get()) {
            finalPrinted.set(true);
            System.out.printf("---%n%05d: %05d/%05d requests done in : %sms. (%05d Successful | %05d Failed)%n---",
                    probeIndex.incrementAndGet(), requestsDone.get(), totalRequests,
                    System.currentTimeMillis() - startTime, successful.get(), failed.get());
        }

        System.exit(0);
    }
}
