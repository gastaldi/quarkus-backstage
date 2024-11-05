package io.quarkiverse.backstage.client.dsl.events;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.type.TypeReference;

import io.quarkiverse.backstage.client.BackstageClientContext;
import io.quarkiverse.backstage.client.model.ScaffolderEvent;
import io.quarkiverse.backstage.common.utils.Serialization;

public class EventsClient implements EventsInterface, WaitingUntilPredicateCompletionGetInterface<List<ScaffolderEvent>> {

    private static final Predicate<ScaffolderEvent> COMPLETION_PREDICATE = e -> "completion".equalsIgnoreCase(e.getType());
    private BackstageClientContext context;
    private String id;
    private Predicate<ScaffolderEvent> predicate;
    private Long waitingamount;
    private TimeUnit waitingTimeUnit = TimeUnit.MINUTES;
    private Long interval = 1000L;

    public EventsClient(BackstageClientContext context) {
        this.context = context;
    }

    public EventsClient(BackstageClientContext context, String id, Predicate<ScaffolderEvent> predicate, Long waitingamount,
            TimeUnit waitingTimeUnit) {
        this.context = context;
        this.id = id;
        this.predicate = predicate;
        this.waitingamount = waitingamount;
        this.waitingTimeUnit = waitingTimeUnit;
    }

    @Override
    public WaitingUntilPredicateCompletionGetInterface<List<ScaffolderEvent>> forTask(String id) {
        return new EventsClient(context, id, predicate, waitingamount, waitingTimeUnit);
    }

    @Override
    public GetInterface<List<ScaffolderEvent>> waitingUntilCompletion() {
        return new EventsClient(context, id, COMPLETION_PREDICATE, waitingamount, waitingTimeUnit);
    }

    @Override
    public GetInterface<List<ScaffolderEvent>> waitingUntil(Predicate<ScaffolderEvent> predicate) {
        return new EventsClient(context, id, predicate, waitingamount, waitingTimeUnit);
    }

    @Override
    public GetInterface<List<ScaffolderEvent>> waitingUntil(Predicate<ScaffolderEvent> predicate, long waitingAmount,
            TimeUnit waitingTimeUnit) {
        return new EventsClient(context, id, predicate, waitingAmount, waitingTimeUnit);
    }

    @Override
    public List<ScaffolderEvent> get() {
        if (predicate == null) {
            return doGet();
        } else if (waitingamount == null) {
            List<ScaffolderEvent> events = doGet();
            for (; events.stream().anyMatch(predicate); events = doGet()) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return events;
        } else {
            long start = System.currentTimeMillis();
            long end = start + waitingTimeUnit.toMillis(waitingamount);
            List<ScaffolderEvent> events = doGet();
            for (; events.stream().anyMatch(predicate) && System.currentTimeMillis() < end; events = doGet()) {
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return events;
        }
    }

    private List<ScaffolderEvent> doGet() {
        try {
            return context.getWebClient().get("/api/scaffolder/v2/tasks/" + id + "/events")
                    .putHeader("Authorization", "Bearer " + context.getToken())
                    .putHeader("Content-Type", "application/json")
                    .send()
                    .toCompletionStage()
                    .toCompletableFuture()
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            return response.bodyAsString();
                        } else {
                            throw new RuntimeException("Failed to get entity: " + response.statusMessage());
                        }
                    })
                    .thenApply(s -> Serialization.unmarshal(s, new TypeReference<List<ScaffolderEvent>>() {
                    })).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
