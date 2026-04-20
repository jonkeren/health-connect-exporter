#!/usr/bin/env python3
"""
import-activities.py
Reads Google Fit session JSONs + TCX GPS files, outputs per-activity JSON files
and an index.json for the Health Connect Dashboard.
"""

import os
import re
import json
import glob
import sys
from datetime import datetime, timezone, timedelta
import xml.etree.ElementTree as ET

BASE_DIR   = '/opt/health-dashboard'
SESSIONS_DIR = os.path.join(BASE_DIR, 'fit-takeout', 'Alle sessies')
TCX_DIR      = os.path.join(BASE_DIR, 'fit-takeout', 'Activiteiten')
OUT_DIR      = os.path.join(BASE_DIR, 'activities')

os.makedirs(OUT_DIR, exist_ok=True)

SKIP_TYPES = {'sleep', 'in_vehicle', 'still', 'unknown', 'other'}

# ── TCX namespace ─────────────────────────────────────────────────────────────
NS = {'tcx': 'http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2'}

def parse_offset(offset_str):
    """Parse offset like +02:00 or +0200 into timedelta."""
    offset_str = offset_str.strip()
    sign = 1 if offset_str[0] == '+' else -1
    offset_str = offset_str[1:].replace(':', '')
    h = int(offset_str[:2])
    m = int(offset_str[2:4]) if len(offset_str) >= 4 else 0
    return timedelta(hours=h, minutes=m) * sign

def filename_to_utc(filename):
    """
    Extract UTC datetime from TCX / session JSON filename.
    Format: 2018-04-08T12_53_33.348+02_00_...
    We normalise underscores in time part → colons/colon, then parse.
    """
    basename = os.path.basename(filename)
    # Grab the datetime prefix (everything up to the first underscore after the timezone part)
    # Pattern: YYYY-MM-DDTHH_MM_SS[.sss][+|-]HH_MM
    m = re.match(
        r'^(\d{4}-\d{2}-\d{2}T\d{2}_\d{2}_\d{2}(?:\.\d+)?)([\+\-])(\d{2})_(\d{2})',
        basename
    )
    if not m:
        return None
    dt_part   = m.group(1).replace('_', ':')  # YYYY-MM-DDTHH:MM:SS[.sss]
    sign      = m.group(2)
    tz_h      = m.group(3)
    tz_m      = m.group(4)
    tz_str    = f"{sign}{tz_h}:{tz_m}"

    try:
        # Python 3.7+ fromisoformat doesn't support +HH:MM with fractional seconds on older builds
        # so we parse manually
        dt_str = f"{dt_part}{sign}{tz_h}:{tz_m}"
        # Handle fractional seconds
        if '.' in dt_part:
            fmt_base, frac = dt_part.split('.', 1)
        else:
            fmt_base = dt_part
            frac = None

        naive_str = dt_part  # YYYY-MM-DDTHH:MM:SS[.fraction]
        offset = parse_offset(tz_str)
        if frac:
            naive = datetime.strptime(naive_str, '%Y-%m-%dT%H:%M:%S.%f')
        else:
            naive = datetime.strptime(naive_str, '%Y-%m-%dT%H:%M:%S')
        utc_dt = naive - offset
        utc_dt = utc_dt.replace(tzinfo=timezone.utc)
        return utc_dt
    except Exception as e:
        return None

def iso_to_utc(iso_str):
    """Parse ISO8601 string (with Z or offset) to UTC datetime."""
    if not iso_str:
        return None
    iso_str = iso_str.strip()
    if iso_str.endswith('Z'):
        iso_str = iso_str[:-1] + '+00:00'
    # Python 3.7+
    try:
        dt = datetime.fromisoformat(iso_str)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(timezone.utc)
    except Exception:
        return None

def make_id(utc_dt):
    return utc_dt.strftime('%Y-%m-%dT%H-%M-%S')

def parse_tcx(tcx_path):
    """Parse TCX file, return list of trackpoints with lat/lng (required)."""
    try:
        tree = ET.parse(tcx_path)
        root = tree.getroot()
    except Exception as e:
        print(f"  [WARN] Cannot parse TCX {os.path.basename(tcx_path)}: {e}", file=sys.stderr)
        return []

    trackpoints = []
    for tp in root.iter('{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}Trackpoint'):
        pos_el = tp.find('{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}Position')
        if pos_el is None:
            continue
        lat_el = pos_el.find('{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}LatitudeDegrees')
        lng_el = pos_el.find('{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}LongitudeDegrees')
        if lat_el is None or lng_el is None:
            continue
        try:
            lat = float(lat_el.text)
            lng = float(lng_el.text)
        except (TypeError, ValueError):
            continue

        time_el = tp.find('{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}Time')
        time_str = time_el.text if time_el is not None else None

        alt_el = tp.find('{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}AltitudeMeters')
        alt = float(alt_el.text) if alt_el is not None and alt_el.text else None

        dist_el = tp.find('{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}DistanceMeters')
        dist = float(dist_el.text) if dist_el is not None and dist_el.text else None

        hr_el = tp.find('.//{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}HeartRateBpm/{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}Value')
        hr = int(float(hr_el.text)) if hr_el is not None and hr_el.text else None

        trackpoints.append({
            'lat': lat,
            'lng': lng,
            'alt': round(alt, 1) if alt is not None else None,
            'time': time_str,
            'distanceMeters': round(dist, 2) if dist is not None else None,
            'heartRate': hr
        })

    return trackpoints

