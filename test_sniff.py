# -*- coding:utf8 -*-
from scapy.all import *
from scapy.layers.dns import DNSRR, DNSQR
import time


def dns_sniff(pkt):
    if DNSRR in pkt and pkt.sport == 53:
        print(str(pkt[DNSRR].rrname))
    # if DNSQR in pkt and pkt.dport == 53:
    #     if 'bing.com' in str(pkt[DNSQR].qname):
    #         print(time.strftime("%H:%M:%S", time.localtime()))
    #         print("Host is requesting for url: %s from %s" % (pkt[DNSQR].qname[:-1], pkt[IP].dst))
    # elif DNSRR in pkt and pkt.sport == 53:
    #     print(str(pkt[DNSRR].rrname))


def main():
    sniff(filter="udp and port 53", prn=dns_sniff)


if __name__ == "__main__":
    main()
