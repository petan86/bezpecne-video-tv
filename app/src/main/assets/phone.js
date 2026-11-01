'use strict';
const byId = id => document.getElementById(id);
const urlInput = byId('url'), preview = byId('preview'), status = byId('status');
const supplied = location.hash.slice(1);
let token = /^[A-Za-z0-9_-]{32}$/.test(supplied) ? supplied : '';
try {
    if (token) sessionStorage.setItem('parentSession', token);
    else token = sessionStorage.getItem('parentSession') || '';
} catch (_) { }
history.replaceState(null, '', '/');
let selectedId = null;
function message(text, kind = '') { status.textContent = text; status.className = kind; }
async function api(path, data) {
    const abort = new AbortController();
    const timeout = setTimeout(() => abort.abort(), 15000);
    try {
        const response = await fetch(path, {method: 'POST', headers: {
            'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token
        }, body: JSON.stringify(data), signal: abort.signal, cache: 'no-store'});
        const result = await response.json();
        if (!response.ok) throw new Error(result.error || 'Požadavek se nepodařilo dokončit.');
        return result;
    } catch (error) {
        if (error instanceof TypeError || error.name === 'AbortError')
            throw new Error('TV neodpovídá. Zkontroluj stejnou Wi-Fi a otevřený QR kód na TV. Po vypršení přístupu naskenuj nový kód.');
        throw error;
    } finally { clearTimeout(timeout); }
}
byId('lookup').addEventListener('submit', async event => {
    event.preventDefault();
    if (!token) return;
    preview.hidden = true; selectedId = null;
    byId('previewButton').disabled = true; urlInput.disabled = true;
    message('Načítám video…');
    try {
        const data = await api('/api/preview', {url: urlInput.value.trim()});
        selectedId = data.id;
        byId('thumbnail').src = 'https://i.ytimg.com/vi/' + selectedId + '/mqdefault.jpg';
        byId('title').value = data.title || '';
        byId('title').disabled = false; byId('recipients').disabled = false;
        byId('author').textContent = data.author || '';
        byId('add').disabled = false; byId('another').hidden = true;
        preview.hidden = false;
        message(data.warning || 'Zkontroluj video a vyber, komu ho chceš povolit.');
        preview.scrollIntoView({behavior: 'smooth', block: 'start'});
    } catch (error) { message(error.message, 'error'); }
    finally { byId('previewButton').disabled = false; urlInput.disabled = false; }
});
urlInput.addEventListener('input', () => { selectedId = null; preview.hidden = true; message(''); });
byId('add').addEventListener('click', async () => {
    if (!selectedId) return;
    const profile = document.querySelector('input[name=profile]:checked').value;
    const names = {matysek: 'Matýskovi', filipek: 'Filípkovi', both: 'Matýskovi i Filípkovi'};
    byId('add').disabled = true; byId('previewButton').disabled = true; urlInput.disabled = true;
    byId('title').disabled = true; byId('recipients').disabled = true;
    message('Ukládám do TV…');
    try {
        await api('/api/add', {id: selectedId, title: byId('title').value.trim(), profile});
        message('✓ Video je povolené ' + names[profile] + '.\nNa TV zavři QR kód a otevři profil dítěte. Video najdeš v kategorii Vše.', 'success');
        byId('another').hidden = false;
    } catch (error) {
        message(error.message, 'error'); byId('add').disabled = false;
        byId('title').disabled = false; byId('recipients').disabled = false;
    }
    finally { byId('previewButton').disabled = false; urlInput.disabled = false; }
});
byId('another').addEventListener('click', () => {
    selectedId = null; preview.hidden = true; urlInput.value = ''; message(''); urlInput.focus();
});
function itemRow(profile, type, item) {
    const row = document.createElement('div'); row.className = 'item';
    const label = document.createElement('span'); label.textContent = item.title || item.id;
    const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = 'Odebrat';
    remove.addEventListener('click', async () => {
        remove.disabled = true;
        try { await api('/api/remove', {profile, type, id: item.id}); await loadManaged(); }
        catch (error) { message(error.message, 'error'); remove.disabled = false; }
    });
    row.append(label, remove); return row;
}
async function loadManaged() {
    if (!token) return;
    const root = byId('managed'); byId('refresh').disabled = true;
    try {
        const data = await api('/api/list', {}); root.textContent = '';
        for (const [profile, name] of [['matysek', 'Matýsek'], ['filipek', 'Filípek']]) {
            const heading = document.createElement('h3'); heading.textContent = name; root.append(heading);
            const list = document.createElement('div'); list.className = 'profile-list';
            const entries = [...(data[profile]?.videos || []).map(x => ['video', x]),
                ...(data[profile]?.channels || []).map(x => ['channel', x])];
            if (!entries.length) { const empty = document.createElement('p'); empty.textContent = 'Žádné ručně přidané položky.'; list.append(empty); }
            for (const [type, item] of entries) list.append(itemRow(profile, type, item));
            root.append(list);
        }
    } catch (error) { message(error.message, 'error'); }
    finally { byId('refresh').disabled = false; }
}
byId('refresh').addEventListener('click', loadManaged);
if (!token) {
    byId('previewButton').disabled = true;
    message('Nejprve v rodičovské správě na TV otevři „Přidat video z mobilu“ a naskenuj QR kód.', 'error');
} else loadManaged();
