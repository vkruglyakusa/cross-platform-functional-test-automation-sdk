package com.test.automation.sdk.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PageContext - ThreadLocal page name isolation")
class PageContextTest {

    @Test
    @DisplayName("Set and get page name in same thread")
    void setAndGetInSameThread() {
        PageContext.currentPage.set("LoginPage");
        assertEquals("LoginPage", PageContext.currentPage.get());
        PageContext.currentPage.remove();
    }

    @Test
    @DisplayName("Page name is null by default")
    void defaultValueIsNull() {
        PageContext.currentPage.remove();
        assertNull(PageContext.currentPage.get(), "Default ThreadLocal value should be null");
    }

    @Test
    @DisplayName("Set in thread A is not visible in thread B")
    void threadIsolation() throws InterruptedException {
        PageContext.currentPage.set("ThreadAPage");
        AtomicReference<String> threadBValue = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Thread threadB = new Thread(() -> {
            threadBValue.set(PageContext.currentPage.get());
            latch.countDown();
        });
        threadB.start();
        latch.await();
        assertNull(threadBValue.get(), "Thread B must not see Thread A page name");
        PageContext.currentPage.remove();
    }

    @Test
    @DisplayName("Value can be overwritten in same thread")
    void overwriteValue() {
        PageContext.currentPage.set("FirstPage");
        PageContext.currentPage.set("SecondPage");
        assertEquals("SecondPage", PageContext.currentPage.get());
        PageContext.currentPage.remove();
    }

    @Test
    @DisplayName("Remove clears the value")
    void removeClears() {
        PageContext.currentPage.set("SomePage");
        PageContext.currentPage.remove();
        assertNull(PageContext.currentPage.get(), "After remove value must be null");
    }

    @Test
    @DisplayName("Multiple threads each see only their own value")
    void multipleThreadsIndependent() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threadCount);
        AtomicReference<String> firstMismatch = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final String pageName = "Page-" + i;
            new Thread(() -> {
                PageContext.currentPage.set(pageName);
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                String got = PageContext.currentPage.get();
                if (!pageName.equals(got)) {
                    firstMismatch.compareAndSet(null, "Expected " + pageName + " got " + got);
                }
                PageContext.currentPage.remove();
                done.countDown();
            }).start();
        }
        ready.await();
        start.countDown();
        done.await();
        assertNull(firstMismatch.get(), "ThreadLocal isolation failed: " + firstMismatch.get());
    }
}