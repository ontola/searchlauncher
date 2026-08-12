# Marketing site (GitHub Pages)

Published from this folder by [`.github/workflows/deploy-website.yml`](../.github/workflows/deploy-website.yml).

**Live URL:** https://searchlauncher.eu/

## One-time GitHub setup

**Required:** Repo **Settings → Pages → Build and deployment → Source: GitHub Actions**.

If Source is still **Deploy from a branch** (`main` / `/`), GitHub Jekyll-publishes the
repo root and https://searchlauncher.eu/ shows the README — not `website/`. The
Deploy website workflow will also fail until Source is GitHub Actions.

Then:
1. Custom domain: `searchlauncher.eu` (uses `website/CNAME`)
2. **Actions → Deploy website → Run workflow** (or push a `website/` change)
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
