# -*- coding:utf8 -*-
from scapy.all import *
from scapy.layers.dns import DNSRR
import asyncdns
import eventloop
import threading
import argparse
import logging
import config

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

    parser = argparse.ArgumentParser()
    parser.add_argument("-c", "--config_file",
                        help="increase output verbosity",
                        default="./config.toml")
    args = parser.parse_args()
    logging.info(f'args: {args}')
    config.read_config(args.config_file)



    main()
