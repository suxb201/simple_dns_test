import sys


def config_generater(read_file, write_file):
    ans: list = list()
    with open(read_file, 'r') as hostfile:
        hosts = hostfile.readlines()
        for host in hosts:
            info = host.split()
            ans.append("A "+info[1] + " " +info[0] + "\n")
    with open(write_file, 'w') as configfile:
        for info1 in ans:
            configfile.write(info1)

config_generater("hosts.txt","dns.conf")