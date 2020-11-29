package org.itxtech.daedalus.speedtest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class TCPLatencyTest {
    @FunctionalInterface
    public interface Callback {
        void call(String ip, double latency);
    }

    ExecutorService executor;

    public TCPLatencyTest() {
        executor = Executors.newCachedThreadPool();
    }

    private void doTest(String ip, int port, Callback func) {
        try {
            long startTime = System.currentTimeMillis();
            boolean isPinged = InetAddress.getByName(ip).isReachable(2000);
            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;
            if (!isPinged) latency = (long) 1e6;
            func.call(ip, latency);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void test(String ip, int port, Callback func) {
        executor.submit(() -> doTest(ip, port, func));
    }

    public void test(String ip, Callback func) {
        test(ip, 80, func);
    }
}

class Main {
    public static void main(String[] args) {
        TCPLatencyTest t = new TCPLatencyTest();
        t.test("220.181.38.148", (ip, latency) -> System.out.println(ip + " callback: " + latency));
        t.test("zhihu.com", (ip, latency) -> System.out.println(ip + " callback: " + latency));
        t.test("220.181.38.148", (ip, latency) -> System.out.println(ip + " callback: " + latency));
        t.test("120.220.212.3", (ip, latency) -> System.out.println(ip + " callback: " + latency));
    }
}