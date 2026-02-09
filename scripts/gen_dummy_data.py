#!/usr/bin/env python3
"""
Generate synthetic proximity-service dummy data.

Outputs:
- businesses.csv
- geohash_index.csv
- search_queries.csv
- import.sql (optional)
- generation_summary.json
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import random
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path


BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
BITS = (16, 8, 4, 2, 1)


@dataclass(frozen=True)
class Cluster:
    name: str
    latitude: float
    longitude: float
    min_radius_m: int
    max_radius_m: int


HOTSPOT_CLUSTERS = (
    Cluster("gangnam", 37.49794, 127.02758, 50, 1200),
    Cluster("jamsil", 37.51326, 127.10286, 50, 1400),
    Cluster("hongdae", 37.55635, 126.92363, 50, 1000),
    Cluster("yeouido", 37.52190, 126.92456, 50, 1000),
    Cluster("pangyo", 37.39476, 127.11120, 80, 1600),
)

CITY_CLUSTERS = (
    Cluster("busan-seomyeon", 35.15768, 129.05938, 200, 5000),
    Cluster("daegu-downtown", 35.87144, 128.60144, 200, 4200),
    Cluster("daejeon-dunsan", 36.35041, 127.38455, 200, 3800),
    Cluster("gwangju-geumnam", 35.15473, 126.91337, 200, 3500),
    Cluster("incheon-songdo", 37.38257, 126.65595, 200, 4200),
    Cluster("suwon-ingyedong", 37.26433, 127.03104, 200, 3600),
)

RURAL_CLUSTERS = (
    Cluster("gangwon-rural", 37.82280, 128.15550, 1000, 30000),
    Cluster("chungbuk-rural", 36.87040, 127.73420, 1000, 25000),
    Cluster("jeonnam-rural", 34.81610, 126.46290, 1000, 30000),
    Cluster("gyeongbuk-rural", 36.43300, 128.16000, 1000, 28000),
    Cluster("jeju-rural", 33.36170, 126.52920, 1000, 22000),
)

CATEGORY_WEIGHTS = (
    ("CAFE", 0.20),
    ("KOREAN_RESTAURANT", 0.17),
    ("CONVENIENCE_STORE", 0.12),
    ("PHARMACY", 0.08),
    ("HOSPITAL", 0.06),
    ("FITNESS", 0.05),
    ("BAKERY", 0.07),
    ("SALON", 0.06),
    ("BOOKSTORE", 0.04),
    ("PUB", 0.07),
    ("DESSERT", 0.08),
)

RADIUS_WEIGHTS = (
    (500, 0.50),
    (2000, 0.30),
    (5000, 0.15),
    (10000, 0.05),
)

ADJECTIVES = (
    "Blue",
    "Sunny",
    "Happy",
    "Urban",
    "Prime",
    "Fresh",
    "Central",
    "Daily",
    "Mellow",
    "Bright",
)

# Heavier lunch + evening traffic.
HOUR_WEIGHTS = (
    0.2, 0.15, 0.10, 0.10, 0.10, 0.15,
    0.25, 0.45, 0.70, 0.90, 1.10, 1.50,
    1.80, 1.70, 1.20, 1.00, 1.10, 1.25,
    1.55, 1.65, 1.35, 0.95, 0.60, 0.35,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate synthetic proximity-service data")
    parser.add_argument("--business-count", type=int, default=200_000)
    parser.add_argument("--query-count", type=int, default=1_000_000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--output-dir", default="data/dummy")
    parser.add_argument("--sql-batch-size", type=int, default=1000)
    parser.add_argument("--skip-sql", action="store_true")
    parser.add_argument("--query-days", type=int, default=1)
    parser.add_argument("--hotspot-ratio", type=float, default=0.70)
    parser.add_argument("--city-ratio", type=float, default=0.25)
    parser.add_argument("--rural-ratio", type=float, default=0.05)
    parser.add_argument("--active-ratio", type=float, default=0.98)
    return parser.parse_args()


def validate_args(args: argparse.Namespace) -> None:
    if args.business_count < 0 or args.query_count < 0:
        raise ValueError("business-count and query-count must be >= 0")
    if args.sql_batch_size <= 0:
        raise ValueError("sql-batch-size must be > 0")
    if args.query_days <= 0:
        raise ValueError("query-days must be > 0")
    if not 0.0 <= args.active_ratio <= 1.0:
        raise ValueError("active-ratio must be in [0, 1]")
    ratio_sum = args.hotspot_ratio + args.city_ratio + args.rural_ratio
    if abs(ratio_sum - 1.0) > 1e-9:
        raise ValueError("hotspot-ratio + city-ratio + rural-ratio must equal 1.0")


def weighted_choice(rng: random.Random, weighted_items: tuple[tuple[object, float], ...]) -> object:
    threshold = rng.random()
    cumulative = 0.0
    for value, weight in weighted_items:
        cumulative += weight
        if threshold <= cumulative:
            return value
    return weighted_items[-1][0]


def random_distance(rng: random.Random, min_radius_m: float, max_radius_m: float) -> float:
    # Uniform over area, not radius.
    min_sq = min_radius_m * min_radius_m
    max_sq = max_radius_m * max_radius_m
    return math.sqrt(rng.uniform(min_sq, max_sq))


def move_point(lat: float, lon: float, distance_m: float, bearing_rad: float) -> tuple[float, float]:
    lat_delta = (distance_m * math.cos(bearing_rad)) / 111_320.0
    lon_divisor = 111_320.0 * max(0.1, math.cos(math.radians(lat)))
    lon_delta = (distance_m * math.sin(bearing_rad)) / lon_divisor
    moved_lat = max(-89.999999, min(89.999999, lat + lat_delta))
    moved_lon = lon + lon_delta
    while moved_lon < -180.0:
        moved_lon += 360.0
    while moved_lon >= 180.0:
        moved_lon -= 360.0
    return moved_lat, moved_lon


def random_point_near_cluster(rng: random.Random, cluster: Cluster) -> tuple[float, float]:
    distance = random_distance(rng, cluster.min_radius_m, cluster.max_radius_m)
    bearing = rng.uniform(0.0, 2.0 * math.pi)
    return move_point(cluster.latitude, cluster.longitude, distance, bearing)


def encode_geohash(latitude: float, longitude: float, precision: int = 8) -> str:
    lat_range = [-90.0, 90.0]
    lon_range = [-180.0, 180.0]
    geohash_chars: list[str] = []
    even_bit = True
    bit = 0
    ch = 0

    while len(geohash_chars) < precision:
        if even_bit:
            mid = (lon_range[0] + lon_range[1]) / 2.0
            if longitude >= mid:
                ch |= BITS[bit]
                lon_range[0] = mid
            else:
                lon_range[1] = mid
        else:
            mid = (lat_range[0] + lat_range[1]) / 2.0
            if latitude >= mid:
                ch |= BITS[bit]
                lat_range[0] = mid
            else:
                lat_range[1] = mid

        even_bit = not even_bit
        if bit < 4:
            bit += 1
        else:
            geohash_chars.append(BASE32[ch])
            bit = 0
            ch = 0

    return "".join(geohash_chars)


def random_phone(rng: random.Random) -> str:
    return f"010-{rng.randint(1000, 9999)}-{rng.randint(1000, 9999)}"


def random_timestamps(rng: random.Random, now_utc: datetime) -> tuple[str, str]:
    created = now_utc - timedelta(seconds=rng.randint(0, 365 * 24 * 60 * 60))
    max_delta_seconds = int((now_utc - created).total_seconds())
    updated = created + timedelta(seconds=rng.randint(0, max(0, max_delta_seconds)))
    return created.isoformat(), updated.isoformat()


def sql_quote(text: str) -> str:
    return text.replace("'", "''")


def pick_tier(rng: random.Random, args: argparse.Namespace) -> str:
    return weighted_choice(
        rng,
        (
            ("hotspot", args.hotspot_ratio),
            ("city", args.city_ratio),
            ("rural", args.rural_ratio),
        ),
    )


def pick_cluster(rng: random.Random, tier: str) -> Cluster:
    if tier == "hotspot":
        return rng.choice(HOTSPOT_CLUSTERS)
    if tier == "city":
        return rng.choice(CITY_CLUSTERS)
    return rng.choice(RURAL_CLUSTERS)


def pick_hour(rng: random.Random) -> int:
    weighted = tuple((hour, weight / sum(HOUR_WEIGHTS)) for hour, weight in enumerate(HOUR_WEIGHTS))
    return int(weighted_choice(rng, weighted))


def random_query_time(rng: random.Random, now_utc: datetime, query_days: int) -> str:
    day_offset = rng.randint(0, query_days - 1)
    base_day = (now_utc - timedelta(days=day_offset)).date()
    hour = pick_hour(rng)
    minute = rng.randint(0, 59)
    second = rng.randint(0, 59)
    query_time = datetime(
        base_day.year,
        base_day.month,
        base_day.day,
        hour,
        minute,
        second,
        tzinfo=timezone.utc,
    )
    if query_time > now_utc:
        query_time -= timedelta(days=1)
    return query_time.isoformat()


def generate_businesses(args: argparse.Namespace, rng: random.Random, output_dir: Path) -> dict[str, str]:
    businesses_path = output_dir / "businesses.csv"
    geohash_index_path = output_dir / "geohash_index.csv"
    sql_path = output_dir / "import.sql"

    now_utc = datetime.now(timezone.utc).replace(microsecond=0)
    owner_upper_bound = max(1, args.business_count // 4)
    category_weights = tuple((category, weight) for category, weight in CATEGORY_WEIGHTS)

    with businesses_path.open("w", newline="", encoding="utf-8") as business_file, \
            geohash_index_path.open("w", newline="", encoding="utf-8") as geohash_file:
        business_writer = csv.writer(business_file)
        geohash_writer = csv.writer(geohash_file)
        business_writer.writerow(
            [
                "id",
                "owner_id",
                "name",
                "category",
                "phone",
                "address",
                "latitude",
                "longitude",
                "geohash",
                "status",
                "created_at",
                "updated_at",
            ]
        )
        geohash_writer.writerow(["geohash", "business_id"])

        sql_file = None
        sql_open_batch = False
        sql_batch_count = 0
        if not args.skip_sql:
            sql_file = sql_path.open("w", encoding="utf-8")
            sql_file.write("-- Generated by scripts/gen_dummy_data.py\n")
            sql_file.write("BEGIN;\n")

        for business_id in range(1, args.business_count + 1):
            tier = pick_tier(rng, args)
            cluster = pick_cluster(rng, tier)
            latitude, longitude = random_point_near_cluster(rng, cluster)
            category = str(weighted_choice(rng, category_weights))
            adjective = rng.choice(ADJECTIVES)
            name = f"{adjective} {category} {business_id}"
            owner_id = rng.randint(1, owner_upper_bound)
            phone = random_phone(rng)
            address = f"{cluster.name} block-{rng.randint(1, 999)}"
            geohash = encode_geohash(latitude, longitude, 8)
            status = "ACTIVE" if rng.random() <= args.active_ratio else "DELETED"
            created_at, updated_at = random_timestamps(rng, now_utc)

            business_writer.writerow(
                [
                    business_id,
                    owner_id,
                    name,
                    category,
                    phone,
                    address,
                    f"{latitude:.6f}",
                    f"{longitude:.6f}",
                    geohash,
                    status,
                    created_at,
                    updated_at,
                ]
            )
            geohash_writer.writerow([geohash, business_id])

            if sql_file is not None:
                if not sql_open_batch:
                    sql_file.write(
                        "INSERT INTO business "
                        "(id, owner_id, name, category, phone, address, latitude, longitude, geohash, status, created_at, updated_at)\n"
                        "VALUES\n"
                    )
                    sql_open_batch = True

                tuple_sql = (
                    f"({business_id}, {owner_id}, '{sql_quote(name)}', '{category}', '{phone}', "
                    f"'{sql_quote(address)}', {latitude:.6f}, {longitude:.6f}, '{geohash}', '{status}', "
                    f"'{created_at}', '{updated_at}')"
                )
                if sql_batch_count > 0:
                    sql_file.write(",\n")
                sql_file.write(tuple_sql)
                sql_batch_count += 1

                if sql_batch_count >= args.sql_batch_size:
                    sql_file.write(";\n")
                    sql_open_batch = False
                    sql_batch_count = 0

            if business_id % 50_000 == 0:
                print(f"[progress] generated businesses: {business_id}")

        if sql_file is not None:
            if sql_open_batch:
                sql_file.write(";\n")
            sql_file.write("COMMIT;\n")
            sql_file.close()

    result = {
        "businesses_csv": str(businesses_path),
        "geohash_index_csv": str(geohash_index_path),
    }
    if not args.skip_sql:
        result["import_sql"] = str(sql_path)
    return result


def generate_queries(args: argparse.Namespace, rng: random.Random, output_dir: Path) -> dict[str, str]:
    queries_path = output_dir / "search_queries.csv"
    radius_weights = tuple((radius, weight) for radius, weight in RADIUS_WEIGHTS)
    now_utc = datetime.now(timezone.utc).replace(microsecond=0)

    with queries_path.open("w", newline="", encoding="utf-8") as query_file:
        writer = csv.writer(query_file)
        writer.writerow(["query_id", "user_id", "latitude", "longitude", "radius_m", "requested_at", "tier"])

        for query_id in range(1, args.query_count + 1):
            tier = pick_tier(rng, args)
            cluster = pick_cluster(rng, tier)
            query_lat, query_lon = random_point_near_cluster(rng, cluster)
            radius_m = int(weighted_choice(rng, radius_weights))
            user_id = rng.randint(1, 100_000_000)
            requested_at = random_query_time(rng, now_utc, args.query_days)

            writer.writerow(
                [
                    query_id,
                    user_id,
                    f"{query_lat:.6f}",
                    f"{query_lon:.6f}",
                    radius_m,
                    requested_at,
                    tier,
                ]
            )

            if query_id % 200_000 == 0:
                print(f"[progress] generated queries: {query_id}")

    return {"search_queries_csv": str(queries_path)}


def write_summary(args: argparse.Namespace, output_dir: Path, file_map: dict[str, str]) -> None:
    summary = {
        "generated_at_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "business_count": args.business_count,
        "query_count": args.query_count,
        "ratios": {
            "hotspot": args.hotspot_ratio,
            "city": args.city_ratio,
            "rural": args.rural_ratio,
        },
        "active_ratio": args.active_ratio,
        "seed": args.seed,
        "query_days": args.query_days,
        "files": file_map,
    }
    summary_path = output_dir / "generation_summary.json"
    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    file_map["summary_json"] = str(summary_path)


def main() -> None:
    args = parse_args()
    validate_args(args)

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    rng = random.Random(args.seed)

    print(f"[start] generating data into: {output_dir}")
    file_map: dict[str, str] = {}
    file_map.update(generate_businesses(args, rng, output_dir))
    file_map.update(generate_queries(args, rng, output_dir))
    write_summary(args, output_dir, file_map)

    print("[done] generated files:")
    for name, path in file_map.items():
        print(f"  - {name}: {path}")


if __name__ == "__main__":
    main()
