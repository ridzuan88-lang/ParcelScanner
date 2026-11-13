/**
 * PARCEL SCANNER BACKEND  (Kaicom K7)
 *
 * Google Sheet tabs "RPU" and "RTS" - each has EXACTLY three columns:
 *   A Date (dd/MM/yyyy)   B DateTime (dd/MM/yyyy HH:mm:ss)   C Tracking Number (plain text)
 *
 * Timestamps are created HERE (server side) in Asia/Kuala_Lumpur and stored as real
 * date / date-time values. New scans are always appended; nothing is ever overwritten.
 */

// ====== CONFIGURE ======
var SECRET_KEY     = 'CHANGE-THIS-SECRET';  // must match "Secret key" in the app (Settings)
var SPREADSHEET_ID = '';                    // leave '' when this script is opened from the sheet
                                            // (Extensions > Apps Script); otherwise paste the sheet ID
// =======================

var TIMEZONE     = 'Asia/Kuala_Lumpur';
var TABS         = ['RPU', 'RTS'];
var HEADERS      = ['Date', 'DateTime', 'Tracking Number'];
var DATE_FMT     = 'dd/MM/yyyy';
var DATETIME_FMT = 'dd/MM/yyyy HH:mm:ss';
var TRACKING_RE  = /^[A-Z0-9][A-Z0-9\-]{5,39}$/;   // keep in sync with ScanValidator.java

/** Browser / "Test connection" button. */
function doGet(e) {
  var p = (e && e.parameter) || {};
  if (p.action === 'ping') {
    if (p.key !== SECRET_KEY) return out({status: 'UNAUTHORIZED', message: 'Wrong secret key'});
    try {
      var ss = getSS();
      setupSheets(ss);
      return out({status: 'OK', message: 'Connected to "' + ss.getName() + '" - tabs RPU and RTS are ready'});
    } catch (err) {
      return out({status: 'ERROR', message: String(err)});
    }
  }
  return out({status: 'OK', message: 'Parcel Scanner backend is running'});
}

/** One scan from the app. */
function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    var req = JSON.parse(e.postData.contents);
    if (req.key !== SECRET_KEY) return out({status: 'UNAUTHORIZED', message: 'Wrong secret key'});

    var mode = String(req.mode || '').toUpperCase();
    var tracking = String(req.tracking || '').replace(/[\u0000-\u001f]/g, '').trim().toUpperCase();
    var scanId = String(req.scanId || '');

    if (TABS.indexOf(mode) < 0) return out({status: 'INVALID', message: 'Mode must be RPU or RTS'});
    if (!TRACKING_RE.test(tracking)) return out({status: 'INVALID', message: 'Invalid tracking number'});

    lock.waitLock(25000);   // one scan at a time -> no overwritten / skipped rows

    // A retry of the same scan (app never got our reply) gets the original answer back.
    var cache = CacheService.getScriptCache();
    var cacheKey = scanId ? 'scan_' + scanId : '';
    if (cacheKey) {
      var hit = cache.get(cacheKey);
      if (hit) return out(JSON.parse(hit));
    }

    var ss = getSS();
    var sheet = ensureSheet(ss, mode);
    var result;

    var existingRow = findRow(sheet, tracking);   // duplicate check on column C only
    if (existingRow > 0) {
      result = {
        status: 'DUPLICATE', mode: mode, tracking: tracking,
        date: sheet.getRange(existingRow, 1).getDisplayValue(),
        dateTime: sheet.getRange(existingRow, 2).getDisplayValue(),
        message: 'Already recorded in ' + mode + ' (row ' + existingRow + ')'
      };
    } else {
      var now = new Date();
      var dateOnly = Utilities.parseDate(Utilities.formatDate(now, TIMEZONE, 'yyyy-MM-dd'), TIMEZONE, 'yyyy-MM-dd');
      var row = Math.max(sheet.getLastRow(), 1) + 1;          // next free row, never overwrites
      var rng = sheet.getRange(row, 1, 1, 3);
      rng.setNumberFormats([[DATE_FMT, DATETIME_FMT, '@']]);  // '@' = plain text for the tracking number
      rng.setValues([[dateOnly, now, tracking]]);
      SpreadsheetApp.flush();
      result = {
        status: 'OK', mode: mode, tracking: tracking, row: row,
        date: Utilities.formatDate(now, TIMEZONE, 'dd/MM/yyyy'),
        dateTime: Utilities.formatDate(now, TIMEZONE, 'dd/MM/yyyy HH:mm:ss'),
        message: 'Saved to ' + mode
      };
    }
    if (cacheKey) cache.put(cacheKey, JSON.stringify(result), 21600);
    return out(result);
  } catch (err) {
    return out({status: 'ERROR', message: String(err)});
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}

/** Run this once from the editor (Run > setup) to create / check both tabs. */
function setup() {
  var ss = getSS();
  setupSheets(ss);
  Logger.log('Ready: ' + ss.getName());
}

// ---------------------------------------------------------------- helpers

function getSS() {
  return SPREADSHEET_ID ? SpreadsheetApp.openById(SPREADSHEET_ID) : SpreadsheetApp.getActiveSpreadsheet();
}

function setupSheets(ss) {
  if (ss.getSpreadsheetTimeZone() !== TIMEZONE) ss.setSpreadsheetTimeZone(TIMEZONE);
  TABS.forEach(function (name) { ensureSheet(ss, name); });
}

function ensureSheet(ss, name) {
  var sheet = ss.getSheetByName(name);
  if (!sheet) sheet = ss.insertSheet(name);

  var head = sheet.getRange(1, 1, 1, 3);
  var vals = head.getValues()[0];
  var empty = vals.every(function (v) { return v === '' || v === null; });
  if (empty) {
    head.setValues([HEADERS]).setFontWeight('bold');
    sheet.setFrozenRows(1);
  } else if (String(vals[0]) !== HEADERS[0] || String(vals[1]) !== HEADERS[1] || String(vals[2]) !== HEADERS[2]) {
    throw new Error('Tab "' + name + '": row 1 must be exactly: ' + HEADERS.join(', '));
  }
  var maxRows = sheet.getMaxRows();
  if (maxRows > 1) {
    sheet.getRange(2, 1, maxRows - 1, 1).setNumberFormat(DATE_FMT);
    sheet.getRange(2, 2, maxRows - 1, 1).setNumberFormat(DATETIME_FMT);
    sheet.getRange(2, 3, maxRows - 1, 1).setNumberFormat('@');
  }
  return sheet;
}

/** Row number of the tracking number in column C (header excluded), or 0. */
function findRow(sheet, tracking) {
  var last = sheet.getLastRow();
  if (last < 2) return 0;
  var found = sheet.getRange(2, 3, last - 1, 1)
      .createTextFinder(tracking).matchCase(false).matchEntireCell(true).findNext();
  return found ? found.getRow() : 0;
}

function out(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
