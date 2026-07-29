# NopeBrowser (Minimal Web Renderer)

Renders one page from a hyperlink. No navigation.

## Why

A phone with no algorithmic feeds that still works out in the world.

Maps, transit, music, podcasts, ebooks, camera, notes, messages, banking,
tickets — all fine. Tools you open for a reason and close. Feeds, no.

A browser puts every feed back one URL away, so the browsers go too. That breaks
links: texted tickets, QR codes, confirmations all fail with "could not find
appropriate application."

This registers for `http`/`https` so they work. It renders the page it was handed
and goes nowhere — no address bar, tabs, history, or working links. Enough to read
a ticket. Not enough to browse.

It shows in the app list, and says so if you open it.

## Blocklist

`app/src/main/res/values/blocklist.xml` — one domain per line, subdomains
included. Links to anything on it get a "Nope" toast and don't open. Ships with
`reddit.com`.

## Install

[Obtainium](https://github.com/ImranR98/Obtainium), pointed at this repo's
releases.

Or build it — `podman` is the only requirement:

```bash
./build.sh
adb install -r dist/chrisincode-render.apk
```

## Notes

Server redirects are allowed; login walls and captive portals need them.
Otherwise: `intent://` never parsed, non-web schemes dropped, no popups or
downloads, camera/mic/location denied, bad certificates end the page, no
`addJavascriptInterface`. JavaScript is on, or most pages are blank.

Apache-2.0.
