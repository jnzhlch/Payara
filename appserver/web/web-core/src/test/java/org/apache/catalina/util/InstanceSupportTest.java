package org.apache.catalina.util;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import org.apache.catalina.InstanceEvent;
import org.apache.catalina.InstanceListener;
import org.apache.catalina.Wrapper;
import org.junit.Test;
import static org.mockito.Mockito.mock;

public class InstanceSupportTest {

    static final class RecordingListener implements InstanceListener {
        final List<InstanceEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void instanceEvent(InstanceEvent event) {
            events.add(event);
        }
    }

    private static InstanceSupport newSupport() {
        // InstanceEvent extends EventObject and rejects a null source,
        // so the wrapper must be a non-null stub even though nothing reads it.
        return new InstanceSupport(mock(Wrapper.class));
    }

    @Test
    public void fireNotifiesEveryRegisteredListener() {
        InstanceSupport support = newSupport();
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();
        support.addInstanceListener(first);
        support.addInstanceListener(second);

        support.fireInstanceEvent(InstanceEvent.EventType.BEFORE_SERVICE_EVENT, (jakarta.servlet.Filter) null);

        assertEquals(1, first.events.size());
        assertEquals(1, second.events.size());
        assertEquals(InstanceEvent.EventType.BEFORE_SERVICE_EVENT, first.events.get(0).getType());
    }

    @Test
    public void fireWithoutListenersIsNoOp() {
        InstanceSupport support = newSupport();
        support.fireInstanceEvent(InstanceEvent.EventType.BEFORE_SERVICE_EVENT, (jakarta.servlet.Filter) null);
        // no exception = pass
    }

    @Test
    public void concurrentFireAndAddRemoveIsSafe() throws Exception {
        InstanceSupport support = newSupport();
        RecordingListener stable = new RecordingListener();
        support.addInstanceListener(stable);

        int fireThreads = 6, mutatorThreads = 2, iters = 10_000;
        CountDownLatch done = new CountDownLatch(fireThreads + mutatorThreads);
        for (int t = 0; t < fireThreads + mutatorThreads; t++) {
            final boolean mutator = t < mutatorThreads;
            Thread th = new Thread(() -> {
                try {
                    RecordingListener churn = new RecordingListener();
                    for (int i = 0; i < iters; i++) {
                        if (mutator) {
                            support.addInstanceListener(churn);
                            support.removeInstanceListener(churn);
                        } else {
                            support.fireInstanceEvent(InstanceEvent.EventType.BEFORE_SERVICE_EVENT, (jakarta.servlet.Filter) null);
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
            th.start();
        }

        done.await();
        // The stable listener must see exactly every event fired by every fire thread
        // (no loss, no duplication) despite concurrent add/remove of another listener.
        assertEquals(fireThreads * iters, stable.events.size());
    }
}
