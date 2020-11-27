import time
import socket


def single_tcp(ip: str, port: int, timeout: float):
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(timeout)
        startTime = time.time()
        ans = sock.connect_ex((ip, int(port)))
        sock.shutdown(socket.SHUT_RD)
        endTime = time.time()
        sock.close()
    except:
        endTime,startTime,ans=0,0,1
    return endTime - startTime if ans == 0 else 1e9


from concurrent.futures import ThreadPoolExecutor,wait,ALL_COMPLETED


def spee_push_queue(hostname: str, ip: str, port: int, timeout: float):
    ans = single_tcp(ip, port, timeout)
    return {"hostname": hostname, "ip": ip, "time": ans}

globalthreadPool=ThreadPoolExecutor(max_workers=10, thread_name_prefix="tcptest_")
class SpeedTestThreadPool:
    def __init__(self):
        self.threadPool = globalthreadPool
        self.taskList = []


    def testSpeed(self, hostname: str, ip: str, port: int = 443, timeout: float = 3):
        task=self.threadPool.submit(spee_push_queue,  hostname, ip, port, timeout)
        self.taskList.append(task)
    def wait(self)->list:
        # 结束测速
        wait(self.taskList,return_when=ALL_COMPLETED)
        res=list(map(lambda x:x.result(),self.taskList))
        self.taskList.clear()
        return res



def work(self):
    threadPool=SpeedTestThreadPool()
    hostnames = list(self._hosts.keys())
    for hostname in hostnames:
        ip_info = self._hosts[hostname]
        ips = ip_info.ip_to_nameserver.keys()
        ips = list(filter(lambda x: x is not None, ips))
        if len(ips) ==0:
            #报error
            continue
        for ip in ips:
            threadPool.testSpeed(hostname,ip)
        # 拿结果
        res=threadPool.wait()
        bestres=min(res,key=lambda x:x["time"],default=None)
        if bestres:
            print("best"+str(bestres))


