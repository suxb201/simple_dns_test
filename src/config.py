import toml

with open("config.toml", encoding='utf-8') as f:
    config = toml.loads(f.read())

