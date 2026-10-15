# Play Store listing

How Google Play search works, in short: there is no keyword field. The words
people type are matched against the title (weighs most), the short
description and the full description, and the ranking also uses installs,
ratings and how many people who see the listing install it. So the words
musicians actually search for go in the title and the first lines, said once
or twice in normal sentences. Repeating them, or words like "best" or "#1",
is against the metadata policy and can get the listing rejected.

Words this audience searches for, from the competing tools (SpinVid, Turn,
spinningvinyl.es, customdj.co.uk) and how artists describe the effect on
TikTok: spinning vinyl, vinyl video maker, spinning record video, album cover
video, cover art, music promotion, promote your music, release, single, EP,
Reels, TikTok, Stories. On Google Play itself there is no direct competitor:
"spinning vinyl" only finds widgets for the music you are listening to.

Category: **Video Players & Editors**. Tags in the Play Console: Video editor, Music, Social media tools.

## English (default)

**App name** (30 of 30): `Spin Doctor: Vinyl Video Maker`

**Short description** (78 of 80):
`Spinning vinyl video of your cover art and track for Reels, TikTok and Stories`

**Full description:**

Spin Doctor makes a spinning vinyl video of your release: your cover art as the label of a record that turns while your track plays. A short music video like that is an easy way to promote a new single or EP on Instagram Reels, TikTok, Stories and YouTube Shorts.

Pick your cover art and drag a circle over the part that becomes the label. Pick your track and choose the part people should hear: the whole track at the top, a close-up below where you slide the clip and trim its edges, from 5 seconds up to a minute. Then style the record and the video is saved to your gallery.

What you can change:
• Black vinyl, a colour of your choice, splatter or marble
• How fast the record turns and how big it is
• Spin up: the record starts from standstill and the music speeds up with it, from a low rumble to normal pitch, in a time you choose
• Background colour, or one of your own photos with blur
• Artist name and track title, in three type styles
• A badge: OUT NOW, NEW SINGLE, NEW EP, a release date or your own text
• Beat pulse: the record pumps on the kick of your track

Square for the Instagram feed, 9:16 for Reels, TikTok and Stories, 4:5 for portrait posts. Videos are 1080 pixels wide, 30 frames per second, with your audio.

For an EP or an album you add all tracks at once and choose a part of each one. Every track gets its own video and its own title in the same style, made one after the other, also when you switch to another app in the meantime.

Everything happens on your phone. There is no account and no watermark in your video, and nothing gets uploaded: the app has no internet access at all. It reads MP3, WAV, FLAC, M4A and OGG.

Spin Doctor is made by Lloyd Stellar, an electro producer from Hilversum, and started as a free tool on lloydstellar.nl.

## Nederlands

**App-naam** (29 van 30): `Spin Doctor: vinylvideo maken`

**Korte beschrijving** (73 van 80):
`Draaiende vinylvideo van je cover en track, voor Reels, TikTok en Stories`

**Volledige beschrijving:**

Spin Doctor maakt een draaiende vinylvideo van je release: je albumcover als label van een plaat die draait terwijl je track speelt. Zo'n korte muziekvideo is een makkelijke manier om een nieuwe single of EP te promoten op Instagram Reels, TikTok, Stories en YouTube Shorts.

Kies je cover en schuif een cirkel over het stuk dat het label wordt. Kies je track en het stuk dat mensen moeten horen: bovenaan de hele track, daaronder een close-up waarin je de clip verschuift en de randen bijknipt, van 5 seconden tot een minuut. Daarna maak je de plaat op en staat de video in je galerij.

Wat je kunt instellen:
• Zwart vinyl, een kleur naar keuze, splatter of marmer
• Hoe snel de plaat draait en hoe groot hij is
• Spin up: de plaat komt vanuit stilstand op gang en de muziek versnelt mee, van een laag gebrom naar de normale toonhoogte, in een tijd die je zelf kiest
• Een achtergrondkleur, of een eigen foto met vervaging
• Artiestnaam en tracktitel, in drie letterstijlen
• Een badge: OUT NOW, NEW SINGLE, NEW EP, een releasedatum of je eigen tekst
• Beat pulse: de plaat pompt mee op de kick van je track

Vierkant voor de Instagram-feed, 9:16 voor Reels, TikTok en Stories, 4:5 voor staande posts. Video's zijn 1080 pixels breed, 30 beelden per seconde, met je audio.

Voor een EP of album voeg je alle tracks in één keer toe en kies je van elke track een stuk. Elke track krijgt een eigen video met een eigen titel in dezelfde stijl. Ze worden na elkaar gemaakt, ook als je intussen een andere app gebruikt.

Alles gebeurt op je telefoon. Je hebt geen account nodig, er komt geen watermerk in je video en er wordt niets geüpload: de app heeft helemaal geen internettoegang. Hij leest MP3, WAV, FLAC, M4A en OGG.

Spin Doctor is gemaakt door Lloyd Stellar, electroproducer uit Hilversum, en begon als gratis tool op lloydstellar.nl.

## Graphics

- App icon: `tools/store/icon-512.png`
- Feature graphic: `tools/store/feature-1024x500.png`
- Phone screenshots, in this order: `tools/store/screen-01.png` to `screen-07.png` (1080x1920). The captions are in English; Play shows them for every language, which is fine for words like Reels and TikTok.

## Foreground service declaration (Play Console, App content)

**Types:** Media processing (Android 15 and up), Data sync (Android 12 to 14).

**Description:**
The app renders and encodes MP4 videos from the user's own artwork and audio. A release can hold many tracks and each becomes its own video, which takes a few minutes in total. The user starts the export with a button; a foreground service with a progress notification keeps the export running when the user switches to another app, and stops as soon as the last video is saved. Nothing is uploaded: the files are processed locally and written to the user's gallery. On Android 12 to 14 the Data sync type is used, which covers local file processing; on Android 15 and up the Media processing type.

**Impact if deferred or interrupted:** the user's export stops halfway and the videos that were not finished are lost.

**Video:** record the export screen of a release with three or more tracks, press MAKE VIDEOS, switch to the home screen, pull down the notification shade to show the progress, and return to the app when it is done.

## Data safety

- Data collected: none
- Data shared: none
- The app has no INTERNET permission.
