const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const html = fs.readFileSync('app/src/main/assets/phone.html', 'utf8');
const script = fs.readFileSync('app/src/main/assets/phone.js', 'utf8');
function setup(hasToken = true) {
    const nodes = new Map([...html.matchAll(/id="([^"]+)"/g)].map(match => [match[1], {
        value: '', hidden: true, disabled: false, events: {},
        addEventListener(event, fn) { this.events[event] = fn; },
        scrollIntoView() {}, focus() {}
    }]));
    const selectedProfile = {value: 'filipek'}, requests = [], stored = new Map();
    let fail = false;
    const context = vm.createContext({document: {getElementById: id => nodes.get(id), querySelector: () => selectedProfile},
        location: {hash: hasToken ? '#' + 'a'.repeat(32) : ''}, history: {replaceState() {}},
        sessionStorage: {setItem: (k, v) => stored.set(k, v), getItem: k => stored.get(k)},
        setTimeout, clearTimeout, AbortController, TypeError,
        fetch: async (path, options) => {
            requests.push({path, ...options});
            if (fail) throw new TypeError('network');
            return {ok: true, json: async () => path.endsWith('preview')
                ? {id: '16LuvR2CARA', title: 'Test title', author: '<script>not executed</script>'} : {ok: true}};
        }
    });
    vm.runInContext(script, context);
    return {nodes, requests, fail: () => fail = true};
}
(async () => {
    const t = setup();
    t.nodes.get('url').value = 'https://youtu.be/16LuvR2CARA';
    await t.nodes.get('lookup').events.submit({preventDefault() {}});
    assert.equal(t.nodes.get('preview').hidden, false);
    assert.equal(t.nodes.get('title').value, 'Test title');
    assert.equal(t.nodes.get('author').textContent, '<script>not executed</script>');
    assert.equal(t.requests[0].headers.Authorization, 'Bearer ' + 'a'.repeat(32));
    await t.nodes.get('add').events.click();
    assert.equal(JSON.parse(t.requests[1].body).profile, 'filipek');
    assert.match(t.nodes.get('status').textContent, /Filípkovi/);
    t.nodes.get('another').events.click();
    assert.equal(t.nodes.get('url').value, '');
    assert.equal(t.nodes.get('preview').hidden, true);
    await t.nodes.get('add').events.click();
    assert.equal(t.requests.length, 2, 'reset must not submit previous video');
    const missing = setup(false);
    assert.equal(missing.nodes.get('previewButton').disabled, true);
    const offline = setup(); offline.fail();
    await offline.nodes.get('lookup').events.submit({preventDefault() {}});
    assert.match(offline.nodes.get('status').textContent, /TV neodpovídá/);
    assert.equal(offline.nodes.get('previewButton').disabled, false);
    console.log('Phone UI tests passed: preview, explicit profile, authorization, reset, missing session, network error.');
})().catch(error => { console.error(error); process.exitCode = 1; });
