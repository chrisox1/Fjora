"""
Data ingest: spot prices, production, realtime 5-min, gas prices, temperature.

Each ``fetch_*`` function pulls from the remote API (or local CSV) and upserts
into the SQLite database.
"""

from __future__ import annotations

import csv
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import requests

from .api import fetch_pages, strip_z
from .config import (
    ELSPOT_CUTOFF,
    GAS_CSV_PATH,
    HISTORY_YEARS,
    ALL_PRICE_AREAS,
    TEMP_HISTORY_DAYS,
    TEMP_LAT,
    TEMP_LON,
)
from .db import get_conn, meta_get
from .logger import log


# ── Helpers ──────────────────────────────────────────────────────────────────

import json as _json

def _price_area_filter() -> str:
    """Build the JSON filter string for all configured price areas."""
    return _json.dumps({"PriceArea": ALL_PRICE_AREAS})

# Production is DK-only
_DK_FILTER = '{"PriceArea":["DK1","DK2"]}'


def _rsum(record: dict, *cols: str) -> float | None:
    """Sum named fields from a record dict, treating None as 0."""
    total = sum(float(record[c]) for c in cols if record.get(c) is not None)
    return total if total != 0.0 else None


def _fv(record: dict, col: str | None) -> float | None:
    v = record.get(col) if col else None
    return float(v) if v is not None else None


def _pick(keys: set[str], *candidates: str) -> str | None:
    for c in candidates:
        if c in keys:
            return c
    return None


# ── Spot prices ──────────────────────────────────────────────────────────────

def _rows_from_elspot(recs: list[dict]) -> list[tuple]:
    rows = []
    for r in recs:
        ts = strip_z(r.get("HourUTC"))
        if ts:
            rows.append((ts, r.get("PriceArea"),
                         r.get("SpotPriceEUR"), r.get("SpotPriceDKK")))
    return rows


def _rows_from_dayahead(recs: list[dict]) -> list[tuple]:
    if not recs:
        return []
    sample = recs[0]
    keys = list(sample.keys())
    log.info("DayAheadPrices keys: %s", keys)

    ts_field = next(
        (k for k in keys if "UTC" in k
         and any(x in k.lower() for x in ("time", "hour", "quarter", "15", "minute"))),
        None,
    )
    if not ts_field:
        ts_field = next((k for k in keys if "UTC" in k), keys[0])
    eur_field = next((k for k in keys if "eur" in k.lower()), None)
    dkk_field = next((k for k in keys if "dkk" in k.lower()), None)
    log.info("DayAheadPrices: ts=%s eur=%s dkk=%s  (native 15-min)", ts_field, eur_field, dkk_field)

    rows = []
    for r in recs:
        ts = strip_z(r.get(ts_field, ""))
        if not ts:
            continue
        area = r.get("PriceArea", "")
        eur = float(r[eur_field]) if eur_field and r.get(eur_field) is not None else None
        dkk = float(r[dkk_field]) if dkk_field and r.get(dkk_field) is not None else None
        rows.append((ts, area, eur, dkk))
    return rows


