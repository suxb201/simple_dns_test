# -*- coding:utf8 -*-
from scapy.all import *
from scapy.layers.dns import DNSRR
import asyncdns
import eventloop
import threading

dns_resolver: asyncdns.DNSResolver = None


def dns_sniff(pkt):
    if DNSRR in pkt and pkt.sport == 53:
        hostname = pkt[DNSRR].rrname.decode()
        dns_resolver.resolve(hostname)


def main():
    global dns_resolver

    loop = eventloop.EventLoop()
    dns_resolver = asyncdns.DNSResolver(loop)
    thread = threading.Thread(target=loop.run)
    thread.start()
    sniff(filter="udp and port 53", prn=dns_sniff)


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format='%(filename)13s : %(levelname)6s  %(message)s'
    )
    main()
