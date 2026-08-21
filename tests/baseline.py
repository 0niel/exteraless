import json
import os

BASELINE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "baselines")


def load(name):
    path = os.path.join(BASELINE_DIR, name)
    if not os.path.isfile(path):
        return {}
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def save(name, payload):
    os.makedirs(BASELINE_DIR, exist_ok=True)
    path = os.path.join(BASELINE_DIR, name)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2, sort_keys=True)
        fh.write("\n")


def compare(name, found):
    known = set(load(name).get("known", ()))
    found = set(found)
    return sorted(found - known), sorted(known - found)