def fetch_prices(full: bool = False) -> None:
    """Fetch spot prices from Elspotprices (legacy) + DayAheadPrices."""
    end_utc = datetime.now(timezone.utc) + timedelta(days=2)
    all_rows: list[tuple] = []

    if full:
        # Part 1 – Elspotprices (up to Sep 30 2025) in 5-year chunks
        log.info("Prices backfill part 1: Elspotprices (5-year chunks)…")
        start_year = datetime.now(timezone.utc).year - HISTORY_YEARS
        elspot_end = datetime.fromisoformat(ELSPOT_CUTOFF).replace(tzinfo=timezone.utc)

        # Build 5-year chunks
        chunks = []
        y = start_year
        while True:
            chunk_start = datetime(y, 1, 1, tzinfo=timezone.utc)
            chunk_end = datetime(min(y + 5, elspot_end.year + 1), 1, 1, tzinfo=timezone.utc)
            if chunk_start >= elspot_end:
                break
            chunk_end = min(chunk_end, elspot_end)
            chunks.append((chunk_start, chunk_end))
            y += 5

        for i, (h_start, h_end) in enumerate(chunks, 1):
            label = f"chunk {i}/{len(chunks)}: {h_start.year}–{h_end.year}"
            log.info("  %s: %s → %s", label,
                     h_start.strftime("%Y-%m-%d"), h_end.strftime("%Y-%m-%d"))
            try:
                chunk = fetch_pages(
                    "Elspotprices",
                    h_start.strftime("%Y-%m-%dT%H:%M"),
                    h_end.strftime("%Y-%m-%dT%H:%M"),
                    {"filter": _price_area_filter(), "sort": "HourUTC ASC"},
                )
                log.info("    → %d records", len(chunk))
                all_rows.extend(_rows_from_elspot(chunk))
            except Exception as exc:
                log.error("  %s failed: %s", label, exc)

        # Part 2 – DayAheadPrices (Oct 2025 → now)
        log.info("Prices backfill part 2: DayAheadPrices (Oct 2025 → now)…")
        try:
            chunk = fetch_pages(
                "DayAheadPrices",
                ELSPOT_CUTOFF[:16],
                end_utc.strftime("%Y-%m-%dT%H:%M"),
                {"filter": _price_area_filter()},
            )
            log.info("  DayAheadPrices → %d raw records", len(chunk))
            all_rows.extend(_rows_from_dayahead(chunk))
        except Exception as exc:
            log.error("  DayAheadPrices failed: %s", exc)
    else:
        # Incremental – last 72 h of DayAheadPrices
        inc_start = (datetime.now(timezone.utc) - timedelta(hours=72)).strftime("%Y-%m-%dT%H:%M")
        log.info("Prices incremental: DayAheadPrices from %s", inc_start)
        try:
            chunk = fetch_pages(
                "DayAheadPrices",
                inc_start,
                end_utc.strftime("%Y-%m-%dT%H:%M"),
                {"filter": _price_area_filter()},
            )
            log.info("  DayAheadPrices → %d raw records", len(chunk))
            all_rows.extend(_rows_from_dayahead(chunk))
        except Exception as exc:
            log.error("  DayAheadPrices incremental failed: %s", exc)

    if not all_rows:
        log.warning("Prices: no rows to store")
        return

    valid_ts = sorted(r[0] for r in all_rows if r[0])
    newest_ts = valid_ts[-1] if valid_ts else None

    db = get_conn()
    db.executemany(
        "INSERT OR REPLACE INTO spot_prices(ts_utc,price_area,price_eur,price_dkk)"
        " VALUES(?,?,?,?)",
        all_rows,
    )
    if newest_ts:
        db.execute("INSERT OR REPLACE INTO meta VALUES('price_latest',?)", (newest_ts,))
    db.commit()
    db.close()
    log.info("Prices: upserted %d rows, price_latest=%s", len(all_rows), newest_ts)


# ── Production (hourly settlement) ──────────────────────────────────────────

