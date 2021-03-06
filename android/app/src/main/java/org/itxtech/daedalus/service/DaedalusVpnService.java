package org.itxtech.daedalus.service;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;

import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.provider.Provider;
import org.itxtech.daedalus.provider.ProviderPicker;
import org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.DnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.speedtest.IpContainer;
import org.itxtech.daedalus.speedtest.TCPLatencyTest;
import org.itxtech.daedalus.util.DnsServersDetector;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.Rule;
import org.itxtech.daedalus.util.RuleResolver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */


public class DaedalusVpnService extends VpnService implements Runnable {
    public static final String ACTION_ACTIVATE = "org.itxtech.daedalus.service.DaedalusVpnService.ACTION_ACTIVATE";
    public static final String ACTION_DEACTIVATE = "org.itxtech.daedalus.service.DaedalusVpnService.ACTION_DEACTIVATE";

    private static final int NOTIFICATION_ACTIVATED = 0;

    private static final String TAG = "DaedalusVpnService";
    private static final String CHANNEL_ID = "daedalus_channel_1";
    private static final String CHANNEL_NAME = "daedalus_channel";

    public static AbstractDnsServer primaryServer;
    public static AbstractDnsServer secondaryServer;
    private static InetAddress aliasPrimary;
    private static InetAddress aliasSecondary;

    private NotificationCompat.Builder notification = null;
    private boolean running = false;
    private long lastUpdate = 0;
    private boolean statisticQuery;
    private Provider provider;
    private ParcelFileDescriptor descriptor;
    private Thread mThread = null;
    public HashMap<String, AbstractDnsServer> dnsServers;
    private static boolean activated = false;
    private static BroadcastReceiver receiver;

    public static boolean isActivated() {
        return activated;
    }

    public Thread changehostThread;


    public static class ActivityUtils {

        /**
         * 判断某个界面是否在前台
         *
         * @param activity 要判断的Activity
         * @return 是否在前台显示
         */
        public static boolean isForeground(Activity activity) {
            return isForeground(activity, activity.getClass().getName());
        }

        /**
         * 判断某个界面是否在前台
         *
         * @param context   Context
         * @param className 界面的类名
         * @return 是否在前台显示
         */
        public static boolean isForeground(Context context, String className) {
            if (context == null || TextUtils.isEmpty(className))
                return false;
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(1);
            if (list != null && list.size() > 0) {
                ComponentName cpn = list.get(0).topActivity;
                if (className.equals(cpn.getClassName()))
                    return true;
            }
            return false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Daedalus.getPrefs().getBoolean("settings_use_system_dns", true)) {
            registerReceiver(receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateUpstreamServers(context);
                }
            }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    private static void updateUpstreamServers(Context context) {
        String[] servers = DnsServersDetector.getServers(context);
        if (servers != null) {
            if (servers.length >= 2 && (aliasPrimary == null || !aliasPrimary.getHostAddress().equals(servers[0])) &&
                    (aliasSecondary == null || !aliasSecondary.getHostAddress().equals(servers[0])) &&
                    (aliasPrimary == null || !aliasPrimary.getHostAddress().equals(servers[1])) &&
                    (aliasSecondary == null || !aliasSecondary.getHostAddress().equals(servers[1]))) {
                primaryServer.setAddress(servers[0]);
                primaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
                secondaryServer.setAddress(servers[1]);
                secondaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
            } else if ((aliasPrimary == null || !aliasPrimary.getHostAddress().equals(servers[0])) &&
                    (aliasSecondary == null || !aliasSecondary.getHostAddress().equals(servers[0]))) {
                primaryServer.setAddress(servers[0]);
                primaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
                secondaryServer.setAddress(servers[0]);
                secondaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
            } else {
                StringBuilder buf = new StringBuilder();
                for (String server : servers) {
                    buf.append(server).append(" ");
                }
                Logger.error("Invalid upstream DNS " + buf);
            }
            Logger.info("Upstream DNS updated: " + primaryServer.getAddress() + " " + secondaryServer.getAddress());
        } else {
            Logger.error("Cannot obtain upstream DNS server!");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_ACTIVATE:
                    activated = true;

                    if (Daedalus.getPrefs().getBoolean("settings_notification", false)) {
                        NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

                        NotificationCompat.Builder builder;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
                            manager.createNotificationChannel(channel);
                            builder = new NotificationCompat.Builder(this, CHANNEL_ID);
                        } else {
                            builder = new NotificationCompat.Builder(this);
                        }

                        Intent deactivateIntent = new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION);
                        deactivateIntent.setClass(this, StatusBarBroadcastReceiver.class);
                        Intent settingsIntent = new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION);
                        settingsIntent.setClass(this, StatusBarBroadcastReceiver.class);
                        PendingIntent pIntent = PendingIntent.getActivity(this, 0,
                                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setWhen(0)
                                .setContentTitle(getResources().getString(R.string.notice_activated))
                                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                                .setSmallIcon(R.drawable.ic_security)
                                .setColor(getResources().getColor(R.color.colorPrimary)) //backward compatibility
                                .setAutoCancel(false)
                                .setOngoing(true)
                                .setTicker(getResources().getString(R.string.notice_activated))
                                .setContentIntent(pIntent)
                                .addAction(R.drawable.ic_clear, getResources().getString(R.string.button_text_deactivate),
                                        PendingIntent.getBroadcast(this, 0,
                                                deactivateIntent, PendingIntent.FLAG_UPDATE_CURRENT))
                                .addAction(R.drawable.ic_settings, getResources().getString(R.string.action_settings),
                                        PendingIntent.getBroadcast(this, 0,
                                                settingsIntent, PendingIntent.FLAG_UPDATE_CURRENT));

                        Notification notification = builder.build();

                        manager.notify(NOTIFICATION_ACTIVATED, notification);

                        this.notification = builder;
                    }

