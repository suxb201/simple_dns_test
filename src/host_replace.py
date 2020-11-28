import os
from pathlib import Path
import shutil


def host_replace(path):
    os.rename('C:\Windows\System32\drivers\etc\hosts', 'hosts.bak')
    shutil.copyfile(path, 'C:\Windows\System32\drivers\etc\host')


def host_recover():
    file = Path('C:\Windows\System32\drivers\etc\hosts.bak')
    if file.is_file():
        os.remove('C:\Windows\System32\drivers\etc\hosts')
        os.rename('C:\Windows\System32\drivers\etc\hosts.bak', 'C:\Windows\System32\drivers\etc\hosts')
