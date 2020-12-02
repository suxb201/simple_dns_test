package org.itxtech.daedalus.speedtest;

import org.itxtech.daedalus.Daedalus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class IpContainer {
    public static class DnsServer{
        public String ip;
        public String description;
        public AtomicInteger querySuccess=new AtomicInteger();
        public AtomicInteger queryFailed=new AtomicInteger();
        public DnsServer(String ip,String description){
            this.ip=ip;
            this.description=description;
        }
    }

    public static class IpFound{
        public String host;
        public int findTimes;
        public long lastFindms;
        public long FoundCoolDownms=1000*60;
        public IpFound(String host){
            this.host=host;
            findTimes=0;
            lastFindms=0;
        }
        public boolean shouldFound(){
            return System.currentTimeMillis()-FoundCoolDownms>lastFindms;
        }
        public void found(){
            lastFindms=System.currentTimeMillis();
            FoundCoolDownms*=2;
        }
    }
    //hostname-ip-time 用来收集访问延迟
    public static final ConcurrentHashMap<String,ConcurrentHashMap<String,Double> > name_ip_time=new ConcurrentHashMap<>();

    //hostname-ip 用来收集域名对应的所有 ip
    public static ConcurrentHashMap<String, IpFound>name_ip=new ConcurrentHashMap<>();
    //初始dns
    public static HashMap<String,String>allDns=new HashMap<String,String>(){{
        put("114.114.114.114","114");
        put("114.114.115.115","114115");
        put("119.29.29.29","DNSPod");
        put("182.254.116.116","腾讯dns2");
        put("192.168.254.245","sdu_qd");
        put("192.168.254.141","sdu_jn");
        put("1.2.4.8","cnnic");
        put("211.137.191.26","mobile_jn");
        put("218.201.96.130","mobile_烟台");
        put("101.226.4.6","dns派1");
        put("218.30.118.6","dns派2");
        put("180.76.76.76","百度dns");
        put("223.5.5.5","阿里dns1");
        put("223.6.6.6","阿里dns2");
//        put("210.2.4.8","中国互联网中心");
        put("8.8.8.8","google");
        put("1.1.1.1","Cloudflare");
        put("208.67.222.222","opendns");

    }};
    public static ArrayList<DnsServer>AllDnsList=new ArrayList<>();
    static {
        for(Map.Entry<String,String>ip_description:allDns.entrySet()){
            AllDnsList.add(new DnsServer(ip_description.getKey(),ip_description.getValue()));
        }
    }
    public static TCPLatencyTest testcore;
    public static int GetSpeedTestCoolDown(){
        return Daedalus.getPrefs().getInt("refresh_rate", 0);
    }
}
