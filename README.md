# Minimal Web Renderer

A WebView that renders one link and goes nowhere.

## Why

Uninstalling every browser breaks Android in an annoying way: links from SMS, QR
codes, and share sheets fail with "could not find appropriate application."

This registers for `http`/`https` so links resolve, without being a browser. No
address bar, no tabs, no history, no bookmarks, no launcher entry — and links on
the page don't work. Another app hands it a URL, it renders that URL, and that's
the end. Every page is a dead end by design.

Built for a deliberately distraction-free phone.

## Install

Use [Obtainium](https://github.com/ImranR98/Obtainium) and point it at this
repo's releases — it'll handle updates. (Release coming; nothing published yet.)

Or build it yourself (needs `podman`, nothing else):

```bash
./build.sh
adb install -r dist/chrisincode-render.apk
```

## Notes

Server redirects are allowed — login walls and captive portals need them.
Everything else is refused: `intent://` URLs are never parsed, non-web schemes are
dropped, popups and downloads are off, camera/mic/location are denied without
asking, bad certificates end the page, and there's no `addJavascriptInterface`
anywhere. JavaScript is on, because otherwise most pages are blank.

Apache-2.0.
