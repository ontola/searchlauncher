# Store copy

The text every store shows, in one place. `copy.py` writes it out to the fastlane files
that F-Droid publishes and that Play's console fields are filled from, so the two cannot
drift:

```bash
.venv/bin/python copy.py          # write the fastlane files, checking the limits
.venv/bin/python copy.py --check  # only check, change nothing
```

Edit the fenced blocks below and nothing else — everything outside them is a note to
whoever is editing. **Neither store renders Markdown**, so what is inside a block is
literally what a reader sees: no `**bold**`, no `- ` bullets that you want rendered as
dots. The capitalised lines are section headings because that is the most formatting
either store gives you.

Keep the claims true. Every feature named below is one the app actually has; if a feature
is removed, this file is what has to change first.

## Changelogs

Release notes are not in this file. They are one file per version code in
`fastlane/metadata/android/en-US/changelogs/`, and **one file serves both stores**:
F-Droid publishes it from the tag, and `fastlane supply` sends the same text to Play.

So write about the app, not about a listing page. "The screenshots on this page are new"
reads sensibly on F-Droid and means nothing on Play.

Play refuses release notes over **500 characters**. `copy.py` fails if the changelog for
the version in `app/build.gradle.kts` is over that, and CI runs it, so a tag cannot get
as far as a rejected upload.

## Title

Play allows 30 characters, F-Droid shows it as the app name.

```
SearchLauncher
```

## Short description

Play allows 80 characters and shows it under the title. F-Droid calls it the summary and
**its linter rejects a trailing full stop**, so do not add one.

```
Search apps, contacts and the web, and open results in an ad-free browser
```

## Full description

Play allows 4000 characters. The order follows the six propositions the screenshots make,
so someone scrolling the images and someone reading the text are told the same things in
the same sequence.

```
Your home screen opens with a keyboard. One bar finds everything on your phone and everything on the web, and opens what you find in a browser that blocks ads.

SEARCH ANYTHING
Apps, app shortcuts, contacts, device settings, downloads, calendar events for the coming week, text snippets and your own custom actions. Ranking learns from what you pick, so the next time is faster.

SEARCH INSIDE APPS, NOT JUST FOR THEM
Type y for YouTube, m for Maps, w for Wikipedia, then what you are looking for. Add a shortcut for any site with a search URL, and give it whatever letter you like.

ONE FIELD THAT KNOWS WHAT YOU TYPED
A sum gets an answer you can tap to copy. A web address opens. "5m pasta" sets a timer. A phone number offers to call it, message it, or save it as a contact. An email address offers to write to it.

A BROWSER WITH NO ADS
Web results open in the app, with ads and trackers blocked by default. Swipe the search bar sideways to move between tabs, or up to see them all as live previews. Both work from the home screen, so your tabs are one gesture away without opening a browser first. Save a page under a title you choose, and find visited pages back from the search bar. Private windows, file uploads, picture-in-picture video and hardware keyboard shortcuts are all included.

FEWER ICONS, MORE ROOM FOR WIDGETS
Add a widget by typing its name, resize it by dragging, and tap the wallpaper to show or hide them all. On a tablet they lay out in columns rather than one widget stretched across the display.

MAKE IT YOURS
Swipe through your own wallpapers. The theme colour can follow the wallpaper behind the search bar. Monochrome icons are optional. Dark mode, a true black OLED mode, a row of favourites, voice search, and export and import of everything you have set up.

Use it as your default launcher, or as a widget on the launcher you already have.

PRIVACY
Nothing is sent anywhere by default. Search suggestions are optional, and when you turn them on, what you type goes to whichever provider the shortcut names. Crash reporting is opt-in. Contacts, calendar entries and photos are read on your device to answer what you typed and are never uploaded. The browser's blocklist is downloaded from the StevenBlack hosts project.

SearchLauncher is free and open source: https://github.com/ontola/searchlauncher
```