def fetch_production(full: bool = False) -> None:
    latest = meta_get("prod_latest")
    end_utc = datetime.now(timezone.utc)
    if latest and not full:
        start_utc = datetime.fromisoformat(latest) - timedelta(hours=48)
    else:
        start_utc = end_utc - timedelta(days=365 * HISTORY_YEARS)

    recs = fetch_pages(
        "ProductionConsumptionSettlement",
        start_utc.strftime("%Y-%m-%dT%H:%M"),
        end_utc.strftime("%Y-%m-%dT%H:%M"),
        {"filter": _DK_FILTER},
    )
    if not recs:
        return

    log.info("Prod API keys: %s", list(recs[0].keys()))
    keys = set(recs[0].keys())

    offshore_cols = [k for k in keys if "offshorewind" in k.lower() or "windoffshore" in k.lower()]
    onshore_cols = [k for k in keys if "onshorewind" in k.lower() or "windonshore" in k.lower()]
    solar_cols = [k for k in keys if "solar" in k.lower() and "selfcon" not in k.lower()]
    central_col = _pick(keys, "CentralPowerMWh", "CentralPower")
    decentral_col = _pick(keys, "LocalPowerMWh", "DecentralPowerMWh", "CommercialPowerMWh", "DecentralPower")
    consumption_col = _pick(keys, "GrossConsumptionMWh", "GrossConsumption")
    ex_no_col = _pick(keys, "ExchangeNO_MWh", "ExchangeNO", "ExchangeNorway")
    ex_se_col = _pick(keys, "ExchangeSE_MWh", "ExchangeSE", "ExchangeSweden")
    ex_de_col = _pick(keys, "ExchangeGE_MWh", "ExchangeGE", "ExchangeGermany", "ExchangeDE_MWh")

    log.info("  offshore=%s onshore=%s solar=%s central=%s decentral=%s",
             offshore_cols, onshore_cols, solar_cols, central_col, decentral_col)
    log.info("  consumption=%s ex_no=%s ex_se=%s ex_de=%s",
             consumption_col, ex_no_col, ex_se_col, ex_de_col)

    rows = []
    for r in recs:
        offshore = _rsum(r, *offshore_cols) if offshore_cols else None
        onshore = _rsum(r, *onshore_cols) if onshore_cols else None
        solar = _rsum(r, *solar_cols) if solar_cols else None
        rows.append((
            strip_z(r.get("HourUTC")), r.get("PriceArea"),
            _fv(r, central_col), _fv(r, decentral_col),
            onshore, offshore, solar,
            _fv(r, consumption_col),
            _fv(r, ex_no_col), _fv(r, ex_se_col), _fv(r, ex_de_col),
        ))

    db = get_conn()
    db.executemany(
        "INSERT OR REPLACE INTO production"
        "(ts_utc,price_area,central_mw,decentral_mw,onshore_mw,offshore_mw,"
        " solar_mw,consumption_mw,ex_no,ex_se,ex_de) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        rows,
    )
    last_ts = next((r[0] for r in reversed(rows) if r[0]), None)
    if last_ts:
        db.execute("INSERT OR REPLACE INTO meta VALUES('prod_latest',?)", (last_ts,))
    db.commit()
    db.close()
    log.info("Production: upserted %d rows", len(rows))


# ── Realtime 5-min ───────────────────────────────────────────────────────────

def fetch_realtime() -> None:
    now = datetime.now(timezone.utc)
    recs = fetch_pages(
        "ElectricityProdex5MinRealtime",
        (now - timedelta(days=10)).strftime("%Y-%m-%dT%H:%M"),
        now.strftime("%Y-%m-%dT%H:%M"),
        {"filter": _DK_FILTER},
    )
    if not recs:
        return

    keys = set(recs[0].keys())
    central_col = _pick(keys, "ProductionGe100MW", "CentralPowerMWh", "CentralPower")
    decentral_col = _pick(keys, "ProductionLt100MW", "LocalPowerMWh", "DecentralPower")
    onshore_col = _pick(keys, "OnshoreWindPower", "OnshoreWindMWh")
    offshore_col = _pick(keys, "OffshoreWindPower", "OffshoreWindMWh")
    solar_col = _pick(keys, "SolarPower", "SolarMWh")
    ex_no_col = _pick(keys, "ExchangeNorway", "ExchangeNO_MWh", "ExchangeNO")
    ex_se_col = _pick(keys, "ExchangeSweden", "ExchangeSE_MWh", "ExchangeSE")
    ex_de_col = _pick(keys, "ExchangeGermany", "ExchangeGE_MWh", "ExchangeGE", "ExchangeDE")

    log.info("RT keys: %s", list(recs[0].keys()))

    rows = [(
        strip_z(r.get("Minutes5UTC") or r.get("Minutes5DK")),
        r.get("PriceArea"),
        _fv(r, central_col), _fv(r, decentral_col),
        _fv(r, onshore_col), _fv(r, offshore_col),
        _fv(r, solar_col), None,
        _fv(r, ex_no_col), _fv(r, ex_se_col), _fv(r, ex_de_col),
    ) for r in recs]

    db = get_conn()
    db.executemany(
        "INSERT OR REPLACE INTO production"
        "(ts_utc,price_area,central_mw,decentral_mw,onshore_mw,offshore_mw,"
        " solar_mw,consumption_mw,ex_no,ex_se,ex_de) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        rows,
    )
    db.commit()
    db.close()
    log.info("Realtime: upserted %d rows", len(rows))


# ── Gas prices (CSV import) ──────────────────────────────────────────────────

