# Website Audit — dmitrylipinskiy.com

**Audited:** 23 August 2026
**Scope:** Full audit — performance, SEO, accessibility, mobile/responsive, content, security headers, HTML validity
**Stack detected:** WordPress + PHP + MySQL, Cloudflare CDN, HTTP/3, Rank Math PRO (SEO), Gravity Forms, Responsive Menu Pro, Slick slider
**Primary conversion goal:** Book a Calendly call ("BOOK YOUR CALL" → `calendly.com/d/cx35-pjz-2dm/directorii-assistance`)

---

## 1. Executive summary

The site is well-positioned strategically: clear single conversion goal, strong personal brand, good SEO metadata hygiene from Rank Math, and a fast origin server (18 ms TTFB behind Cloudflare). The problems are almost entirely in the **delivery layer** — the page ships 4.4 MB and 149 requests on mobile, most of it third-party JavaScript that isn't needed for the first screen.

| Area | Grade | Headline metric |
|---|---|---|
| Performance (mobile) | **F — 36/100** | LCP 18.9 s, FCP 8.1 s, TBT 910 ms |
| Security headers | **F** | All 6 recommended headers missing |
| Accessibility | **D** | 10+ images with no `alt`, broken form label, empty heading |
| HTML validity | **C** | 40+ W3C errors incl. duplicate `id="header"` |
| SEO (technical) | **B+** | Good meta/OG/schema/sitemap; thin content, stale blog |
| Content & conversion | **C+** | Contradictory social numbers, dead nav item, 2024 copyright |
| Mobile/responsive | **B** | Viewport correct, CLS = 0, but load time kills it |

**The single most valuable fix:** lazy-load the YouTube embed and defer reCAPTCHA. Those two third parties alone account for ~1.6 MB and ~790 ms of main-thread time on a page where neither is needed until the user scrolls or interacts.

---

## 2. Performance — critical

Lighthouse mobile (moto g power emulation, Chrome 152):

| Metric | Value | Target | Status |
|---|---|---|---|
| Performance score | 36 / 100 | ≥ 90 | 🔴 |
| First Contentful Paint | 8.1 s | < 1.8 s | 🔴 |
| **Largest Contentful Paint** | **18.9 s** | < 2.5 s | 🔴 |
| Total Blocking Time | 910 ms | < 200 ms | 🔴 |
| Speed Index | 8.1 s | < 3.4 s | 🔴 |
| Cumulative Layout Shift | 0 | < 0.1 | 🟢 |
| Server response (TTFB) | 18 ms | < 600 ms | 🟢 |
| Total page weight | 4,389 KiB | < 1,500 KiB | 🔴 |
| Requests | 149 | < 60 | 🔴 |
| Main-thread work | 10.4 s | < 4 s | 🔴 |
| JS execution time | 6.2 s | < 2 s | 🔴 |

The `/work-with-me/` page scores 64/100 with a 13.3 s LCP and 2,479 KiB — better, still failing.

### 2.1 Resource breakdown

| Type | Requests | Transfer |
|---|---:|---:|
| Images | 56 | 2,433 KiB |
| Scripts | 41 | 1,544 KiB |
| Stylesheets | 23 | 178 KiB |
| Fonts | 10 | 160 KiB |
| **Third-party** | **61** | **2,022 KiB (46%)** |

### 2.2 Third-party cost (the main problem)

| Entity | Transfer | Main-thread |
|---|---:|---:|
| YouTube embed | 1,119 KiB | 515 ms |
| Google reCAPTCHA (gstatic) | 491 KiB | 275 ms |
| Google Tag Manager (×2 tags) | 290 KiB | 85 ms |
| Google Fonts | 266 KiB | 0 ms |
| Google APIs / recaptcha anchor | 143 KiB | 59 ms |
| Google Analytics (analytics.js) | 22 KiB | 13 ms |

**Fixes, in order of payoff:**

