package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import androidx.annotation.NonNull;

import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.fragment.DnsTestFragment;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.speedtest.IpContainer;
import org.itxtech.daedalus.speedtest.TCPLatencyTest;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsmessage.Question;
import org.minidns.record.Record;
import org.pcap4j.packet.DnsDomainName;
import org.pcap4j.packet.DnsPacket;
import org.pcap4j.packet.DnsQuestion;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.UdpPacket;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class UdpProvider extends Provider {
    private static final String TAG = "UdpProvider";

    private final WospList dnsIn = new WospList();

    public UdpProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        super(descriptor, service);
    }

    public void process() {
        try {
            FileDescriptor[] pipes = Os.pipe();
            mInterruptFd = pipes[0];
            mBlockFd = pipes[1];
            FileInputStream inputStream = new FileInputStream(descriptor.getFileDescriptor());
            FileOutputStream outputStream = new FileOutputStream(descriptor.getFileDescriptor());

            byte[] packet = new byte[32767];
            while (running) {
                StructPollfd deviceFd = new StructPollfd();
                deviceFd.fd = inputStream.getFD();
                deviceFd.events = (short) OsConstants.POLLIN;
                StructPollfd blockFd = new StructPollfd();
                blockFd.fd = mBlockFd;
                blockFd.events = (short) (OsConstants.POLLHUP | OsConstants.POLLERR);

                if (!deviceWrites.isEmpty())
                    deviceFd.events |= (short) OsConstants.POLLOUT;

                StructPollfd[] polls = new StructPollfd[2 + dnsIn.size()];
                polls[0] = deviceFd;
                polls[1] = blockFd;
                {
                    int i = -1;
                    for (WaitingOnSocketPacket wosp : dnsIn) {
                        i++;
                        StructPollfd pollFd = polls[2 + i] = new StructPollfd();
                        pollFd.fd = ParcelFileDescriptor.fromDatagramSocket(wosp.socket).getFileDescriptor();
                        pollFd.events = (short) OsConstants.POLLIN;
                    }
                }

//                Log.d(TAG, "doOne: Polling " + polls.length + " file descriptors");
                Os.poll(polls, -1);
                if (blockFd.revents != 0) {
                    Log.i(TAG, "Told to stop VPN");
                    running = false;
                    return;
                }

                // Need to do this before reading from the device, otherwise a new insertion there could
                // invalidate one of the sockets we want to read from either due to size or time out
                // constraints
                {
                    int i = -1;
                    Iterator<WaitingOnSocketPacket> iter = dnsIn.iterator();
                    while (iter.hasNext()) {
                        i++;
                        WaitingOnSocketPacket wosp = iter.next();
                        if ((polls[i + 2].revents & OsConstants.POLLIN) != 0) {
//                            Log.d(TAG, "Read from UDP DNS socket" + wosp.socket);
                            iter.remove();
                            handleRawDnsResponse(wosp.packet, wosp.socket);
                            wosp.socket.close();
                        }
                    }
                }
                if ((deviceFd.revents & OsConstants.POLLOUT) != 0) {
//                    Log.d(TAG, "Write to device");
                    writeToDevice(outputStream);
                }
                if ((deviceFd.revents & OsConstants.POLLIN) != 0) {
//                    Log.d(TAG, "Read from device");
                    readPacketFromDevice(inputStream, packet);
                }
                service.providerLoopCallback();
            }
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    protected void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket, AbstractDnsServer dnsServer) throws DaedalusVpnService.VpnNetworkException {
        DatagramSocket dnsSocket;
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            dnsSocket = new DatagramSocket();

            service.protect(dnsSocket);

            dnsSocket.send(outPacket);

            if (parsedPacket != null) {
                dnsIn.add(new WaitingOnSocketPacket(dnsSocket, parsedPacket));
            } else {
                dnsSocket.close();
            }
        } catch (IOException e) {
            handleDnsResponse(parsedPacket, outPacket.getData());
            Logger.warning("DNSProvider: Could not send packet to upstream, forwarding packet directly");
        }
    }

    private void handleRawDnsResponse(IpPacket parsedPacket, DatagramSocket dnsSocket) {
        try {
            byte[] datagramData = new byte[1024];
            DatagramPacket replyPacket = new DatagramPacket(datagramData, datagramData.length);
            dnsSocket.receive(replyPacket);
            handleDnsResponse(parsedPacket, datagramData);
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    static Pattern pip;

    static {
        String regex = "^([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])\\." +
                "(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])\\." +
                "(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])\\." +
                "(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])$";
        pip = Pattern.compile(regex);
    }

    public boolean isIPAddressByRegex(String str) {
        if (str.equals("") || str.length() == 0) {
            return false;
        }
        Matcher m = pip.matcher(str);
        return m.matches();
    }


    /**
     * Handles a DNS request, by either blocking it or forwarding it to the remote location.
     *
     * @param packetData The packet data to read
     * @throws DaedalusVpnService.VpnNetworkException If some network error occurred
     */

    @Override
    protected void handleDnsRequest(byte[] packetData) throws DaedalusVpnService.VpnNetworkException {
        IpPacket parsedPacket;
        try {
            parsedPacket = (IpPacket) IpSelector.newPacket(packetData, 0, packetData.length);
        } catch (Exception e) {
            Log.i(TAG, "handleDnsRequest: Discarding invalid IP packet", e);
            return;
        }

        if (!(parsedPacket.getPayload() instanceof UdpPacket)) {
            //只检查udp的数据包
            try {
                Logger.debug("handleDnsRequest: Discarding unknown packet type " + parsedPacket.getPayload());
            } catch (Exception ignored) {
            }
            return;
        }

        InetAddress destAddr = parsedPacket.getHeader().getDstAddr();
        if (destAddr == null) {
            return;
        }

        UdpPacket upk = (UdpPacket) parsedPacket.getPayload();
        if(upk.getPayload() instanceof DnsPacket){
            DnsPacket dpk = (DnsPacket) upk.getPayload();
            DnsPacket.DnsHeader dhd = dpk.getHeader();
            ArrayList<DnsQuestion> al = (ArrayList<DnsQuestion>) dhd.getQuestions();
            for (DnsQuestion q : al) {

                DnsDomainName domainName = q.getQName();
                String domainNameS = domainName.toString();
                if (!domainNameS.contains(".")) {
                    //没有.也能叫域名?
                    continue;
                }
                IpContainer.IpFound found = IpContainer.name_ip.computeIfAbsent(domainNameS, (k) -> new IpContainer.IpFound(domainNameS));
                if (found.shouldFound()) {
                    //刷新次数
                    found.found();
                    long startTime = System.currentTimeMillis();
                    if (IpContainer.testcore == null) {
                        IpContainer.testcore = new TCPLatencyTest();
                    }

                    //这里去找所有的ip
                    for (IpContainer.DnsServer server: IpContainer.AllDnsList) {
                        String ip= server.ip;
                        IpContainer.testcore.executor.submit(() -> {
                            try {
                                DnsTestFragment.DnsQuery dnsQuery = new DnsTestFragment.DnsQuery();
                                DnsMessage.Builder message = DnsMessage.builder()
                                        .addQuestion(new Question(domainName.toString(), Record.TYPE.A))
                                        .setId((new Random()).nextInt())
                                        .setRecursionDesired(true)
                                        .setOpcode(DnsMessage.OPCODE.QUERY)
                                        .setResponseCode(DnsMessage.RESPONSE_CODE.NO_ERROR)
                                        .setQrFlag(false);
                                DnsMessage response = dnsQuery.query(message.build(), InetAddress.getByName(ip), 53);
                                if (response.answerSection.size() > 0) {
                                    ConcurrentHashMap<String,Double>ip_time=IpContainer.name_ip_time.computeIfAbsent(domainNameS, (k) -> new ConcurrentHashMap<>());
                                    for (Record record : response.answerSection) {
                                        String ipstr = record.getPayload().toString();
                                        if (isIPAddressByRegex(ipstr) && !ip_time.containsKey(ipstr)) {
                                            IpContainer.testcore.test(ipstr, ip_time::put);
                                        }
                                    }
                                    server.querySuccess.getAndIncrement();
                                }
                            } catch (IOException e) {
                                server.queryFailed.getAndIncrement();
//                                Logger.info("拉闸dns! " + server.description + " " + domainName.toString());
                            }
                        });
                    }
                    long endTime = System.currentTimeMillis();
                    Logger.info("找到域名 " + domainNameS + " submit耗时 " + (endTime - startTime) + "ms");

                }
            }
        }







        AbstractDnsServer dnsServer;
        try {
            dnsServer = service.dnsServers.get(destAddr.getHostAddress());
            destAddr = InetAddress.getByName(dnsServer.getHostAddress());
        } catch (Exception e) {
            Logger.logException(e);
            Logger.error("handleDnsRequest: DNS server alias query failed for " + destAddr.getHostAddress());
            return;
        }

        UdpPacket parsedUdp = (UdpPacket) parsedPacket.getPayload();

        if (parsedUdp.getPayload() == null) {
            Log.i(TAG, "handleDnsRequest: Sending UDP packet without payload: " + parsedUdp);
            //不走vpn的数据
            // Let's be nice to Firefox. Firefox uses an empty UDP packet to
            // the gateway to reduce the RTT. For further details, please see
            // https://bugzilla.mozilla.org/show_bug.cgi?id=888268
            DatagramPacket outPacket = new DatagramPacket(new byte[0], 0, 0, destAddr, dnsServer.getPort());
            forwardPacket(outPacket, null, dnsServer);
            return;
        }

        byte[] dnsRawData = (parsedUdp).getPayload().getRawData();
        DnsMessage dnsMsg;
        try {
            dnsMsg = new DnsMessage(dnsRawData);
            if (Daedalus.getPrefs().getBoolean("settings_debug_output", false)) {
                Logger.debug("DnsRequest: " + dnsMsg.toString());
            }
        } catch (IOException e) {
            Log.i(TAG, "handleDnsRequest: Discarding non-DNS or invalid packet", e);
            return;
        }
        if (dnsMsg.getQuestion() == null) {
            Logger.debug("handleDnsRequest: Discarding DNS packet with no query " + dnsMsg);
            return;
        }
        //先resolve 走一波host
        if (!resolve(parsedPacket, dnsMsg)) {
            //如果host里没找到规则
            DatagramPacket outPacket = new DatagramPacket(dnsRawData, 0, dnsRawData.length, destAddr, dnsServer.getPort());
            forwardPacket(outPacket, parsedPacket, dnsServer);
        }
    }

    /**
     * Helper class holding a socket, the packet we are waiting the answer for, and a time
     */
    private static class WaitingOnSocketPacket {
        final DatagramSocket socket;
        final IpPacket packet;
        private final long time;

        WaitingOnSocketPacket(DatagramSocket socket, IpPacket packet) {
            this.socket = socket;
            this.packet = packet;
            this.time = System.currentTimeMillis();
        }

        long ageSeconds() {
            return (System.currentTimeMillis() - time) / 1000;
        }
    }

    /**
     * Queue of WaitingOnSocketPacket, bound on time and space.
     */
    private static class WospList implements Iterable<WaitingOnSocketPacket> {
        private final LinkedList<WaitingOnSocketPacket> list = new LinkedList<>();

        void add(WaitingOnSocketPacket wosp) {
            if (list.size() > 1024) {
                Log.d(TAG, "Dropping socket due to space constraints: " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            while (!list.isEmpty() && list.element().ageSeconds() > 10) {
                Log.d(TAG, "Timeout on socket " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            list.add(wosp);
        }

        @NonNull
        public Iterator<WaitingOnSocketPacket> iterator() {
            return list.iterator();
        }

        int size() {
            return list.size();
        }

    }
}
