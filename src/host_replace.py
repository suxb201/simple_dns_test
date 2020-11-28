import os


def host_replace(path):
    os.system("rename C:\Windows\System32\drivers\etc\hosts hosts.bak")
    os.system("copy " + path + "C:\Windows\System32\drivers\etc\host")