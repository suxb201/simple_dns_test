import time
import socket
from concurrent.futures import ThreadPoolExecutor, wait, ALL_COMPLETED

global_thread_pool = ThreadPoolExecutor(max_workers=10, thread_name_prefix="tcp_latency_")


def do_test(ip: str, port: int, timeout: float, runs: int):
    latency = 0
    for i in range(runs):
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(timeout)
        time_start = time.time()
        ans = sock.connect_ex((ip, int(port)))
        if ans != 0:
            return ip, None
        sock.shutdown(socket.SHUT_RD)
        time_end = time.time()
        latency += (time_end - time_start) * 1000
    latency /= runs
    return ip, latency


class TCPLatency:
    def __init__(self):
        self.thread_pool = global_thread_pool
        self.task_list = []

    def test(self, ip: str, port: int = 443, timeout: float = 3, runs=3):
        task = self.thread_pool.submit(do_test, ip, port, timeout, runs)
        self.task_list.append(task)

    def wait(self) -> list:
        wait(self.task_list, return_when=ALL_COMPLETED)
        res = list(map(lambda x: x.result(), self.task_list))
        self.task_list.clear()
        return res
