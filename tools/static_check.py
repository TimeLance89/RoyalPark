#!/usr/bin/env python3
"""SDK-freie Projektstrukturprüfung. Kein Ersatz für Gradle-/Gerätetests."""
import pathlib
import re
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
for xml in root.glob('app/src/main/**/*.xml'):
    ET.parse(xml)
java_dir = root / 'app/src/main/java/app/royalpark/mobile'
required = ['MainActivity', 'ParkingStore', 'ParkingSpot', 'LocationGateway',
            'AutoParkService', 'ParkingAlarmReceiver', 'AlarmScheduler', 'ParkingLogic']
for name in required:
    file = java_dir / f'{name}.java'
    assert file.is_file(), f'Missing {name}'
    text = file.read_text()
    assert re.search(rf'\bclass\s+{name}\b', text), f'Missing class declaration: {name}'
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text()
for permission in ['ACCESS_FINE_LOCATION', 'BLUETOOTH_CONNECT', 'FOREGROUND_SERVICE_LOCATION']:
    assert permission in manifest
assert 'com.appcelerate.findmycar.android' not in manifest
assert 'allowBackup="false"' in manifest
print('Project static checks passed')