                    Daedalus.initRuleResolver();


                    changehostThread = new Thread(() -> {

                        //空配置文件 尝试从host里读入一份
                        {
                            String file = Daedalus.rulePath + "晓斌加速器" + ".dr";//Daedalus Rule
                            AtomicInteger rulecount = new AtomicInteger();
                            try (Stream<String> lines = Files.lines(Paths.get(file))) {
                                lines.map(s -> s.split(" "))
                                        .filter(arrs -> arrs.length > 2)
                                        .forEach(arrs -> {
                                            rulecount.getAndIncrement();
                                            IpContainer.name_ip_time.put(arrs[1], new ConcurrentHashMap<String, Double>() {{
                                                put(arrs[0], 50.0);
                                            }});
                                        });
                                Logger.info("从host加载 " + rulecount + "份配置");
                            } catch (Exception e) {
                                e.printStackTrace();
                                Logger.i(e.toString());
                            }
                        }

                        if (IpContainer.testcore == null) {
                            IpContainer.testcore = new TCPLatencyTest();
                        }
                        int sleepsecond = 5;
                        long lastfilesave = 0;
                        long lastretest = 0;
                        while (true) {
                            try {
                                Thread.sleep(1000 * 5);
                                ThreadPoolExecutor tpe = ((ThreadPoolExecutor) IpContainer.testcore.executor);
                                int queueSize = tpe.getQueue().size();
                                int activeCount = tpe.getActiveCount();
                                long completedTaskCount = tpe.getCompletedTaskCount();
                                long taskCount = tpe.getTaskCount();
                                if (activeCount > 0) {
                                    Logger.i("线程性能:" + queueSize + "(积压) + " + activeCount + "(执行中) ");
                                }
                                if (System.currentTimeMillis() - lastfilesave >= sleepsecond * 1000) {
                                    sleepsecond *= 2;
                                    lastfilesave = System.currentTimeMillis();
                                    if (sleepsecond > 60) {
                                        sleepsecond = 60;
                                    }
                                    String file = "晓斌加速器" + ".dr";//Daedalus Rule
                                    try {
                                        synchronized (IpContainer.name_ip_time) {
                                            List<String> sss = IpContainer.name_ip_time.entrySet().stream()
                                                    .map(entry -> {
                                                        try {
                                                            Map.Entry<String, Double> ip_time = entry.getValue().entrySet().stream()
                                                                    .min((a, b) -> Double.compare(a.getValue(), b.getValue())).get();
                                                            String host = entry.getKey();
                                                            String ans = ip_time.getKey() + " " + host + " # " + (Double.toString(ip_time.getValue())) + " ms\n";
                                                            if (ip_time.getValue() < 1e5) {
                                                                return ans;
                                                            }
                                                        } catch (Exception e) {
                                                            Logger.i(e.toString());
                                                        }
                                                        return "";
                                                    }).collect(Collectors.toList());
                                            Files.write(Paths.get(Daedalus.rulePath + file), sss);
                                        }
                                    } catch (Exception e) {
                                        Logger.i(e.toString());
                                    }
                                    //刷新host
                                    Rule r = Rule.getRuleById("114514");
                                    if (r == null) {
                                        //添加新的
                                        Rule rule = new Rule("晓斌加速器", file, 0, "");
                                        rule.setId("114514");
                                        rule.addToConfig();
                                        rule.setUsing(true);
                                    } else {
                                        //重载旧的
                                        //好像不用动
                                    }
                                    if (r != null) {
                                        r.setUsing(true);
                                    }
                                    //启动!
                                    //通知变更
                                    Daedalus.setRulesChanged();
                                }
                                if (System.currentTimeMillis() - lastretest >= IpContainer.GetSpeedTestCoolDown() * 60 * 1000) {
                                    lastretest = System.currentTimeMillis();
                                    //限制每个ip最多只能有10个测速的
                                    long t1 = System.currentTimeMillis();
                                    synchronized (IpContainer.name_ip_time) {
                                        IpContainer.name_ip_time.entrySet()
                                                .forEach(entry -> {
                                                    entry.setValue(
                                                            (entry.getValue().entrySet().stream()
                                                                    .sorted((a, b) -> Double.compare(a.getValue(), b.getValue()))
                                                                    .limit(10)
                                                                    .collect(Collectors.toConcurrentMap(
                                                                            Map.Entry::getKey,
                                                                            Map.Entry::getValue,
                                                                            (o1, o2) -> o1,
                                                                            ConcurrentHashMap::new))));
                                                });

                                    }
                                    long t2 = System.currentTimeMillis();
                                    synchronized (IpContainer.name_ip_time) {
                                        IpContainer.name_ip_time.values()
                                                .forEach(ip_time -> ip_time.keySet()
                                                        .forEach(ip -> IpContainer.testcore.test(ip, ip_time::put)));

                                    }
                                    long t3 = System.currentTimeMillis();
                                    Logger.i("streamlimit 耗时" + " " + (t2 - t1) + " ms " + " 重测耗时= " + (t3 - t2) + " ms");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    changehostThread.start();
                    startThread();
                    Daedalus.updateShortcut(getApplicationContext());
                    if (MainActivity.getInstance() != null) {
                        if (ActivityUtils.isForeground((Activity) MainActivity.getInstance())) {
                            //如果在前台 就刷新一下按钮
                            MainActivity.getInstance().startActivity(new Intent(getApplicationContext(), MainActivity.class)
                                    .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_SERVICE_DONE));
                        }
                    }
                    return START_STICKY;
                case ACTION_DEACTIVATE:
                    stopThread();

                    if (changehostThread != null) {
                        //停!
                        changehostThread.interrupt();
                        changehostThread = null;
                    }
                    return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    private void startThread() {
        if (this.mThread == null) {
            this.mThread = new Thread(this, "DaedalusVpn");
            this.running = true;
            this.mThread.start();
        }
    }

    @Override
    public void onDestroy() {
        stopThread();
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    private void stopThread() {


        Log.d(TAG, "stopThread");
        activated = false;
        boolean shouldRefresh = false;
        try {
            if (this.descriptor != null) {
                this.descriptor.close();
                this.descriptor = null;
            }
            if (mThread != null) {
                running = false;
                shouldRefresh = true;
                if (provider != null) {
                    provider.shutdown();
                    mThread.interrupt();
                    provider.stop();
                } else {
                    mThread.interrupt();
                }
                mThread = null;
            }
            if (notification != null) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancel(NOTIFICATION_ACTIVATED);
                notification = null;
            }
            dnsServers = null;
        } catch (Exception e) {
            Logger.logException(e);
        }
        stopSelf();

        if (shouldRefresh) {
            RuleResolver.clear();
            DnsServerHelper.clearCache();
            Logger.info("Daedalus VPN service has stopped");
        }

        if (shouldRefresh && MainActivity.getInstance() != null) {

            if (ActivityUtils.isForeground((Activity) MainActivity.getInstance())) {
                //如果在前台 就刷新一下按钮
                MainActivity.getInstance().startActivity(new Intent(getApplicationContext(), MainActivity.class)
                        .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_SERVICE_DONE));
            }
        } else if (shouldRefresh) {
            Daedalus.updateShortcut(getApplicationContext());
        }
    }

    @Override
    public void onRevoke() {
        stopThread();
    }

    private InetAddress addDnsServer(Builder builder, String format, byte[] ipv6Template, AbstractDnsServer addr)
            throws UnknownHostException {
        int size = dnsServers.size();
        size++;
        if (addr.getAddress().contains("/")) {//https uri
            String alias = String.format(format, size + 1);
            dnsServers.put(alias, addr);
            builder.addRoute(alias, 32);
            return InetAddress.getByName(alias);
        }
        InetAddress address = InetAddress.getByName(addr.getAddress());
        if (address instanceof Inet6Address && ipv6Template == null) {
            Log.i(TAG, "addDnsServer: Ignoring DNS server " + address);
        } else if (address instanceof Inet4Address) {
            String alias = String.format(format, size + 1);
            addr.setHostAddress(address.getHostAddress());
            dnsServers.put(alias, addr);
            builder.addRoute(alias, 32);
            return InetAddress.getByName(alias);
        } else if (address instanceof Inet6Address) {
            ipv6Template[ipv6Template.length - 1] = (byte) (size + 1);
            InetAddress i6addr = Inet6Address.getByAddress(ipv6Template);
            addr.setHostAddress(address.getHostAddress());
            dnsServers.put(i6addr.getHostAddress(), addr);
            return i6addr;
        }
        return null;
    }

    @Override
    public void run() {
        try {
            DnsServerHelper.buildCache();
            Builder builder = new Builder()
                    .setSession("Daedalus")
                    .setConfigureIntent(PendingIntent.getActivity(this, 0,
                            new Intent(this, MainActivity.class).putExtra(MainActivity.LAUNCH_FRAGMENT, MainActivity.FRAGMENT_SETTINGS),
                            PendingIntent.FLAG_ONE_SHOT));

            if (Daedalus.getPrefs().getBoolean("settings_app_filter_switch", false)) {
                ArrayList<String> apps = Daedalus.configurations.getAppObjects();
                if (apps.size() > 0) {
                    boolean mode = Daedalus.getPrefs().getBoolean("settings_app_filter_mode_switch", false);
                    for (String app : apps) {
                        try {
                            if (mode) {
                                builder.addDisallowedApplication(app);
                            } else {
                                builder.addAllowedApplication(app);
                            }
                            Logger.debug("Added app to list: " + app);
                        } catch (PackageManager.NameNotFoundException e) {
                            Logger.error("Package Not Found:" + app);
                        }
                    }
                }
            }

            String format = null;

            for (String prefix : new String[]{"10.0.0", "192.0.2", "198.51.100", "203.0.113", "192.168.50"}) {
                try {
                    builder.addAddress(prefix + ".1", 24);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                format = prefix + ".%d";
                break;
            }

            boolean advanced = Daedalus.getPrefs().getBoolean("settings_advanced_switch", true);
            statisticQuery = Daedalus.getPrefs().getBoolean("settings_count_query_times", false);
            byte[] ipv6Template = new byte[]{32, 1, 13, (byte) (184 & 0xFF), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

            try {
                InetAddress addr = Inet6Address.getByAddress(ipv6Template);
                Log.d(TAG, "configure: Adding IPv6 address" + addr);
                builder.addAddress(addr, 120);
            } catch (Exception e) {
                Logger.logException(e);

                ipv6Template = null;
            }

            if (advanced) {
                dnsServers = new HashMap<>();
                aliasPrimary = addDnsServer(builder, format, ipv6Template, primaryServer);
                aliasSecondary = addDnsServer(builder, format, ipv6Template, secondaryServer);
            } else {
                aliasPrimary = InetAddress.getByName(primaryServer.getAddress());
                aliasSecondary = InetAddress.getByName(secondaryServer.getAddress());
            }

            Logger.info("Daedalus VPN service is listening on " + primaryServer.getAddress() + " as " + aliasPrimary.getHostAddress());
            Logger.info("Daedalus VPN service is listening on " + secondaryServer.getAddress() + " as " + aliasSecondary.getHostAddress());
            builder.addDnsServer(aliasPrimary).addDnsServer(aliasSecondary);

            if (advanced) {
                builder.setBlocking(true);
                builder.allowFamily(OsConstants.AF_INET);
                builder.allowFamily(OsConstants.AF_INET6);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false);
            }
//            try{
//                NetworkRequest rr= new NetworkRequest.Builder()
//                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
//                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
//                        .build();
//                ConnectivityManager m=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
//                ConnectivityManager.NetworkCallback cb=new ConnectivityManager.NetworkCallback();
//                m.registerNetworkCallback(
//                        rr,cb
//                );
//            }catch(Exception ignored){
//
//            }
            descriptor = builder.establish();
            Logger.info("Daedalus VPN service is started");

            if (advanced) {
                provider = ProviderPicker.getProvider(descriptor, this);
                provider.start();
                provider.process();
            } else {
                while (running) {
                    Thread.sleep(1000);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            MainActivity.getInstance().runOnUiThread(() ->
                    new AlertDialog.Builder(MainActivity.getInstance())
                            .setTitle(R.string.error_occurred)
                            .setMessage(Logger.getExceptionMessage(e))
                            .setPositiveButton(android.R.string.ok, (d, id) -> {
                            })
                            .show());
            Logger.logException(e);
        } finally {
            stopThread();
        }
    }

    public void providerLoopCallback() {
        if (statisticQuery) {
            updateUserInterface();
        }
    }

    private void updateUserInterface() {
        long time = System.currentTimeMillis();
        if (time - lastUpdate >= 1000) {
            lastUpdate = time;
            if (notification != null) {
                notification.setContentTitle(getResources().getString(R.string.notice_queries) + " " + provider.getDnsQueryTimes());
                NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                manager.notify(NOTIFICATION_ACTIVATED, notification.build());
            }
        }
    }

    public static class VpnNetworkException extends Exception {
        public VpnNetworkException(String s) {
            super(s);
        }

        public VpnNetworkException(String s, Throwable t) {
            super(s, t);
        }
    }
}