1. **Lazy-load the YouTube embed** (saves ~1.1 MB + 515 ms). Replace the two `<iframe>` embeds with a click-to-play façade — a static `sddefault.webp` thumbnail plus a play button that swaps in the iframe on click. Libraries: `lite-youtube-embed`, or WP Rocket / Perfmatters "Replace YouTube iframe with preview image". Note you currently embed the **same video twice** (`L3gDzw72AjY` appears in two iframes) — remove the duplicate.
2. **Defer reCAPTCHA** (saves ~630 KiB + ~334 ms). The newsletter form sits far below the fold, yet reCAPTCHA v2 loads on page load on *every* page. Load `api.js` only on first focus of the form, or switch to reCAPTCHA v3 / hCaptcha / a honeypot for a simple email opt-in. A newsletter field rarely justifies a 630 KiB anti-bot payload.
3. **Remove the duplicate analytics stack.** You are running Universal Analytics (`UA-218807492-1`, 134 KiB), GA4 (`G-DC8E72G4V5`, 156 KiB) *and* legacy `analytics.js`. **UA stopped processing data in July 2024** — it is dead weight collecting nothing. Delete the UA tag and `analytics.js`; keep GA4 only. Saves ~155 KiB and ~58 ms immediately.
4. **Kill render-blocking resources** (est. 1,360 ms). 26 blocking files, including 9 render-blocking `<script>` tags in `<head>` (jQuery, jQuery Migrate, slick, counterup, waypoints, select2, flatpickr, noscroll, custom.js). None of these are needed before first paint — add `defer` to all of them. The theme's `style.css` (180 KiB uncompressed) and `wp-user-avatar/frontend.min.css` (100 KiB) cost 1,076 ms each.
5. **Optimise images** (est. 1,048 KiB). Everything is JPEG/PNG; nothing is WebP or AVIF. Worst offenders:
   - `0ed2824f-…-scaled-e1762371346968.jpg` — **551 KiB**
   - `Hero_bg-4.jpg` — **390 KiB** (this is your LCP element)
   - `68373729-…-scaled-e1762369845363.jpg` — 124 KiB
   Convert to WebP, serve `srcset`, and add `fetchpriority="high"` to the hero — Lighthouse explicitly flags *"fetchpriority=high should be applied"* on the LCP request.
6. **Trim the gallery slider.** The homepage slider loads **30 unique 414×414 photos, each duplicated 2–3× in the DOM** (~90 `<img>` tags for 30 images). Render only the first 3–4 eagerly and lazy-load the rest; deduplicate the clones with the slider's own `slidesToShow`/clone settings rather than repeating markup.
7. **Self-host fonts.** 266 KiB across 3 Google Fonts families (Barlow, Barlow Condensed, IBM Plex Sans) from a third-party origin. Note a **173 KiB Roboto woff2** is also being pulled in by the YouTube embed — killed automatically by fix #1. Self-host with `font-display: swap` and subset to Latin.
8. **Unused JavaScript: 1,694 KiB.** Most of it dies with fixes 1–3. Also check `wp-user-avatar` (ProfilePress) — it ships select2 + flatpickr + a 100 KiB stylesheet on the public homepage where no avatar/profile UI exists. Dequeue it from the front end.
9. **Cache policy: 68 KiB of savings** flagged on short-lived assets. Cloudflare is serving the HTML well (`cf-cache-status: HIT`, `max-age=2678400`), so this is minor.

**Realistic outcome:** fixes 1–5 alone should take page weight from 4.4 MB to roughly 1.2–1.5 MB and LCP from 18.9 s into the 3–4 s range.

---

## 3. Security headers — critical

`securityheaders.com` grade: **F**. Every recommended header is absent.

| Header | Status | Recommended value |
|---|---|---|
| `Strict-Transport-Security` | ❌ | `max-age=31536000; includeSubDomains; preload` |
| `Content-Security-Policy` | ❌ | Start in `Content-Security-Policy-Report-Only` mode |
| `X-Frame-Options` | ❌ | `SAMEORIGIN` |
| `X-Content-Type-Options` | ❌ | `nosniff` |
| `Referrer-Policy` | ❌ | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | ❌ | `geolocation=(), microphone=(), camera=()` |

All six can be added in about ten minutes via a Cloudflare Transform Rule (Rules → Transform Rules → Modify Response Header) with no WordPress changes and no deploy risk. Do HSTS last and start with a short `max-age` so you can back out. Missing `X-Frame-Options` is the most pressing — without it the site can be framed for clickjacking, which matters on a page whose whole purpose is driving clicks to a booking link.

