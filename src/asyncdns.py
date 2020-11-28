# async dns

import os
import re
import socket
import struct
from dataclasses import dataclass
from datetime import datetime
from enum import Enum, unique
from typing import Dict, Set, List
import logging
import pickle
import eventloop
from config import config
from tcp_latency import TCPLatency
import os

VALID_HOSTNAME = re.compile(r"(?!-)[A-Z\d\-_]{1,63}(?<!-)$", re.IGNORECASE)

QTYPE_ANY = 255
QTYPE_A = 1
QTYPE_AAAA = 28
QTYPE_CNAME = 5
QTYPE_NS = 2
QCLASS_IN = 1


class DNSPackage:
    @staticmethod
    def build_req(hostname, req_id):  # hostname qtype
        header = req_id + struct.pack('!BBHHHH', 1, 0, 1, 0, 0, 0)  # B 一个字节 H 两个字节
        question = DNSPackage._build_question(hostname) + struct.pack('!HH', QTYPE_A, QCLASS_IN)
        return header + question

    @staticmethod
    def parse_res(data):
        header = DNSPackage._parse_header(data)

        res_id, res_qr, res_tc, res_ra, res_rcode, res_qdcount, res_ancount, res_nscount, res_arcount = header

        questions = []
        answers = []
        offset = 12
        for i in range(0, res_qdcount):
            length, res = DNSPackage._parse_record_question(data, offset)
            offset += length
            questions.append(res)
        for i in range(0, res_ancount):
            length, res = DNSPackage._parse_record_answer(data, offset)
            offset += length
            answers.append(res)

        return answers

    @staticmethod
    def _build_question(hostname):
        labels = hostname.split('.')  # 分割
        results = []
        for label in labels:
            results.append(bytes([len(label)]))  # 长度
            results.append(label.encode(encoding="utf-8"))  # 域名
        results.append(b'\0')
        return b''.join(results)

    @staticmethod
    def _parse_ip(addrtype, data, length, offset):
        # TODO:这里只处理了 type a
        if addrtype == QTYPE_A:
            return socket.inet_ntop(socket.AF_INET, data[offset:offset + length])
        elif addrtype == QTYPE_CNAME:
            return None
        else:
            logging.error(f"addrtype: {addrtype}, {data[offset:offset + length]}")
            return None

    # 解析域名
    @staticmethod
    def _parse_name(data, offset):  # 草！ name 压缩 字节首三位全 1
        p = offset
        labels = []
        length = int(data[p])
        while length > 0:
            if (length & (3 << 6)) == (3 << 6):
                pointer = struct.unpack('!H', data[p:p + 2])[0]
                pointer &= 0x3FFF  # 14bits
                r = DNSPackage._parse_name(data, pointer)
                labels.append(r[1])
                p += 2  # jump
                return p - offset, '.'.join(labels)
            labels.append(data[p + 1:p + 1 + length].decode('utf-8'))
            p += 1 + length
            length = int(data[p])
        return p - offset + 1, '.'.join(labels)

    @staticmethod
    def _parse_record_answer(data, offset):
        nlen, name = DNSPackage._parse_name(data, offset)

        record_type, record_class, record_ttl, record_rdlength = struct.unpack('!HHiH', data[offset + nlen:offset + nlen + 10])
        ip = DNSPackage._parse_ip(record_type, data, record_rdlength, offset + nlen + 10)
        return nlen + 10 + record_rdlength, ip  # 10=HHiH

    @staticmethod
    def _parse_record_question(data, offset):
        nlen, name = DNSPackage._parse_name(data, offset)
        return nlen + 4, name  # 4=QTYPE+QCLASS

    @staticmethod
    def _parse_header(data):
        if len(data) >= 12:  # header 为 12 个字节，也就是 32bits * 3=4Bytes * 3=12Bytes
            header = struct.unpack('!HBBHHHH', data[:12])
            res_id = header[0]
            res_qr = (header[1] & 128) >> 7
            res_tc = (header[1] & 2) >> 1
            res_ra = (header[2] & 128) >> 7
            res_rcode = header[2] & 15
            # assert res_tc == 0
            # assert res_rcode in [0, 3]
            res_qdcount = header[3]
            res_ancount = header[4]
            res_nscount = header[5]
            res_arcount = header[6]
            return res_id, res_qr, res_tc, res_ra, res_rcode, res_qdcount, res_ancount, res_nscount, res_arcount
        return None


def is_valid_hostname(hostname):
    if len(hostname) > 255 or len(hostname) == 0:
        return False
    if hostname[-1] == '.':
        hostname = hostname[:-1]
    return all(VALID_HOSTNAME.match(x) for x in hostname.split('.'))


@dataclass
class DNSResponse:
    req_id: bytes
    hostname: str
    ip_list: List[str]


@unique
class STATUS(Enum):
    INIT = 1
    RUNNING = 2
    FINISH = 3