def load_gas_prices_from_csv(csv_path: Path | None = None) -> None:
    # Search multiple locations for the gas CSV
    candidates = [
        csv_path,
        GAS_CSV_PATH,
        Path("dk_power") / GAS_CSV_PATH.name,
        Path(__file__).parent / GAS_CSV_PATH.name,
    ]
    found = None
    for c in candidates:
        if c and c.exists():
            found = c
            break
    if not found:
        log.warning("Gas price CSV not found in any location: %s",
                    [str(c) for c in candidates if c])
        return

    db = get_conn()
    count = db.execute("SELECT COUNT(*) FROM gas_prices").fetchone()[0]
    # Always reimport if the CSV is newer than what we have
    # (just count-based check for simplicity)
    csv_lines = sum(1 for _ in open(found, encoding="utf-8-sig")) - 1
    if count >= csv_lines and count > 100:
        log.info("Gas prices already loaded (%d rows, CSV has %d), skipping", count, csv_lines)
        db.close()
        return
    if count > 0:
        log.info("Gas prices incomplete or updated (%d rows, CSV has %d), reimporting", count, csv_lines)
        db.execute("DELETE FROM gas_prices")
        db.commit()

    rows: list[tuple] = []
    with open(found, "r", encoding="utf-8-sig") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            try:
                dt = datetime.strptime(row["Date"].strip(), "%m/%d/%Y")
                price = float(row["Price"].replace(",", ""))
                rows.append((dt.strftime("%Y-%m-%d"), price))
            except Exception as exc:
                log.warning("Skipping gas price row: %s", exc)

    if rows:
        db.executemany(
            "INSERT OR REPLACE INTO gas_prices (date, price_eur) VALUES (?, ?)", rows
        )
        db.commit()
        log.info("Loaded %d gas price records from %s", len(rows), found)
    db.close()


# ── Temperature (Open-Meteo) ────────────────────────────────────────────────

def fetch_temperature_data() -> None:
    db = get_conn()
    count = db.execute("SELECT COUNT(*) FROM temperature").fetchone()[0]
    last_date = db.execute("SELECT MAX(date) FROM temperature").fetchone()[0]

    # If we have substantial data and it's recent, skip
    if count > 365 and last_date:
        last_dt = datetime.fromisoformat(last_date)
        days_old = (datetime.now(timezone.utc) - last_dt.replace(tzinfo=timezone.utc)).days
        if days_old < 7:
            log.info("Temperature data fresh (%d records, latest %s), skipping", count, last_date)
            db.close()
            return

    db.close()

    end_date = datetime.now(timezone.utc).date()
    start_date = end_date - timedelta(days=TEMP_HISTORY_DAYS)

    # Fetch in 2-year chunks to avoid API timeouts
    chunk_days = 730
    current_start = start_date
    total_fetched = 0

    while current_start < end_date:
        current_end = min(current_start + timedelta(days=chunk_days), end_date)

        url = "https://archive-api.open-meteo.com/v1/archive"
        params = {
            "latitude": TEMP_LAT,
            "longitude": TEMP_LON,
            "start_date": current_start.isoformat(),
            "end_date": current_end.isoformat(),
            "daily": "temperature_2m_mean",
            "timezone": "UTC",
        }

        try:
            resp = requests.get(url, params=params, timeout=60)
            resp.raise_for_status()
            data = resp.json()

            daily = data.get("daily", {})
            dates = daily.get("time", [])
            temps = daily.get("temperature_2m_mean", [])
            temp_data = [(d, t) for d, t in zip(dates, temps) if t is not None]

            if temp_data:
                db = get_conn()
                db.executemany(
                    "INSERT OR REPLACE INTO temperature (date, temp_avg_c) VALUES (?, ?)",
                    temp_data,
                )
                db.commit()
                db.close()
                total_fetched += len(temp_data)
        except Exception as exc:
            log.error("Temperature fetch failed for %s→%s: %s",
                      current_start.isoformat(), current_end.isoformat(), exc)

        current_start = current_end + timedelta(days=1)

    if total_fetched:
        log.info("Fetched %d temperature records from Open-Meteo (%s → %s)",
                 total_fetched, start_date.isoformat(), end_date.isoformat())
    else:
        log.warning("No temperature data fetched")

    db.close()