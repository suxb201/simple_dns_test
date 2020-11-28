import toml

config = {}


def read_config(config_file):
    with open(config_file, encoding='utf-8') as f:
        tmp_config = toml.loads(f.read())
        for k, v in tmp_config.items():
            config[k] = v
