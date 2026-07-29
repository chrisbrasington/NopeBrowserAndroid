# Minimal Web Renderer

A WebView that renders one page from a hyperlink, with no navigation.

## Why

The goal is a phone with no access to algorithmic feeds, that still works when
you're out in the world.

Everything else stays: maps, transit, music, podcasts, ebooks, camera, notes,
messages, banking, tickets. Those are tools — you open them for a reason and
close them when you're done. What's gone is anything with an infinite feed
choosing what you see next.

A browser undoes all of that. Every social site is still there at a URL, so
removing the feed apps accomplishes nothing while a browser is installed. So the
browsers go too.

That breaks the phone in one specific way: links stop working. A ticket texted to
you, a QR code on a parking meter, a confirmation from a restaurant — all fail
with "could not find appropriate application." Android needs *something*
registered for `http`/`https`.

This is that something. It renders the one page it was handed and goes nowhere:
no address bar, no tabs, no history, no bookmarks, and links on the page don't
work. Enough to read a ticket. Not enough to browse.

It does show up in the app list, because an app that handles every link on your
phone but can't be found anywhere is its own kind of confusing. Opening it that
way just says so.

## Install

Use [Obtainium](https://github.com/ImranR98/Obtainium) and point it at this
repo's releases — it'll handle updates. 

Or build it yourself, `podman` is the only requirement:

```bash
./build.sh
adb install -r dist/chrisincode-render.apk
```

## Notes

Server redirects are allowed, since login walls and captive portals need them.
Everything else is refused: `intent://` URLs are never parsed, non-web schemes are
dropped, popups and downloads are off, camera/mic/location are denied without
asking, bad certificates end the page, and there's no `addJavascriptInterface`
anywhere. JavaScript is on, because otherwise most pages are blank.

Apache-2.0.
