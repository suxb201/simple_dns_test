package org.itxtech.daedalus.speedtest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class TCPLatencyTest {
    @FunctionalInterface
    public interface Callback {
        void call(String ip, double latency);
    }

    public ExecutorService executor;

    public TCPLatencyTest() {
        executor = Executors.newFixedThreadPool(20);

    }

    private void doTest(String ip, int port, Callback func) {
        Socket s = new Socket();
        try {
            InetSocketAddress add = new InetSocketAddress(InetAddress.getByName(ip), 443);
            long startTime = System.currentTimeMillis();
//            boolean isPinged = InetAddress.getByName(ip).isReachable(2000);
            s.connect(add, 200);
            long latency = System.currentTimeMillis() - startTime;
            if (latency >= 200) {
                latency = (long) 1e6;
            }
//            if (!isPinged) latency = (long) 1e6;
            func.call(ip, latency);

        } catch (Exception e) {
            func.call(ip, (long) 1e6);
        }finally {
            try{
                s.close();
            }catch(Exception ignore){}
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
        HashMap<String,Double> ipms=new HashMap<String,Double>(){{
            put("220.181.38.148",0.0);
            put("zhihu.com",0.0);
            put("39.156.69.79",0.0);
            put("120.220.212.3",0.0);}};
        for(Map.Entry<String,Double> m :ipms.entrySet()){
            t.test(m.getKey(),(ip, latency) -> {m.setValue(latency);System.out.println(ip + " callback: " + latency);});
        }
        ipms.put("1",999.0);

        ipms.put("2",999.0);
        ipms.put("3",999.0);
        ipms.put("4",999.0);

        System.out.println("1");
//
//        for(int a : new ArrayList<Integer>(Arrays.asList(1,2,3,4,5))){
//            t.test("220.181.38.148", (ip, latency) -> System.out.println(ip + " 1callback: " + latency));
//            t.test("zhihu.com", (ip, latency) -> System.out.println(ip + " callback: " + latency));
//            t.test("220.181.38.148", (ip, latency) -> System.out.println(ip + " callback: " + latency));
//            t.test("120.220.212.3", (ip, latency) -> System.out.println(ip + " callback: " + latency));
//        }

    }
}