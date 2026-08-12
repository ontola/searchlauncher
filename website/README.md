# Marketing site (GitHub Pages)

Published from this folder by [`.github/workflows/deploy-website.yml`](../.github/workflows/deploy-website.yml).

**Live URL:** https://searchlauncher.eu/

## One-time GitHub setup

1. Repo **Settings → Pages → Build and deployment → Source: GitHub Actions**
2. Under **Custom domain**, set `searchlauncher.eu` (creates/uses `website/CNAME`)
3. After DNS verifies, enable **Enforce HTTPS**

## DNS at your registrar (Openprovider)

Do **not** change NS records. Keep Openprovider nameservers. Add these records in the DNS zone for `searchlauncher.eu`:

| Type | Name | Value |
|------|------|-------|
| `A` | `@` | `185.199.108.153` |
| `A` | `@` | `185.199.109.153` |
| `A` | `@` | `185.199.110.153` |
| `A` | `@` | `185.199.111.153` |
| `AAAA` | `@` | `2606:50c0:8000::153` |
| `AAAA` | `@` | `2606:50c0:8001::153` |
| `AAAA` | `@` | `2606:50c0:8002::153` |
| `AAAA` | `@` | `2606:50c0:8003::153` |
| `CNAME` | `www` | `ontola.github.io` |

Propagation is often minutes, sometimes up to 24–48h. Then click **DNS check** / **Enforce HTTPS** in Pages settings.

## Draft files (not deployed)

`COPY.md`, `specimen.html`, `README.md`
