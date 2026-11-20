#!/usr/bin/env python3
import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import random
from urllib.request import Request, urlopen

CATEGORIES = ("INJECTION", "XSS", "PROTOCOL_VIOLATION", "DATA_LEAKAGE", "BOT", "DOS", "RATE_LIMIT")
PATHS = ("/", "/cart", "/api/search", "/api/v1/login", "/admin/settings")


def events(count, waves, seed, start):
    rng = random.Random(seed)
    wave_starts = {index * count // (waves + 1): index for index in range(1, waves + 1)}
    wave_remaining = 0
    wave_ip = wave_path = None
    run_id = rng.getrandbits(64)
    for index in range(count):
        if index in wave_starts:
            wave = wave_starts[index]
            wave_remaining = min(20, (wave + 1) * count // (waves + 1) - index)
            wave_ip = "2001:db8::%x" % wave
            wave_path = rng.choice(("/api/v1/login", "/admin/settings"))
        if wave_remaining:
            ip, path = wave_ip, wave_path
            wave_remaining -= 1
        else:
            ip, path = f"203.0.113.{rng.randint(1, 254)}", rng.choice(PATHS)
        category = rng.choice(CATEGORIES)
        config = rng.choice((14227, 14228, 14229))
        action = rng.choice(("DENY", "ALERT", "MONITOR"))
        yield {
            "eventId": f"evt-{run_id:016x}-{index:06d}",
            "timestamp": (start + timedelta(milliseconds=index * 20)).isoformat(),
            "configId": config,
            "policyId": f"policy-{config}",
            "clientIp": ip,
            "hostname": rng.choice(("shop.example.com", "api.example.com")),
            "path": path,
            "method": rng.choice(("GET", "POST")),
            "statusCode": 403 if action == "DENY" else 200,
            "userAgent": rng.choice(("Mozilla/5.0", "SyntheticClient/1.0")),
            "rule": {
                "id": f"rule-{category.lower()}",
                "name": category,
                "message": f"Synthetic {category} event",
                "severity": rng.choice(("CRITICAL", "HIGH", "MEDIUM", "LOW")),
                "category": category,
            },
            "action": action,
            "geoLocation": {"country": "US", "city": "New York"},
            "requestSize": rng.randint(128, 4096),
            "responseSize": rng.randint(128, 4096),
        }


def send(dataset, url, batch_size):
    accepted = 0
    for offset in range(0, len(dataset), batch_size):
        batch = dataset[offset:offset + batch_size]
        request = Request(url, data=json.dumps(batch).encode(), headers={"Content-Type": "application/json"})
        with urlopen(request, timeout=30) as response:
            accepted += json.load(response)["acceptedCount"]
    return accepted


def parse_timestamp(value):
    return datetime.fromisoformat(value[:-1] + "+00:00" if value.endswith("Z") else value)


def main():
    parser = argparse.ArgumentParser(description="Generate synthetic Mini WSA events.")
    parser.add_argument("--count", type=int, default=1000)
    parser.add_argument("--waves", type=int)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--start", type=parse_timestamp,
                        help="Timezone-aware ISO-8601 seed timestamp. Defaults to a window ending now.")
    parser.add_argument("--batch-size", type=int, default=200)
    output = parser.add_mutually_exclusive_group()
    output.add_argument("--output", type=Path, help="Write a JSON array to this file.")
    output.add_argument("--post", help="Send batches to this ingestion endpoint.")
    args = parser.parse_args()
    if args.count < 1 or args.batch_size < 1:
        parser.error("count and batch size must be positive")
    max_waves = (args.count - 1) // 2
    waves = min(3, max_waves) if args.waves is None else args.waves
    if not 0 <= waves <= max_waves:
        parser.error("wave count must leave room for at least two events per wave and a baseline event")
    start = args.start or datetime.now(timezone.utc) - timedelta(milliseconds=(args.count - 1) * 20)
    if start.utcoffset() is None:
        parser.error("--start must include a timezone offset")
    dataset = list(events(args.count, waves, args.seed, start))
    if args.post:
        print(f"Accepted {send(dataset, args.post, args.batch_size)} events")
    elif args.output:
        args.output.write_text(json.dumps(dataset, indent=2) + "\n", encoding="utf-8")
    else:
        print(json.dumps(dataset))


if __name__ == "__main__":
    main()