# ── Build TCX index: utc_dt → tcx_path ───────────────────────────────────────
print("Building TCX index...", flush=True)
tcx_index = {}  # key: (year,month,day,hour,min,sec) tuple → path

tcx_files = glob.glob(os.path.join(TCX_DIR, '*.tcx'))
print(f"Found {len(tcx_files)} TCX files", flush=True)

for tcx_path in tcx_files:
    utc_dt = filename_to_utc(os.path.basename(tcx_path))
    if utc_dt is None:
        continue
    # Key: truncate to seconds
    key = utc_dt.replace(microsecond=0)
    tcx_index[key] = tcx_path

print(f"Indexed {len(tcx_index)} TCX files", flush=True)

# ── Process session JSONs ─────────────────────────────────────────────────────
print("\nProcessing session JSONs...", flush=True)
session_files = glob.glob(os.path.join(SESSIONS_DIR, '*.json'))
print(f"Found {len(session_files)} session files", flush=True)

activities = []
gps_count = 0
skipped = 0
errors = 0

METRIC_MAP = {
    'com.google.calories.expended': 'calories',
    'com.google.step_count.delta': 'steps',
    'com.google.distance.delta': 'distanceMeters',
    'com.google.speed.summary': 'avgSpeedMs',
    'com.google.active_minutes': 'activeMinutes',
}

for i, session_path in enumerate(session_files):
    if i > 0 and i % 500 == 0:
        print(f"  {i}/{len(session_files)} processed ({gps_count} with GPS)...", flush=True)

    try:
        with open(session_path, 'r', encoding='utf-8') as f:
            session = json.load(f)
    except Exception as e:
        errors += 1
        continue

    activity_type = (session.get('fitnessActivity') or '').lower()
    if activity_type in SKIP_TYPES or not activity_type:
        skipped += 1
        continue

    start_str = session.get('startTime')
    end_str   = session.get('endTime')
    dur_str   = session.get('duration', '0s')

    start_utc = iso_to_utc(start_str)
    if start_utc is None:
        errors += 1
        continue

    # Duration: "1606.690s"
    dur_s = 0.0
    if dur_str:
        m2 = re.match(r'([\d.]+)s', dur_str)
        if m2:
            dur_s = float(m2.group(1))

    # Aggregate metrics
    agg_vals = {'calories': 0, 'steps': 0, 'distanceMeters': 0, 'avgSpeedMs': 0, 'activeMinutes': 0}
    for agg in session.get('aggregate', []):
        metric = agg.get('metricName', '')
        field = METRIC_MAP.get(metric)
        if field:
            val = agg.get('floatValue') or agg.get('intValue') or 0
            agg_vals[field] = float(val)

    # ID
    act_id = make_id(start_utc)

    # Check output file already exists (resume support)
    out_path = os.path.join(OUT_DIR, f'activity_{act_id}.json')

    # Try to match TCX
    tcx_key = start_utc.replace(microsecond=0)
    tcx_path = tcx_index.get(tcx_key)

    # If not exact match, try within ±2 seconds
    if tcx_path is None:
        for delta_s in range(1, 3):
            for sign in (1, -1):
                candidate = tcx_key.replace(second=(tcx_key.second + sign * delta_s) % 60)
                if candidate in tcx_index:
                    tcx_path = tcx_index[candidate]
                    break
            if tcx_path:
                break

    trackpoints = []
    has_gps = False
    if tcx_path:
        trackpoints = parse_tcx(tcx_path)
        has_gps = len(trackpoints) > 0
        if has_gps:
            gps_count += 1

    activity = {
        'id': act_id,
        'type': activity_type,
        'startTime': start_utc.isoformat().replace('+00:00', 'Z'),
        'endTime': iso_to_utc(end_str).isoformat().replace('+00:00', 'Z') if iso_to_utc(end_str) else None,
        'durationSeconds': round(dur_s),
        'distanceMeters': round(agg_vals['distanceMeters'], 2),
        'calories': round(agg_vals['calories'], 1),
        'steps': int(agg_vals['steps']),
        'avgSpeedMs': round(agg_vals['avgSpeedMs'], 4),
        'hasGps': has_gps,
        'trackpoints': trackpoints,
    }

    # Write individual file
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(activity, f, separators=(',', ':'))

    # Add to index (without trackpoints)
    index_entry = {k: v for k, v in activity.items() if k != 'trackpoints'}
    activities.append(index_entry)

print(f"\nDone processing: {len(activities)} activities, {gps_count} with GPS, {skipped} skipped (sleep/etc), {errors} errors", flush=True)

# Sort newest first
activities.sort(key=lambda a: a['startTime'], reverse=True)

# Write index
index_path = os.path.join(OUT_DIR, 'index.json')
with open(index_path, 'w', encoding='utf-8') as f:
    json.dump(activities, f, separators=(',', ':'))

print(f"Written index.json with {len(activities)} entries", flush=True)
print(f"Output dir: {OUT_DIR}", flush=True)
