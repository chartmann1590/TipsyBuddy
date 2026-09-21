const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadApp(fetch) {
  const context = vm.createContext({
    fetch,
    console: { error() {} },
    document: { getElementById: () => ({}), addEventListener() {} },
    URLSearchParams,
    window: { location: { search: '' } }
  });
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../public/app.js'), 'utf8'), context);
  return context;
}

test('a check-in wave preserves the existing session fields', async () => {
  const stored = { latitude: { doubleValue: 40.7128 }, userName: { stringValue: 'Alex' } };
  const app = loadApp(async (url, request) => {
    assert.equal(new URL(url).searchParams.get('updateMask.fieldPaths'), 'cheerSentAt');
    assert.equal(request.method, 'PATCH');
    const { fields } = JSON.parse(request.body);
    assert.deepEqual(Object.keys(fields), ['cheerSentAt']);
    assert.ok(Number.isFinite(Date.parse(fields.cheerSentAt.stringValue)));
    Object.assign(stored, fields);
    return { ok: true };
  });
  assert.equal(await app.sendWave('tb-test'), true);
  assert.equal(stored.latitude.doubleValue, 40.7128);
  assert.equal(stored.userName.stringValue, 'Alex');
});

test('wave failures are reported instead of claiming success', async () => {
  for (const fetch of [async () => ({ ok: false }), async () => { throw new Error('offline'); }]) {
    assert.equal(await loadApp(fetch).sendWave('tb-test'), false);
  }
});

test('session identifiers are escaped in wave requests', async () => {
  const app = loadApp(async url => {
    assert.ok(url.includes('/sessions/test%2Fid%3Fother%3Dvalue?'));
    assert.deepEqual([...new URL(url).searchParams.keys()], ['updateMask.fieldPaths']);
    return { ok: true };
  });
  assert.equal(await app.sendWave('test/id?other=value'), true);
});

test('session refresh reads the status, location, and sharing deadline', async () => {
  const app = loadApp(async () => ({ ok: true, json: async () => ({ fields: {
    status: { stringValue: 'Heading home' },
    latitude: { doubleValue: 40.7128 }, longitude: { doubleValue: -74.006 },
    expiresAt: { stringValue: '2026-09-21T03:00:00Z' }
  } }) }));
  const session = await app.fetchSessionData('tb-test');
  assert.equal(session.status, 'Heading home');
  assert.equal(session.latitude, 40.7128);
  assert.equal(session.longitude, -74.006);
  assert.equal(session.expiresAt, '2026-09-21T03:00:00Z');
});
