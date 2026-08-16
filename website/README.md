# Glyph website

Landing page for [glyphmc.net](https://glyphmc.net): logo hero, Anarchy +
Forever World cards (rules + copy-address), **How to join**, **Map**, and
**Discord**. `/map/` is a full-page viewer that loads the live BlueMap at
[map.glyphmc.net](https://map.glyphmc.net) (Cloudflare Tunnel, not Pages).

**Live**

| URL | Notes |
| --- | --- |
| https://glyphmc.net | Production custom domain |
| https://www.glyphmc.net | www → same Pages project |
| https://glyph-5ev.pages.dev | Cloudflare Pages default |

## Local preview

```powershell
cd website
npx --yes serve .
```

## Redeploy

From repo root (Wrangler OAuth already used once on this machine):

```powershell
# Do not set CLOUDFLARE_API_TOKEN here — the User DNS token lacks Pages write.
Remove-Item Env:CLOUDFLARE_API_TOKEN -ErrorAction SilentlyContinue
npx --yes wrangler pages deploy website --project-name=glyph --commit-dirty=true
```

Static HTML/CSS/JS only — no build step. Apex DNS is a proxied CNAME to
`glyph-5ev.pages.dev`. Keep `GLYPH_DNS_RECORD=play.glyphmc.net` only so DDNS
never overwrites the site apex.
