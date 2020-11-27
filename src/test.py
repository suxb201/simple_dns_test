from tcp_latency import measure_latency
import ping3

while True:
    ip = input()

    t = measure_latency(ip, runs=5, human_output=True)

