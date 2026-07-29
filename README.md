# NopeBrowser (Minimal Web Renderer)

Renders one page from a hyperlink. No navigation.

## Why

If you've trimmed down your phone to be distraction-free by removing 
social media apps and bad actors, a browser becomes a sort of 
backdoor to addiction. And if you remove all browsers from android,
you might still want to read one article or see one e-ticket delivery page
(image a QR code at a restaurant or museum).

This browser does let you open one hyperlink (there is no nav bar!), but that's it. 
It blocks navigation. And perhaps more importantly, you can compile it 
with a blacklist of your own (default blacklist is reddit). 
It's important that this is at compile time because you cannot alter 
this functionality on the device itself.

The idea is: it is your job to curate the apps you find acceptable on your
device and this browser lets you minimally access the internet (say a link
from a friend) with a level you control.

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
adb install -r dist/NopeBrowser-1.1.apk
```

## Notes

Server redirects are allowed; login walls and captive portals need them.
Otherwise: `intent://` never parsed, non-web schemes dropped, no popups or
downloads, camera/mic/location denied, bad certificates end the page, no
`addJavascriptInterface`. JavaScript is on, or most pages are blank.

Apache-2.0.
