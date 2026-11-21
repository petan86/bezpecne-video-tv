'use strict';
const byId = id => document.getElementById(id);
const urlInput = byId('url'), preview = byId('preview'), status = byId('status');
const localSession = '__LAN_SESSION_TOKEN__';
const supplied = /^[A-Za-z0-9_-]{32}$/.test(localSession) ? localSession : location.hash.slice(1);
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
            throw new Error('TV neodpovídá. Zkontroluj domácí síť a spuštěnou aplikaci v TV. Potom obnov tuto stránku.');
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
    const names = {matysek: 'Matýskovi', filipek: 'Filípkovi', hudba: 'v Hudbě', both: 'Matýskovi i Filípkovi'};
    byId('add').disabled = true; byId('previewButton').disabled = true; urlInput.disabled = true;
    byId('title').disabled = true; byId('recipients').disabled = true;
    message('Ukládám do TV…');
    try {
        await api('/api/add', {id: selectedId, title: byId('title').value.trim(), profile});
        message('✓ Video je povolené ' + names[profile] + '.\nNa TV otevři znovu profil dítěte. Video najdeš v kategorii Vše.', 'success');
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
    const tile = document.createElement('div'); tile.className = 'tile';
    const name = item.title || item.id;
    let thumb;
    if (type === 'video' && /^[A-Za-z0-9_-]{11}$/.test(item.id)) {
        thumb = document.createElement('img');
        thumb.src = 'https://i.ytimg.com/vi/' + item.id + '/mqdefault.jpg';
        thumb.alt = ''; thumb.loading = 'lazy'; thumb.referrerPolicy = 'no-referrer';
    } else {
        thumb = document.createElement('div'); thumb.className = 'thumb'; thumb.textContent = '📺';
    }
    tile.append(thumb);
    if (type === 'channel') {
        const kind = document.createElement('span'); kind.className = 'kind'; kind.textContent = 'Kanál';
        tile.append(kind);
    }
    const label = document.createElement('div'); label.className = 'name'; label.title = name;
    const text = document.createElement('span'); text.textContent = name; label.append(text);
    const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'remove';
    remove.textContent = '×'; remove.setAttribute('aria-label', 'Odebrat: ' + name); remove.title = 'Odebrat';
    remove.addEventListener('click', async () => {
        if (!confirm('Odebrat „' + name + '“?')) return;
        remove.disabled = true; tile.classList.add('busy');
        try { await api('/api/remove', {profile, type, id: item.id}); await loadManaged(); }
        catch (error) { message(error.message, 'error'); remove.disabled = false; tile.classList.remove('busy'); }
    });
    tile.append(label, remove); return tile;
}
function subheading(text) { const h = document.createElement('h4'); h.textContent = text; return h; }
function emptyNote(text) { const p = document.createElement('p'); p.className = 'empty'; p.textContent = text; return p; }
function channelCard(profile, item) {
    const card = document.createElement('div'); card.className = 'channel';
    const name = item.title || item.id;
    const icon = document.createElement('span'); icon.className = 'icon'; icon.textContent = '📺';
    const info = document.createElement('div'); info.className = 'info';
    const title = document.createElement('div'); title.className = 'title'; title.textContent = name; title.title = name;
    info.append(title);
    if (/^UC[A-Za-z0-9_-]{22}$/.test(item.id)) {
        const link = document.createElement('a'); link.href = 'https://www.youtube.com/channel/' + item.id;
        link.target = '_blank'; link.rel = 'noopener noreferrer'; link.textContent = 'Otevřít na YouTube';
        info.append(link);
    }
    card.append(icon, info);
    {
        const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'remove';
        remove.textContent = '×'; remove.setAttribute('aria-label', 'Odebrat kanál: ' + name); remove.title = 'Odebrat kanál';
        remove.addEventListener('click', async () => {
            if (!confirm('Odebrat kanál „' + name + '“? Videa z tohoto kanálu se přestanou dětem nabízet.')) return;
            remove.disabled = true; card.classList.add('busy');
            try { await api('/api/remove', {profile, type: 'channel', id: item.id}); await loadManaged(); }
            catch (error) { message(error.message, 'error'); remove.disabled = false; card.classList.remove('busy'); }
        });
        card.append(remove);
    }
    return card;
}
async function loadManaged() {
    if (!token) return;
    const root = byId('managed'); byId('refresh').disabled = true;
    try {
        const data = await api('/api/list', {}); root.textContent = '';
        for (const [profile, name] of [['matysek', 'Matýsek'], ['filipek', 'Filípek'], ['hudba', 'Hudba']]) {
            const heading = document.createElement('h3'); heading.textContent = name; root.append(heading);
            const channels = data[profile]?.channels || [], videos = data[profile]?.videos || [];
            root.append(subheading('Povolené kanály (' + channels.length + ')'));
            const channelList = document.createElement('div'); channelList.className = 'channel-list';
            if (!channels.length) channelList.append(emptyNote('Žádné povolené kanály.'));
            for (const item of channels) channelList.append(channelCard(profile, item));
            root.append(channelList);
            root.append(subheading('Povolená videa (' + videos.length + ')'));
            const list = document.createElement('div'); list.className = 'profile-list';
            if (!videos.length) list.append(emptyNote('Žádná ručně povolená videa.'));
            for (const item of videos) list.append(itemRow(profile, 'video', item));
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