class Item:
    def __init__(self, hostname):
        self.hostname = hostname
        self.status = STATUS.INIT  # 0: 刚初始化，1：在跑，2：已跑出结果
        self.ip = None
        self.count: int = 0
        self.timestamp: float = 0

        self.ip_to_nameserver: Dict[str, Set[str]] = {}
        self.ip_to_latency: Dict[str, float] = {}

    def is_fresh(self) -> bool:
        if datetime.now().timestamp() - self.timestamp > config['dns']['cache_time']:
            return False
        return True

    # @timeShow
    def calc_fastest_ip_fast(self):
        self.timestamp = datetime.now().timestamp()
        self.ip = None
        tcp_latency = TCPLatency()
        for ip in self.ip_to_nameserver.keys():
            tcp_latency.test(ip, runs=5)
        res = tcp_latency.wait()
        for ip, latency in res:
            if latency is not None:
                self.ip_to_latency[ip] = round(self.ip_to_latency.setdefault(ip, latency) / 2 + latency / 2, 2)

        self.ip, min_latency = min(self.ip_to_latency.items(), key=lambda x: x[1], default=(None, 1e9))
        logging.info(f'{self.hostname}: ip count: {len(self.ip_to_nameserver)}')
        if self.ip is None:
            logging.debug(f"{self.hostname:15}: fk GFW")
        else:
            logging.debug(f"{self.hostname:57}: best ip: {self.ip}, latency: {min_latency}")


def clean_hostname(hostname):
    hostname = hostname.strip('.')
    return hostname


class DNSResolver(object):

    def __init__(self, loop):

        self._hosts: Dict[str, Item] = dict()
        if os.path.exists(config['dns']['temp_file_name']):
            with open(config['dns']['temp_file_name'], 'rb') as f:
                self._hosts = pickle.load(f)
                logging.info(f'load {len(self._hosts)} hostnames.')
        self._exclude: Set[str] = set()
        self._id_to_hostname: Dict[int, str] = dict()
        self._id_to_nameserver: Dict[int, dict] = dict()
        # ---- loop ----
        self._loop = loop  # 主循环
        self._socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.SOL_UDP)
        self._socket.setblocking(False)
        loop.add(self._socket, eventloop.POLL_IN, self)
        loop.add_periodic(self.handle_periodic)

    # 处理 nameserver 返回的数据
    def _handle_data(self, data):
        req_id = int.from_bytes(data[:2], "big")
        if req_id not in self._id_to_nameserver:
            return

        hostname = self._id_to_hostname[req_id]
        nameserver = self._id_to_nameserver[req_id]
        ip_list = DNSPackage.parse_res(data)
        item = self._hosts[hostname]
        item.count -= 1

        for ip in ip_list:
            if ip is None:
                continue
            if ip not in item.ip_to_nameserver:
                item.ip_to_nameserver[ip] = set()
            item.ip_to_nameserver[ip].add(nameserver['describe'])

        del self._id_to_nameserver[req_id]
        del self._id_to_hostname[req_id]

        if item.count == 0:
            item.calc_fastest_ip_fast()
            item.status = STATUS.FINISH

    def handle_event(self, sock, fd, event):
        data, addr = sock.recvfrom(1024)
        self._handle_data(data)

    def handle_periodic(self):
        write_to_file = []
        hostnames = DNSResolver.cy_sort(self._hosts.keys())
        for hostname in hostnames:
            item = self._hosts[hostname]
            if item.ip is not None:
                write_to_file.append(f"{item.ip:<15} {hostname:>31} # {item.ip_to_latency[item.ip]:>6}ms {sorted(list(item.ip_to_nameserver[item.ip]))}\n")
        with open(config['dns']['file_name'], 'w', encoding='utf-8') as f:
            for item in write_to_file:
                f.write(item)
        logging.debug("saved!")

        with open(config['dns']['temp_file_name'], 'wb') as f:
            pickle.dump(self._hosts, f)

    def _send_req(self, nameserver, hostname):
        req_id = os.urandom(2)  # 无符号 2 个字节 = 16bit

        self._id_to_nameserver[int.from_bytes(req_id, "big")] = nameserver
        self._id_to_hostname[int.from_bytes(req_id, "big")] = hostname

        req = DNSPackage.build_req(hostname, req_id)  # TODO: 添加回调函数
        self._socket.sendto(req, (nameserver['ip'], 53))

    def resolve(self, hostname):
        # 如果是无效域名 drop
        if not is_valid_hostname(hostname):
            return
        # 去除首尾 .
        hostname = clean_hostname(hostname)

        item = self._hosts.setdefault(hostname, Item(hostname))

        if item.status == STATUS.INIT:
            for nameserver in config['dns']['nameserver']:
                self._send_req(nameserver, hostname)
                item.count += 1
            item.status = STATUS.RUNNING

        if item.status == STATUS.FINISH:
            if item.is_fresh():
                print(f"{hostname}: hit cache")
            else:
                # 重新来过
                for nameserver in config['dns']['nameserver']:
                    self._send_req(nameserver, hostname)
                    item.count += 1
                item.status = STATUS.RUNNING

    def close(self):
        if self._socket:
            if self._loop:
                self._loop.remove_periodic(self.handle_periodic)
                self._loop.remove(self._socket)
            self._socket.close()
            self._socket = None

    @staticmethod
    def cy_sort(hostnames):
        hostnames = list(map(lambda a: a[::-1], hostnames))
        hostnames.sort()
        hostnames = list(map(lambda a: a[::-1], hostnames))
        return hostnames


def test():
    loop = eventloop.EventLoop()  # 创建 loop 实例
    dns_resolver = DNSResolver(loop)  # 创建实例

    dns_resolver.resolve('www.aliyun.com')
    loop.run()  # rua！


if __name__ == '__main__':
    test()