**Also:** `http://www.dmitrylipinskiy.com/` returns **403 Forbidden** from Cloudflare, while `https://www.dmitrylipinskiy.com/` 301-redirects correctly. Anyone typing the bare `www` address without a scheme may hit a hard error page. Fix the `www` + HTTP path in Cloudflare so all four host/scheme combinations funnel to `https://dmitrylipinskiy.com/`.

---

## 4. Accessibility

Confirmed via W3C validator source inspection (Lighthouse's a11y gatherer crashed on this page — itself a symptom of how heavy the JS is).

### High severity

1. **Images missing `alt` entirely** — the entire footer is unlabelled. A screen reader announces these as bare filenames:
   - Footer logo `dmitry-lipinskiy_logo-footer.png`
   - All 5 social icons: `icon-fb-grey.svg`, `icon-insta-gray.svg`, `icon-youtube-gray.svg`, `icon-twitter-grey.svg`, `icon-linkedin-grey.svg`
   - `email-icon.svg`
   These are inside `<a>` tags with no other text, so the *links themselves* have no accessible name — a WCAG 2.1 **4.1.2 Name, Role, Value** failure. Give each one `alt="Dmitry Lipinskiy on Facebook"` etc.
2. **Broken form label.** `<label for='input_1_4'>` on the newsletter form points at an element that isn't a visible form control (it's the CAPTCHA wrapper). Screen readers will announce an orphan label.
3. **Gallery images have empty `alt=""`.** All ~30 slider photos are marked decorative. If they're purely decorative that's technically valid — but a gallery of Dmitry at events is *content*, and it's also 30 missed SEO signals. Add real descriptions.
4. **Nav links with no `href`.** Three `<a>` elements ("Videos", twice in desktop nav, once in mobile) have no `href` attribute, so they are **not keyboard focusable** and cannot be reached by Tab. Use `<button>` for a dropdown toggle, or give the anchor a real destination.

### Medium severity

5. **Duplicate `id="header"`** — two elements share the ID. Breaks `aria-labelledby`/`aria-controls` targeting and skip-links.
6. **Empty heading** `<h2 class="title"></h2>` — screen readers announce a heading with no content; it also pollutes the document outline.
7. **Sections without headings** — `#gallery-slider` and `#social-media-status` are `<section>` elements with no accessible name.
8. **`target=""`** on two links (empty string is invalid; browsers may treat it unpredictably).
9. **Contrast not verifiable automatically** — please spot-check the white-on-photo hero text ("Meet your contractor concierge") manually. Text over a photographic background is the classic 4.5:1 failure; add a semi-transparent overlay if needed.

---

## 5. SEO

### Working well 🟢

- Clean, descriptive `<title>`: *"Dmitry Lipinskiy | Contractor Referrals"* (39 chars)
- Meta description present and well-written, 128 chars
- `rel=canonical` correct
- Full Open Graph + Twitter Card set, including `og:image:width/height/alt`
- JSON-LD schema: `Person`/`Organization`, `WebSite` with `SearchAction`, `WebPage`, `ImageObject`
- `robots.txt` valid, points to `sitemap_index.xml`
- Sitemaps present and current (`page-sitemap.xml` updated 2026-06-16)
- `<html lang="en-US">` set
- HTTP/3, Cloudflare CDN, 18 ms TTFB
- Custom 404 page returns a proper *"Page Not Found | Dmitry Lipinskiy"* template

### Issues 🔴

