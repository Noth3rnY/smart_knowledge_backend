package com.smartknowledge.backend.common;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Bounds provider responses while retaining HttpClient's whole-request timeout. */
public final class BoundedBody implements HttpResponse.BodySubscriber<String> {
    private final HttpResponse.BodySubscriber<String> delegate=HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
    private final long limit;
    private Flow.Subscription subscription;
    private long received;
    private boolean failed;
    public BoundedBody(long limit) { this.limit=limit; }
    @Override public CompletionStage<String> getBody() { return delegate.getBody(); }
    @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription; delegate.onSubscribe(subscription); }
    @Override public void onNext(List<ByteBuffer> buffers) {
        if(failed) return;
        received+=buffers.stream().mapToLong(ByteBuffer::remaining).sum();
        if(received>limit) { failed=true; subscription.cancel(); delegate.onError(new IOException("Provider response exceeded limit")); }
        else delegate.onNext(buffers);
    }
    @Override public void onError(Throwable error) { if(!failed) delegate.onError(error); }
    @Override public void onComplete() { if(!failed) delegate.onComplete(); }
}
