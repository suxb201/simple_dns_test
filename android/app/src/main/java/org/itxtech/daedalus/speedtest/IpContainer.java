package org.itxtech.daedalus.speedtest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class IpContainer {

    //hostname-ip-time 用来收集访问延迟
    public static ConcurrentHashMap<String,ConcurrentHashMap<String,Double> > name_ip_time=new ConcurrentHashMap<>();

    //hostname-ip 用来收集域名对应的所有 ip
    public static HashMap<String, HashSet<String>>name_ip=new HashMap<>();
    //初始dns
    public static HashMap<String,String>allDns=new HashMap<String,String>(){{
        put("114.114.114.114","114");
        put("119.29.29.29","DNSPod");
        put("192.168.254.245","sdu_qd");
        put("1.2.4.8","cnnic");
        put("218.201.96.130","mobile_qd");
        put("101.6.6.6","thu");
        put("101.226.4.6","dns派");
    }};
    public static TCPLatencyTest testcore;

}