1. **Core Web Vitals are a ranking factor and you are failing all but CLS.** An 18.9 s LCP is the biggest SEO liability on the site. Everything in §2 is also SEO work.
2. **The blog is 5 years stale.** `post-sitemap.xml` contains exactly **7 posts, all from April–July 2021**, none updated since. For a self-described industry educator this is a large missed opportunity and a weak E-E-A-T signal. Either publish regularly or de-emphasise `/blog/` in the nav.
3. **Duplicate/orphan pages in the sitemap.** `/blog/` (index) and `/blog-new/` (a single post's full text, titled *"Blog New"*) both exist and are both indexable. `/blog-new/` is a leftover draft page — it duplicates the *"Losing is not an Option"* post verbatim. Redirect it to the real post or `noindex` it. Same question for `/zoom-coaching-session/` (last touched 2021) and `/coaching-phone-call/` (2023) — both appear to be superseded by `/work-with-me/`.
4. **`/thank-you/` is in the sitemap and indexable.** Conversion confirmation pages should be `noindex` — otherwise they can rank, and they pollute goal tracking.
5. **The `Events` nav link is broken.** `/events/` is in the main navigation on every page but is **not in the sitemap**, 301-redirects back to the homepage, and is blocked to crawlers. You are advertising a section that doesn't exist. Either build it or remove the nav item.
6. **Thin homepage content.** Roughly 90 words of body copy for the primary landing page of a referral business. Google has very little to work with. Consider adding: how the concierge process works (3 steps), what a homeowner gets, service areas, an FAQ block (also unlocks `FAQPage` schema).
7. **No `Service`, `LocalBusiness` or `FAQPage` schema.** You have a physical address (16650 Bass Lake Rd, Maple Grove, MN 55311) and a defined service — both are structured-data opportunities that can win rich results. Also add `sameAs` links (YouTube, Facebook, Instagram, X, LinkedIn) to the `Person` node; they're in the footer but not in the schema.
8. **PDFs blocked in robots.txt** (`Disallow: /*.pdf`). Intentional? If any are lead magnets or media kits, this hides them from search.

---

## 6. HTML validity

W3C Nu validator on the homepage: **40+ errors**, plus many warnings.

| Error | Count | Note |
|---|---:|---|
| `<img>` missing required `alt` | 10 | Footer + social icons — also the a11y issue above |
| `width="100%"` on `<img>` | ~25 | Invalid; `width` takes an integer. Use CSS |
| `width="100%"`/`height="100%"` on `<iframe>` | 4 | Same problem |
| `<a>` missing `href` | 3 | Keyboard-inaccessible nav items |
| Duplicate `id="header"` | 1 | |
| `<style>` as a direct child of `<div>` | 1 | Not permitted in that context |
| `target=""` empty value | 2 | |
| `autocomplete="off"` on `type="hidden"` | 2 | Gravity Forms bug — vendor-side |
| `<label for>` → non-existent control | 1 | |
| Obsolete `frameborder` on iframe | 2 | Use CSS |
| Obsolete `align` on `<p>` | 1 | Use CSS |
| Empty heading | 1 | |

The `width="100%"` pattern appears ~29 times and is a single find-and-replace in the theme's gallery/video templates — move it to CSS (`.gallery img { width: 100%; }`). Note that *unsized* images are also a CLS risk; the right fix is real intrinsic `width`/`height` attributes plus CSS `width: 100%; height: auto`.

---

## 7. Content, UX & trust

1. **Your social proof contradicts itself on the same screen.** The stats carousel shows two slides with different numbers for the same platforms:
   - Slide 1: YouTube 119,000+ · Facebook 85,000+ · Instagram 61,900+
   - Slide 2: YouTube 44,030+ · Facebook 31,450+ · Instagram 22,903+

   Meanwhile the embedded YouTube player reports the channel's real count as **150K subscribers**. Three different numbers for one channel, all visible in one session, on a page whose entire premise is *trust*. Fix immediately: one slide, current figures, ideally auto-pulled.
2. **Copyright reads "© Copyright 2024"** — two years stale. Make it dynamic (`date('Y')`).
3. **Duplicate video embed.** The same YouTube video (`L3gDzw72AjY`, *"7 Roofing Scams to Avoid in 2026"*) is embedded twice on the homepage. Almost certainly a template bug, and it doubles the heaviest third-party cost on the page.
4. **`Events` nav item goes nowhere** (see §5.5). A dead link in primary navigation on a lead-gen site is a direct conversion leak.
5. **Nav has an insecure/inconsistent link.** "Directorii" in the Brands menu points to `http://contractors.directorii.com/` (plain HTTP) while the same brand is linked as `https://directorii.com/` in body copy and footer. Use HTTPS and pick one canonical destination.
6. **Menu overload.** The Services dropdown has 8 items mixing homeowner and contractor intents (Book Free Homeowner Call, Rebranding Services, QuickBooks For Dummies, 2 Days Workshop, Buy/Sell Roofing Business, Merch) and links out to 6 different domains including a raw Stripe checkout and a Kajabi checkout. The homepage hero targets **homeowners**; the nav targets **contractors**. Split the audiences — a homeowner who came for a referral shouldn't be one click from a QuickBooks course checkout.
7. **Newsletter form asks for a CAPTCHA on an email field.** High friction, low value, 630 KiB cost. Drop to a honeypot.
8. **No testimonials or proof of outcomes** on the homepage. For a referral/concierge service, homeowner testimonials and the "$30K protection on every job" guarantee (currently only mentioned on `/work-with-me/`) are the strongest trust assets you have and they're missing from the landing page.
9. **`/work-with-me/` buries the lede.** It's the money page, and the Directorii $30K guarantee is in the last paragraph of the About section. Move proof points up.

---

## 8. Prioritised action plan

### 🔥 Do this week — low effort, high impact
| # | Action | Effort | Impact |
|---|---|---|---|
| 1 | Lazy-load / façade the YouTube embeds; delete the duplicate | S | −1.1 MB, −515 ms |
| 2 | Remove the dead UA tag + `analytics.js`; keep GA4 only | XS | −155 KiB |
| 3 | Add the 6 security headers via Cloudflare Transform Rule | S | F → A |
| 4 | Fix the contradictory follower counts | XS | Trust |
| 5 | Add `alt` to the 10 footer/social images | XS | WCAG 4.1.2 |
| 6 | Fix or remove the dead `Events` nav link | XS | Conversion leak |
| 7 | Dynamic copyright year | XS | Polish |
| 8 | Fix `http://www.` 403 | S | Availability |

### ⚡ Next 2–4 weeks — the performance rebuild
| # | Action | Effort | Impact |
|---|---|---|---|
| 9 | Defer/replace reCAPTCHA on the newsletter | M | −630 KiB, −334 ms |
| 10 | `defer` all 9 head scripts; inline critical CSS | M | −1,360 ms |
| 11 | WebP conversion + `srcset` + `fetchpriority="high"` on hero | M | −1,048 KiB |
| 12 | Deduplicate & lazy-load the 30-image gallery slider | M | Large |
| 13 | Dequeue `wp-user-avatar` assets from the front end | S | −120 KiB |
| 14 | Self-host and subset fonts | S | −266 KiB |
| 15 | Fix the `<a href>`, duplicate ID, empty heading, form label | S | A11y + validity |

### 📈 Next quarter — content & SEO
| # | Action | Effort | Impact |
|---|---|---|---|
| 16 | `noindex` `/thank-you/`; redirect `/blog-new/`; audit orphan pages | S | Index hygiene |
| 17 | Expand homepage copy: 3-step process, FAQ, testimonials | M | Rankings + CVR |
| 18 | Add `LocalBusiness`, `Service`, `FAQPage`, `sameAs` schema | S | Rich results |
| 19 | Split homeowner vs. contractor navigation paths | M | CVR |
| 20 | Restart the blog or de-emphasise it | L | E-E-A-T |

---

## 9. Methodology & caveats

- **Lighthouse:** mobile emulation (moto g power 2022, Chrome 152, Lighthouse 13.4.1) via Microlink, run 23 Aug 2026.
- **Security headers:** securityheaders.com, live scan.
- **HTML validity:** W3C Nu Html Checker v26.8.21, full source.
- **Content/structure:** rendered-DOM extraction of `/`, `/work-with-me/`, `/contact/`, `/podcasts/`, `/blog/`, `/blog-new/`, `/speaking-request/`, plus `robots.txt` and both sitemaps.
- **Caveats:**
  - Lighthouse's accessibility and SEO **category scores failed to compute** — the a11y gatherer crashed with *"Session closed"* mid-run, which on a page with 10.4 s of main-thread work is itself a finding. Accessibility findings here come from direct source analysis instead, so this list is a floor, not a ceiling. Re-run Lighthouse after the performance fixes to get scored a11y/SEO numbers.
  - **Colour contrast was not machine-verified** — needs a manual pass, especially hero text over photography.
  - Single-run lab data; no field/CrUX data was available. Real-user numbers may differ, though with 4.4 MB on mobile they're unlikely to be much better.
  - `/events/` could not be fetched directly (Cloudflare 403 to automated clients); its 301-to-homepage behaviour was confirmed via a headless-browser fetch.
