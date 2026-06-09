/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.matrix;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("SystemOut")
public class AsyncGreetingServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final String LATCH_KEY = "LATCH_KEY";
  private static final BlockingQueue<AsyncContext> jobQueue = new LinkedBlockingQueue<>();
  private static final ExecutorService executor = Executors.newFixedThreadPool(2);

  @Override
  public void init() {
    System.err.println("init AsyncGreetingServlet");
    executor.submit(
        new Runnable() {
          @Override
          public void run() {
            try {
              while (true) {
                AsyncContext ac = jobQueue.take();
                System.err.println("got async request from queue");
                executor.submit(() -> handleRequest(ac));
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        });
  }

  @Override
  public void destroy() {
    System.err.println("destroy AsyncGreetingServlet");
    executor.shutdownNow();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
    CountDownLatch latch = null;
    System.err.println("start async request");
    AsyncContext ac = req.startAsync(req, resp);
    boolean requiresDispatchLatch =
        "org.apache.catalina.connector.AsyncContextImpl".equals(ac.getClass().getName());
    if (requiresDispatchLatch) {
      latch = new CountDownLatch(1);
      req.setAttribute(LATCH_KEY, latch);
    }
    System.err.println("add async request to queue");
    jobQueue.add(ac);
    System.err.println("async request added to queue");
    // Some containers require the servlet thread to wait until background dispatch has been
    // triggered, otherwise async dispatch can be skipped due to a container race.
    if (requiresDispatchLatch) {
      try {
        latch.await(30, SECONDS);
        System.err.println("latch released");
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void handleRequest(AsyncContext ac) {
    System.err.println("dispatch async request");
    try {
      ac.dispatch("/greeting");
      System.err.println("async request dispatched");
    } catch (Throwable t) {
      System.err.println("dispatching async request failed");
      t.printStackTrace();
      throw t;
    }
    CountDownLatch latch = (CountDownLatch) ac.getRequest().getAttribute(LATCH_KEY);
    if (latch != null) {
      latch.countDown();
    }
  }
}
