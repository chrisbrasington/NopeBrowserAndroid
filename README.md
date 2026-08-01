# NopeBrowser (Minimal Web Renderer)

Renders one page from a hyperlink. No navigation, except on domains you
whitelist at compile time.

## Why

If you've trimmed down your phone to be distraction-free by removing 
social media apps and bad actors, a browser becomes a sort of 
backdoor to addiction. And if you remove all browsers from android,
you might still want to read one article or see one e-ticket delivery page
(image a QR code at a restaurant or museum).

This browser does let you open one hyperlink (there is no nav bar!), but that's it. 
It blocks navigation. And perhaps more importantly, you can compile it 
with a blacklist of your own (default blacklist is reddit), and a whitelist of 
sites you're fine spending time on, which do get an address bar and working links. 
It's important that both are at compile time because you cannot alter 
this functionality on the device itself.

The idea is: it is your job to curate the apps you find acceptable on your
device and this browser lets you minimally access the internet (say a link
from a friend) with a level you control.

## Blocklist

`app/src/main/res/values/blocklist.xml` — one domain per line, subdomains
included. Links to anything on it get a "Nope" toast and don't open. Ships with
`reddit.com`.

## Whitelist

The other end of the same idea: domains you trust yourself with. On a whitelisted
page an address bar appears and links work, across the domain and its subdomains.

Leave the whitelist and the normal behaviour comes back — that page renders once,
the address bar disappears, and nothing on it goes anywhere. One step out costs
one page. Typing an off-whitelist URL into the address bar does the same thing.
The blocklist still wins over everything.

Opening the app from the app list lists the whitelisted domains as links. Tap one
and you're browsing it; ignore them and the countdown closes the app as it always
did. That's still the only way in that doesn't start with a link — there's no
field to type a URL into until you're on a whitelisted page.

Empty whitelist means no address bar ever and no links on the notice screen,
which is the app as it was.

`app/src/main/res/values/whitelist.xml` holds a sample. To build with your own
list instead of committing it:

```bash
NOPE_WHITELIST="example.com,example.org" ./build.sh
```

## Install

[Obtainium](https://github.com/ImranR98/Obtainium), pointed at this repo's
releases.

Or build it — `podman` is the only requirement:

```bash
./build.sh
adb install -r dist/NopeBrowser-1.2.apk
```

## Notes

Server redirects are allowed; login walls and captive portals need them.
Otherwise: `intent://` never parsed, non-web schemes dropped, no popups or
downloads, camera/mic/location denied, bad certificates end the page, no
`addJavascriptInterface`. JavaScript is on, or most pages are blank.

Apache-2.0.
