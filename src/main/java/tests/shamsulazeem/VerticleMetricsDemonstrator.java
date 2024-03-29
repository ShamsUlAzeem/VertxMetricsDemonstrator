/*
 * Copyright (c) 2020 Shams Ul Azeem
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tests.shamsulazeem;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="https://github.com/ShamsUlAzeem">Shams Ul Azeem</a>
 */
public class VerticleMetricsDemonstrator {

    static Vertx vertx = Vertx.vertx(new VertxOptions().setMaxEventLoopExecuteTime(10).setMaxEventLoopExecuteTimeUnit(TimeUnit.SECONDS));

    public static void main(String[] args) throws InterruptedException {
        List<Integer> instancesArray = new ArrayList<>();
        for (int i = 1; i <= 1; i++) {
            instancesArray.add(i);
        }

        int[] arrayToPass = new int[instancesArray.size()];
        for (int i = 0; i < instancesArray.size(); i++) {
            arrayToPass[i] = instancesArray.get(i);
        }

        testVerticles(10000, arrayToPass).printStats();
        //testVerticles(1, arrayToPass).printStats();

        vertx.close();
    }

    static void unDeploy(String deploymentId) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        vertx.undeploy(deploymentId, handler -> {
            if (handler.succeeded()) {
                System.out.printf("Deployment with id=\"%s\" undeployed successfully!%n", deploymentId);
                countDownLatch.countDown();
            } else {
                handler.cause().printStackTrace();
                System.exit(-1);
            }
        });

        countDownLatch.await();
    }

    static Stats.StatDetail testVerticle(int numberOfRequests, int verticleInstances) throws InterruptedException {
        AtomicLong currentTime = new AtomicLong();
        AtomicInteger requestsDone = new AtomicInteger(0);
        AtomicReference<String> deploymentId = new AtomicReference<>();
        CountDownLatch countDownLatch = new CountDownLatch(numberOfRequests);
        WebClient client = WebClient.create(vertx);

        vertx.deployVerticle(HttpServerVerticle.class,
                new DeploymentOptions().setInstances(verticleInstances), handler -> {
                    currentTime.set(System.currentTimeMillis());

                    File file = new File("C:\\Users\\shams\\Downloads\\image.jpg");

                    if (handler.succeeded()) {
                        deploymentId.set(handler.result());
                            MultipartForm multipartForm = MultipartForm.create();
                            multipartForm.binaryFileUpload("image", file.getName(), file.getAbsolutePath(), "image/jpeg");

                            for(int i = 0; i < numberOfRequests; i++) {
                                //client.postAbs("http://localhost:8080/api/v1/PkFRupFiD7xKlPJHLAvr/rpc")
                                client.postAbs("http://localhost:8080/api/v1/PkFRupFiD7xKlPJHLAvr/telemetry")
                                //client.postAbs("https://demo.thingsboard.io/api/v1/sL7S6LnNMFTh2ZyTxPgm/telemetry")
                                        .sendJsonObject(new JsonObject().put("temperature", 1), requestHandler -> {
                                        //.sendJsonObject(new JsonObject().put("method", "some_method").put("params", new JsonObject().put("temperature", 1)), requestHandler -> {
                                    if(requestHandler.succeeded()) {
                                        //System.out.println(requestHandler.result().bodyAsString());
                                        requestsDone.incrementAndGet();
                                        countDownLatch.countDown();
                                    } else {
                                        System.out.println(requestHandler.cause().getMessage());
                                        System.exit(-1);
                                    }
                                });
                            }
                    } else {
                        handler.cause().printStackTrace();
                        System.exit(-1);
                    }
                });

        long periodicTimerId = vertx.setPeriodic(1000, handler -> System.out.printf("Requests done are: %s%n", requestsDone.get()));
        countDownLatch.await();

        vertx.cancelTimer(periodicTimerId);

        long elapsedTime = System.currentTimeMillis() - currentTime.get();
        System.out.printf("Time passed between %s requests on %s verticle(s) is: %s milli seconds%n", numberOfRequests, verticleInstances, elapsedTime);

        unDeploy(deploymentId.get());

        return new Stats.StatDetail(numberOfRequests, verticleInstances, elapsedTime);
    }

    static Stats testVerticles(int numberOfRequests, int... verticleInstancesArray) throws InterruptedException {
        Stats stats = new Stats();
        for (int verticleInstances : verticleInstancesArray) {
            stats.statDetails.add(testVerticle(numberOfRequests, verticleInstances));
        }

        return stats;
    }

    static class Stats {
        public List<StatDetail> statDetails;

        Stats() {
            statDetails = new ArrayList<>();
        }

        static class StatDetail {

            StatDetail(int numberOfRequests, int verticleInstances, long elapsedTime) {
                this.numberOfRequests = numberOfRequests;
                this.verticleInstances = verticleInstances;
                this.elapsedTime = elapsedTime;
            }

            public int numberOfRequests;
            public int verticleInstances;
            public long elapsedTime;
        }

        public void printStats() {
            System.out.println("\n\n");
            System.out.println("-------------------------------------------------------------------------");
            System.out.println("| Number Of Requests | Verticle Instances | Time Elapsed |     Gain     |");
            System.out.println("-------------------------------------------------------------------------");
            for (int i = 0; i < statDetails.size(); i++) {
                StatDetail statDetail = statDetails.get(i);
                System.out.printf("| %18s | %18s | %12s | %12s |%n",
                        statDetail.numberOfRequests,
                        statDetail.verticleInstances,
                        statDetail.elapsedTime,
                        calculateGain(i));
            }
            System.out.println("-------------------------------------------------------------------------\n\n\n");
        }

        private String calculateGain(int index) {
            if (index < 1) {
                return "---";
            } else {
                StatDetail statDetailPrevious = statDetails.get(index - 1);
                StatDetail statDetailCurrent = statDetails.get(index);
                float gain = (float) (statDetailPrevious.elapsedTime - statDetailCurrent.elapsedTime) / (float) statDetailCurrent.elapsedTime * 100;
                return String.valueOf(gain);
            }
        }
    }

    public static class HttpServerVerticle extends AbstractVerticle {

        @Override
        public void start(Promise<Void> startPromise) {
            vertx.createHttpServer()
                    .requestHandler(req -> {
                        try {
                            Thread.sleep(0, 1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        req.response().end("Output");
                    })
                    .listen(0, handler -> {
                        if (handler.succeeded()) startPromise.complete();
                        else startPromise.fail(handler.cause());
                    });
        }
    }
}
